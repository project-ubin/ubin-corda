package com.r3.demos.ubin2a.cash

import co.paralleluniverse.fibers.Suspendable
import com.r3.demos.ubin2a.base.*
import com.r3.demos.ubin2a.obligation.GetQueue
import com.r3.demos.ubin2a.obligation.IssueObligation
import com.r3.demos.ubin2a.obligation.Obligation
import com.r3.demos.ubin2a.obligation.PostIssueObligationFlow
import net.corda.confidential.IdentitySyncFlow
import net.corda.confidential.SwapIdentitiesFlow
import net.corda.core.contracts.Amount
import net.corda.core.contracts.InsufficientBalanceException
import net.corda.core.flows.*
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.seconds
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.contracts.getCashBalances
import net.corda.finance.flows.CashIssueFlow
import java.util.*

// TODO: Remove this and just use the built in cash issue flow.
@StartableByRPC
@InitiatingFlow
class SelfIssueCashFlow(val amount: Amount<Currency>) : FlowLogic<Cash.State>() {

    companion object {
        object PREPARING : ProgressTracker.Step("Preparing to self issue cash.")
        object ISSUING : ProgressTracker.Step("Issuing cash")

        fun tracker() = ProgressTracker(PREPARING, ISSUING)
    }

    override val progressTracker: ProgressTracker = tracker()

    @Suspendable
    override fun call(): Cash.State {
        progressTracker.currentStep = PREPARING
        val issueRef = OpaqueBytes.of(0)
        val notary = serviceHub.networkMapCache.notaryIdentities.firstOrNull()
                ?: throw IllegalStateException("No available notary.")
        progressTracker.currentStep = ISSUING
        val cashIssueTransaction = subFlow(CashIssueFlow(amount, issueRef, notary))
        return cashIssueTransaction.stx.tx.outputs.single().data as Cash.State
    }
}

/**
 * Flow to send digital currencies to the other party
 */
@StartableByRPC
@InitiatingFlow
class Pay(val otherParty: Party,
          val amount: Amount<Currency>,
          val priority: Int,
          val anonymous: Boolean = true) : FlowLogic<SignedTransaction>() {

    companion object {
        object BUILDING : ProgressTracker.Step("Building payment to be sent to other party")
        object SIGNING : ProgressTracker.Step("Signing the payment")
        object COLLECTING : ProgressTracker.Step("Collecting signature from the counterparty") {
            override fun childProgressTracker() = CollectSignaturesFlow.tracker()
        }

        object FINALISING : ProgressTracker.Step("Finalising the transaction") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(BUILDING, SIGNING, COLLECTING, FINALISING)
    }

    override val progressTracker: ProgressTracker = tracker()
    @Suspendable
    override fun call(): SignedTransaction {
        logger.info("Pay: Building pay transaction")
        val priorityIsValid = OBLIGATION_PRIORITY.values().map { it.ordinal }.contains(priority)
        if (!priorityIsValid) throw IllegalArgumentException("Priority given is invalid")
        val notary = serviceHub.networkMapCache.notaryIdentities.firstOrNull()
                ?: throw IllegalStateException("No available notary.")
        // Exchange certs and keys.
        val txIdentities = if (anonymous) {
            subFlow(SwapIdentitiesFlow(otherParty))
        } else {
            emptyMap<Party, AnonymousParty>()
        }
        val maybeAnonymousOtherParty = txIdentities[otherParty] ?: otherParty
        progressTracker.currentStep = BUILDING
        val builder = TransactionBuilder(notary = notary)
        // Check we have enough cash to transfer the requested amount in a try block
        try {
            serviceHub.getCashBalances()[amount.token]?.let { if (it < amount) throw InsufficientBalanceException(amount - it) }
            // Check to see if any higher priorities obligation in queue
            // if higher priority obligations exist in queue than current transfer priority, put in queue
            val higherPriorityNotEmpty = subFlow(GetQueue.OutgoingUnconsumed()).any { it.priority >= priority }
            if (higherPriorityNotEmpty) throw IllegalArgumentException("Higher priority payments exist in queue.")
            // else continue to generate spend
            val (spendTx, keysForSigning) = Cash.generateSpend(serviceHub, builder, amount, maybeAnonymousOtherParty)

            // Verify and sign the transaction
            builder.verify(serviceHub)
            val currentTime = serviceHub.clock.instant()
            builder.setTimeWindow(currentTime, 30.seconds)

            // Sign the transaction.
            progressTracker.currentStep = SIGNING
            val partSignedTx = serviceHub.signInitialTransaction(spendTx, keysForSigning)
            val session = initiateFlow(otherParty)
            subFlow(IdentitySyncFlow.Send(session, partSignedTx.tx))
            
            // Collect signatures
            progressTracker.currentStep = COLLECTING
            // Finalise the transaction
            progressTracker.currentStep = FINALISING
            return subFlow(FinalityFlow(partSignedTx, FINALISING.childProgressTracker()))
            // Handle not enough cash by automatically issuing Obligation to lender
        } catch (ex: Exception) {
            return if (ex is InsufficientBalanceException || (ex is IllegalArgumentException && ex.message == "Higher priority payments exist in queue.")) {
                val lender = otherParty
                subFlow(IssueObligation.Initiator(amount, lender, priority))
            } else {
                logger.error("Exception CashFlows.Pay: ${ex.message.toString()}")
                throw ex
            }
        }
    }
}

