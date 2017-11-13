package com.r3.demos.ubin2a.pledge

import co.paralleluniverse.fibers.Suspendable
import com.r3.demos.ubin2a.base.CENTRAL_PARTY_X500
import com.r3.demos.ubin2a.pledge.Pledge.Companion.PLEDGE_CONTRACT_ID
import net.corda.confidential.IdentitySyncFlow
import net.corda.confidential.SwapIdentitiesFlow
import net.corda.core.contracts.Amount
import net.corda.core.flows.*
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.seconds
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashIssueFlow
import java.util.*

/**
 * This flow is invoked by the central bank where it issues digital currencies and transfer the funds to the receiver
 */
object ApprovePledge {
    @StartableByRPC
    @InitiatingFlow
    class Initiator(val amount: Amount<Currency>, val pledger: Party, val anonymous: Boolean = true) : FlowLogic<SignedTransaction>() {
        companion object {
            object PREPARING : ProgressTracker.Step("Preparing to self issue cash.")
            object ISSUING : ProgressTracker.Step("Issuing cash")
            object TRANSFER : ProgressTracker.Step("Transferring cash")
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
                    ISSUING,
                    TRANSFER,
                    BUILDING,
                    SIGNING,
                    COLLECTING,
                    FINALISING)
        }

        override val progressTracker: ProgressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {
            logger.info("ApprovePledge.Initiator: Building Pledge issuance transaction")
            progressTracker.currentStep = PREPARING
            val notary = serviceHub.networkMapCache.notaryIdentities.firstOrNull()
                    ?: throw IllegalStateException("No available notary.")
            if (ourIdentity.name != CENTRAL_PARTY_X500) throw IllegalStateException(ourIdentity.name.organisation + " is not allowed to issue cash")

            progressTracker.currentStep = ISSUING
            val issueRef = OpaqueBytes.of(0)
            subFlow(CashIssueFlow(amount, issueRef, notary))

            progressTracker.currentStep = BUILDING
            val txIdentities = if (anonymous) {
                subFlow(SwapIdentitiesFlow(pledger))
            } else {
                emptyMap<Party, AnonymousParty>()
            }
            val maybeAnonymousOtherParty = txIdentities[pledger] ?: pledger
            val maybeAnonymousMe = txIdentities[ourIdentity] ?: ourIdentity
            val pledgeState = Pledge.State(
                                                amount = amount,
                                                requester = maybeAnonymousOtherParty,
                                                approver = maybeAnonymousMe,
                                                issueDate = serviceHub.clock.instant())
            
            val utx = TransactionBuilder(notary = notary)
                    .addOutputState(pledgeState, PLEDGE_CONTRACT_ID)
                    .addCommand(Pledge.Issue(), pledgeState.participants.map { it.owningKey })
                    .setTimeWindow(serviceHub.clock.instant(), 30.seconds)

            progressTracker.currentStep = TRANSFER
            // Generate spend by bypassing queue checking
            val (spendTx, keysForSigning) = Cash.generateSpend(serviceHub, utx, amount, maybeAnonymousOtherParty)
            // Verify and sign the transaction
            spendTx.verify(serviceHub)

            progressTracker.currentStep = SIGNING
            // Sign the transaction.
            val ptx = serviceHub.signInitialTransaction(
                    spendTx,
                    keysForSigning + maybeAnonymousMe.owningKey)

            // Sync identities.
            val session = initiateFlow(pledger)
            subFlow(IdentitySyncFlow.Send(session, ptx.tx))

            // Collect signatures
            progressTracker.currentStep = COLLECTING
            val stx = subFlow(CollectSignaturesFlow(
                    ptx,
                    setOf(session),
                    pledgeState.participants.map { it.owningKey },
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
    @InitiatedBy(Initiator::class)
    class Responder(val otherFlow: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            logger.info("ApprovePledge.Responder: Syncing Identities")
            subFlow(IdentitySyncFlow.Receive(otherFlow))
            logger.info("ApprovePledge.Responder: Signing received transaction")
            val flow = object : SignTransactionFlow(otherFlow) {
                @Suspendable
                override fun checkTransaction(stx: SignedTransaction) = Unit // TODO: Add some checking here.
            }
            val stx = subFlow(flow)
            return waitForLedgerCommit(stx.id)
        }
    }
}