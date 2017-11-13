package com.r3.demos.ubin2a.redeem

import co.paralleluniverse.fibers.Suspendable
import com.r3.demos.ubin2a.base.*
import com.r3.demos.ubin2a.redeem.Redeem.Companion.REDEEM_CONTRACT_ID
import net.corda.confidential.IdentitySyncFlow
import net.corda.confidential.SwapIdentitiesFlow
import net.corda.core.contracts.Amount
import net.corda.core.contracts.InsufficientBalanceException
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.*
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.seconds
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.contracts.asset.CashSelection
import net.corda.finance.flows.CashException
import net.corda.finance.issuedBy
import java.util.*

/**
 * A bank will transfer cash to central bank and issue a RedeemRequest to Central Bank
 * for debiting and crediting in external system eg. MEPS+
 */
object IssueRedeem {
    @StartableByRPC
    @InitiatingFlow
    class Initiator(val amount: Amount<Currency>, val anonymous: Boolean = true) : FlowLogic<SignedTransaction>() {
        companion object {
            object PREPARING : ProgressTracker.Step("Preparing to redeem cash.")
            object TRANSFER : ProgressTracker.Step("Transferring cash for redemption")
            object BUILDING : ProgressTracker.Step("Building payment to be sent to other party")
            object SIGNING : ProgressTracker.Step("Signing the payment")
            object COLLECTING : ProgressTracker.Step("Collecting signature from the counterparty") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }
            object FINALISING : ProgressTracker.Step("Finalising the transaction") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    PREPARING,
                    TRANSFER,
                    BUILDING,
                    SIGNING,
                    COLLECTING,
                    FINALISING)
        }

        override val progressTracker: ProgressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {
            logger.info("IssueRedeem.Initiator: Building Redeem issuance transaction")
            progressTracker.currentStep = PREPARING
            val notary = serviceHub.networkMapCache.notaryIdentities.firstOrNull()
                    ?: throw IllegalStateException("No available notary.")
            val centralBank = serviceHub.networkMapCache.getNodeByLegalName(CENTRAL_PARTY_X500)?.legalIdentities?.firstOrNull() ?:
                    throw Exception("Cannot find central bank node.")

            progressTracker.currentStep = BUILDING

            val txIdentities = if (anonymous) {
                subFlow(SwapIdentitiesFlow(centralBank))
            } else { emptyMap<Party, AnonymousParty>() }

            val maybeAnonymousOtherParty = txIdentities[centralBank] ?: centralBank
            val maybeAnonymousMe = txIdentities[ourIdentity] ?: ourIdentity
            val redeemState = Redeem.State(
                                                amount = amount,
                                                requester = maybeAnonymousMe,
                                                approver = maybeAnonymousOtherParty,
                                                issueDate = serviceHub.clock.instant())

            val utx = TransactionBuilder(notary = notary)
                    .addOutputState(redeemState, REDEEM_CONTRACT_ID)
                    .addCommand(Redeem.Issue(), redeemState.participants.map { it.owningKey })
                    .setTimeWindow(serviceHub.clock.instant(), 30.seconds)

            progressTracker.currentStep = TRANSFER
            // Generate spend by bypassing queue checking to central bank well known identity
            val (spendTx, keysForSigning) = Cash.generateSpend(serviceHub, utx, amount, centralBank)

            // Verify and sign the transaction
            spendTx.verify(serviceHub)

            progressTracker.currentStep = SIGNING
            // Sign the transaction.
            val ptx = serviceHub.signInitialTransaction(
                    spendTx,
                    keysForSigning + maybeAnonymousMe.owningKey )

            // Sync identities.
            val session = initiateFlow(centralBank)
            subFlow(IdentitySyncFlow.Send(session, ptx.tx))

            // Collect signatures
            progressTracker.currentStep = COLLECTING
            val stx = subFlow(CollectSignaturesFlow(
                    ptx,
                    setOf(session),
                    redeemState.participants.map { it.owningKey },
                    COLLECTING.childProgressTracker())
            )

            // Finalise the transaction
            progressTracker.currentStep = FINALISING
            return subFlow(FinalityFlow(stx, FINALISING.childProgressTracker()))
        }
    }

    /**
     * The other side of the above flow. For the purposes of this PoC, we won't add any additional checking.
     */
    @InitiatedBy(IssueRedeem.Initiator::class)
    class Responder(val otherFlow: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            logger.info("IssueRedeem.Responder: Syncing identities")
            subFlow(IdentitySyncFlow.Receive(otherFlow))
            logger.info("IssueRedeem.Responder: Signing received transaction")
            val flow = object : SignTransactionFlow(otherFlow) {
                @Suspendable
                override fun checkTransaction(stx: SignedTransaction) = Unit // TODO: Add some checking here.
            }
            val stx = subFlow(flow)
            return waitForLedgerCommit(stx.id)
        }
    }
}