/**
 * The other side of the above flow. For the purposes of this PoC, we won't add any additional checking.
 */
@InitiatingFlow
@InitiatedBy(Pay::class)
class AcceptPayment(val otherFlow: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        logger.info("Pay.AcceptPayment: Syncing identities")
        subFlow(IdentitySyncFlow.Receive(otherFlow))
    }
}

/**
 * Flow for API to call to post transfer transactions
 */
@StartableByRPC
@InitiatingFlow
class PostTransfersFlow(val value: TransactionModel) : FlowLogic<TransactionModel>() {
    @Suspendable
    override fun call(): TransactionModel {
        logger.info("PostTransfersFlow: Running logic to determine to settle pay immediately or putting in queue")
        if (value.enqueue == 1) {
            return subFlow(PostIssueObligationFlow(value))
        }
        val maybeOtherParty = serviceHub.identityService.partiesFromName(value.receiver!!, exactMatch = true)
        if (maybeOtherParty.size != 1) throw IllegalArgumentException("Unknown Party")
        if(maybeOtherParty.first() == ourIdentity) throw IllegalArgumentException("Failed requirement: The payer and payee cannot be the same identity")
        val otherParty = maybeOtherParty.single()
        val transferAmount = SGD(value.transactionAmount!!)
        val stx = subFlow(Pay(otherParty, transferAmount, value.priority!!))

        // If Pay flow successfully moved cash state, the output state is of type Cash.State
        // Model the response based on successful transfer fo funds
        // Else model the response based on Obligation.State that was issued
        val txId = stx.tx.id
        when {
            stx.tx.commands.none { it.value == (Obligation.Issue()) } -> {
                logger.info("PostTransfersFlow: Transfer successful. Returning transaction details")
                val state = stx.tx.outputsOfType<Cash.State>().first()
                val sender = serviceHub.identityService.partyFromKey(stx.tx.commands.first().signers.first())!!
                val receiver = serviceHub.identityService.requireWellKnownPartyFromAnonymous(state.owner)
                return TransactionModel(
                        transId = txId.toString(),
                        sender = sender.name.organisation,
                        receiver = receiver.name.organisation,
                        transactionAmount = state.amount.quantity.to2Decimals(),
                        currency = state.amount.token.product.toString(),
                        status = OBLIGATION_STATUS.SETTLED.name.toLowerCase(),
                        priority = value.priority)
            }
            stx.tx.commands.any { it.value == (Obligation.Issue()) } -> {
                logger.info("PostTransfersFlow: Queue successful. Returning transaction details")
                val state = stx.tx.outputsOfType<Obligation.State>().first()
                val sender = serviceHub.identityService.requireWellKnownPartyFromAnonymous(state.borrower)
                val receiver = serviceHub.identityService.requireWellKnownPartyFromAnonymous(state.lender)
                return TransactionModel(
                        transId = txId.toString(),
                        linearId = state.linearId.toString(),
                        sender = sender.name.organisation,
                        receiver = receiver.name.organisation,
                        transactionAmount = state.amount.quantity.to2Decimals(),
                        currency = state.amount.token.currencyCode.toString(),
                        status = OBLIGATION_STATUS.ACTIVE.name.toLowerCase(),
                        priority = value.priority)
            }
            else -> throw IllegalStateException("Unexpected State exception: " + stx.tx.outputs.first())
        }
    }
}