/**
 * Central bank will approve the redeem request which will
 * 1. Invoke a flow to external api to debit/credit real cash in the bank's account
 * 2. Destroy the redeem request state and remove the cash from the DLT
 * if devMode = true, skip the external api call
 */
object ApproveRedeem {
    @StartableByRPC
    @InitiatingFlow
    class Initiator(val transactionId : String, val devMode: Boolean = false) : FlowLogic<SignedTransaction>() {
        companion object {
            object PREPARING : ProgressTracker.Step("Preparing to destroy cash.")
            object BUILDING : ProgressTracker.Step("Building cash exiting")
            object SIGNING : ProgressTracker.Step("Signing the payment")
            object COLLECTING : ProgressTracker.Step("Collecting signature from the counterparty") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }
            object FINALISING : ProgressTracker.Step("Finalising the transaction") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    PREPARING,
                    BUILDING,
                    SIGNING,
                    COLLECTING,
                    FINALISING)
        }

        override val progressTracker: ProgressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {
            logger.info("ApproveRedeem.Initiator: Building Redeem approval transaction")

            progressTracker.currentStep = PREPARING
            if (ourIdentity.name != CENTRAL_PARTY_X500) throw IllegalStateException( ourIdentity.name.organisation + " is not allowed to destroy cash")

            progressTracker.currentStep = BUILDING
            // Get linearId from transId
            val redeemStateAndRef = subFlow(GetRedeemsFlow.FromTransId(transactionId)).first()
            val notary = redeemStateAndRef.state.notary
            val redeemState = redeemStateAndRef.state.data
            val (amount, requester, approver) = redeemState
            val issuer = ourIdentity.ref(OpaqueBytes.of(0))

            val utx = TransactionBuilder(notary = notary)

            // Exit Cash
            // TODO: there's still an issue where Central Bank can also receive normal transfer cash where MAS identity is anonymised
            // TODO: the cash will be mixed up during cashSelection
            val exitStates = CashSelection
                    .getInstance { serviceHub.jdbcSession().metaData }
                    .unconsumedCashStatesForSpending(serviceHub, amount, setOf(issuer.party), utx.notary, utx.lockId, setOf(issuer.reference))
            var exitAmount = 0.00
            exitStates.forEach{ exitAmount += it.state.data.amount.quantity.to2Decimals() }
            val signers = try {
                Cash().generateExit(
                        utx,
                        amount.issuedBy(issuer),
                        exitStates)
            } catch (e: InsufficientBalanceException) {
                throw CashException("Exiting more cash than exists. Exit States $exitAmount ", e)
            }

            utx.addCommand(Redeem.Settle(), redeemState.participants.map { it.owningKey })
                    .addInputState(redeemStateAndRef)
                    .setTimeWindow(serviceHub.clock.instant(), 30.seconds)

            progressTracker.currentStep = SIGNING
            utx.verify(serviceHub)
            val ptx = serviceHub.signInitialTransaction(utx, signers + approver.owningKey)
            // Get counterparty signature.
            progressTracker.currentStep = COLLECTING
            val requesterIdentity = serviceHub.identityService.requireWellKnownPartyFromAnonymous(requester)


            // Sync identities.
            val session = initiateFlow(requesterIdentity)
            subFlow(IdentitySyncFlow.Send(session, ptx.tx))

            // Collect Signatures
            val stx = subFlow(CollectSignaturesFlow(
                    ptx,
                    setOf(session),
                    redeemState.participants.map { it.owningKey },
                    COLLECTING.childProgressTracker()))
            // Finalise the transaction.
            progressTracker.currentStep = FINALISING
            val ftx = subFlow(FinalityFlow(stx, FINALISING.childProgressTracker()))

            // Send payload to external MEPS
            val RedeemService = serviceHub.cordaService(ExternalRedeemService.Service::class.java)
            val value = TransactionModel(
                    receiver = serviceHub.identityService.requireWellKnownPartyFromAnonymous(requester).name.organisation,
                    transactionAmount = amount.quantity.to2Decimals(),
                    transId = transactionId)
            if(devMode.not()) {
                val approved = RedeemService.approveRedeemInMEPS(value)
                if (approved.not()) {
                    throw FlowException("Unexpected error in RedeemService to External MEPS. Missing config.properties")
                }
            }
            return ftx
        }
    }

    /**
     * The other side of the above flow. For the purposes of this PoC, we won't add any additional checking.
     */
    @InitiatedBy(Initiator::class)
    class Responder(val otherFlow: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            logger.info("ApproveRedeem.Responder: Syncing identities")
            subFlow(IdentitySyncFlow.Receive(otherFlow))
            logger.info("ApproveRedeem.Responder: Signing received transaction")
            val flow = object : SignTransactionFlow(otherFlow) {
                @Suspendable
                override fun checkTransaction(stx: SignedTransaction) = Unit // TODO: Add some checking here.
            }
            val stx = subFlow(flow)
            return waitForLedgerCommit(stx.id)
        }
    }

}

/**
 * Return list of redeems state and transId associated with the creation of the redeems state
 */
object GetRedeemsFlow {

    /**
     * Flow to query redeem states by transId
     */
    @StartableByRPC
    @InitiatingFlow
    class FromTransId(val transactionId: String) : FlowLogic<List<StateAndRef<Redeem.State>>>() {
        @Suspendable
        override fun call(): List<StateAndRef<Redeem.State>> {
            logger.info("GetRedeemsFlow.FromTransId: Querying redeem states based on transaction id")
            val transaction = serviceHub.validatedTransactions.getTransaction(SecureHash.parse(transactionId)) ?: throw IllegalArgumentException("Transaction $transactionId not found")

            if (transaction.tx.outputsOfType<Redeem.State>().isEmpty()) {
                throw IllegalArgumentException("Transaction $transactionId state is of invalid type")
            }

            val state = transaction.tx.outputsOfType<Redeem.State>().single()
            val generalCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
            val transQueryCriteria = QueryCriteria.LinearStateQueryCriteria(uuid = listOf(UUID.fromString(state.linearId.toString())))
            val redeemStateAndRef = serviceHub.vaultService.queryBy<Redeem.State>(generalCriteria.and(transQueryCriteria)).states

            if (redeemStateAndRef.isEmpty()) {
                throw IllegalArgumentException("State within $transactionId has been consumed")
            }
            return redeemStateAndRef
        }
    }

    @InitiatingFlow
    @StartableByRPC
    class Unconsumed : FlowLogic<List<TransactionModel>>() {
        @Suspendable
        override fun call(): List<TransactionModel> {
            logger.info("GetRedeemsFlow.Unconsumed: Querying redeem states that have not been consumed")
            try {
                // Get unconsumed redeem state
                val generalCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
                val redeemStates = serviceHub.vaultService.queryBy<Redeem.State>(generalCriteria)
                val redeemLinearIds = mutableListOf<UniqueIdentifier>()
                redeemStates.states.forEach { redeemLinearIds.add(it.state.data.linearId) }

                // Get the tx that involved these redeem states
                val filteredStx = serviceHub.validatedTransactions.track().snapshot.filter {
                    it.tx.outputsOfType<Redeem.State>().isNotEmpty() && redeemLinearIds.contains(it.tx.outputsOfType<Redeem.State>().first().linearId)
                }

                // Create a map for linearId to tx
                val linearToTxMap = mutableMapOf<UniqueIdentifier, SignedTransaction>()
                filteredStx.forEach { linearToTxMap.put(it.tx.outputsOfType<Redeem.State>().single().linearId, it) }

                if (redeemLinearIds.size != linearToTxMap.size) throw IllegalArgumentException("Uneven size of tx ids list to redeem states list")
                val results = mutableListOf<TransactionModel>()

                // Return the list of unconsumed redeem states with its corresponding tx id
                redeemStates.states.forEach {
                    val state = it.state.data
                    val sender = serviceHub.identityService.requireWellKnownPartyFromAnonymous(state.requester)
                    val receiver = serviceHub.identityService.requireWellKnownPartyFromAnonymous(state.approver)
                    val stx = linearToTxMap[state.linearId] ?: throw IllegalArgumentException("Unexpected missing tx")
                    results.add(
                            TransactionModel(
                                    transType = TRANSACTION_TYPE.REDEEM.name.toLowerCase(),
                                    transId = stx.id.toString(),
                                    linearId = state.linearId.toString(),
                                    sender = sender.name.organisation,
                                    receiver = receiver.name.organisation,
                                    transactionAmount = state.amount.quantity.to2Decimals(),
                                    requestedDate = Date(state.issueDate.toEpochMilli()).toSimpleString(),
                                    updatedDate = Date(stx.tx.timeWindow!!.midpoint!!.toEpochMilli()).toSimpleString(),
                                    currency = state.amount.token.currencyCode
                            )
                    )
                }
                return results
            } catch (ex: Exception) {
                logger.error(ex.message)
                throw ex
            }
        }
    }
}

/**
 * Flow to issue a new redeem and return a post response
 */
@StartableByRPC
@InitiatingFlow
class PostIssueRedeem(val transactionAmount: Double) : FlowLogic<TransactionModel>() {
    @Suspendable
    override fun call(): TransactionModel {
        logger.info("PostIssueRedeem: Logic to start issue redeem initiator flow")
        val stx = subFlow(IssueRedeem.Initiator(SGD(transactionAmount), true))
        val txId = stx.id
        val state = stx.tx.outputsOfType<Redeem.State>().first()
        val sender = serviceHub.identityService.requireWellKnownPartyFromAnonymous(state.requester)
        val receiver = serviceHub.identityService.requireWellKnownPartyFromAnonymous(state.approver)
        return TransactionModel(
                transId = txId.toString(),
                linearId = state.linearId.toString(),
                sender = sender.name.organisation,
                receiver = receiver.name.organisation,
                transactionAmount = state.amount.quantity.to2Decimals(),
                currency = state.amount.token.currencyCode.toString()
                )
    }
}

/**
 * Flow to issue a new redeem and return a post response
 */
@StartableByRPC
@InitiatingFlow
class PostApproveRedeem(val transId: String, val devMode: Boolean = false) : FlowLogic<TransactionModel>() {
    @Suspendable
    override fun call(): TransactionModel {
        logger.info("PostIssueRedeem: Logic to start issue redeem approval flow")
        val stx = subFlow(ApproveRedeem.Initiator(transId, devMode))
        val txId = stx.id
        val state = stx.toLedgerTransaction(serviceHub).inputsOfType<Redeem.State>().first()
        val sender = serviceHub.identityService.requireWellKnownPartyFromAnonymous(state.requester)
        val receiver = serviceHub.identityService.requireWellKnownPartyFromAnonymous(state.approver)
        return TransactionModel(
                transId = txId.toString(),
                linearId = state.linearId.toString(),
                sender = sender.name.organisation,
                receiver = receiver.name.organisation,
                transactionAmount = state.amount.quantity.to2Decimals(),
                currency = state.amount.token.currencyCode.toString()
        )
    }
}

@StartableByRPC
@InitiatingFlow
class PrintRedeemURI : FlowLogic<String>() {
    @Suspendable
    override fun call(): String {
        val RedeemToExternal = serviceHub.cordaService(ExternalRedeemService.Service::class.java)
        return RedeemToExternal.getApproveRedeemURI("ApproveRedeemURI")
    }
}

