package com.r3.demos.ubin2a.obligation

import co.paralleluniverse.fibers.Suspendable
import com.r3.demos.ubin2a.base.*
import com.r3.demos.ubin2a.obligation.Obligation.Companion.OBLIGATION_CONTRACT_ID
import net.corda.confidential.IdentitySyncFlow
import net.corda.confidential.SwapIdentitiesFlow
import net.corda.core.contracts.Amount
import net.corda.core.contracts.TransactionState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.seconds
import net.corda.core.utilities.toBase58String
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.contracts.getCashBalances
import java.sql.SQLException
import java.util.*

/**
 * Simple flow for agreeing a bilateral obligation. This flow would be started by the borrower who asks for
 * the lenders assent. This flow assumes that off-ledger the lender has already sent the borrower some cash or
 * some deal has already been entered in to.
 */
object IssueObligation {
    @InitiatingFlow
    @StartableByRPC
    class Initiator(val amount: Amount<Currency>,
                    val lender: Party,
                    val priority: Int = 0,
                    val anonymous: Boolean = true) : FlowLogic<SignedTransaction>() {

        companion object {
            object BUILDING : ProgressTracker.Step("Building and verifying transaction.")
            object SIGNING : ProgressTracker.Step("signing transaction.")
            object COLLECTING : ProgressTracker.Step("Collecting counterparty signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING : ProgressTracker.Step("Finalising transaction") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(BUILDING, SIGNING, COLLECTING, FINALISING)
        }

        override val progressTracker: ProgressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {
            logger.info("IssueObligation: Building obligation issuance transaction")
            // Step 1. Setup identities.
            progressTracker.currentStep = BUILDING
            val notary = serviceHub.networkMapCache.notaryIdentities.firstOrNull()
                    ?: throw IllegalStateException("No available notary.")
            val txIdentities = subFlow(SwapIdentitiesFlow(lender))
            val maybeAnonymousMe = txIdentities[ourIdentity]
                    ?: throw IllegalStateException("Couldn't generate random key.")
            val maybeAnonymousLender = txIdentities[lender]
                    ?: throw IllegalStateException("Couldn't generate random key.")

            // Step 2. Build transaction.
            val state = if (anonymous) {
                Obligation.State(amount, maybeAnonymousLender, maybeAnonymousMe)
            } else {
                Obligation.State(amount, lender, ourIdentity)
            }

            val utx = TransactionBuilder(notary = notary)
                    .addOutputState(state, OBLIGATION_CONTRACT_ID)
                    .addCommand(Obligation.Issue(), state.participants.map { it.owningKey })
                    .setTimeWindow(serviceHub.clock.instant(), 30.seconds)

            // Step 3. Sign the transaction.
            progressTracker.currentStep = SIGNING
            val ptx = if (anonymous) {
                serviceHub.signInitialTransaction(utx, maybeAnonymousMe.owningKey)
            } else {
                serviceHub.signInitialTransaction(utx)
            }

            // Step 4. Get the counter-party signature.
            progressTracker.currentStep = COLLECTING
            val session = initiateFlow(lender)
            val stx = subFlow(CollectSignaturesFlow(
                    ptx,
                    setOf(session),
                    state.participants.map { it.owningKey },
                    COLLECTING.childProgressTracker())
            )

            // Step 5. Finalise the transaction.
            progressTracker.currentStep = FINALISING
            val ftx = subFlow(FinalityFlow(stx, FINALISING.childProgressTracker()))

            // Step 6. Add new obligation with priority to queue.
            val customVaultQueryService = serviceHub.cordaService(PersistentObligationQueue::class.java)
            val result = customVaultQueryService.addObligationToQueue(state, priority, OBLIGATION_STATUS.ACTIVE.ordinal)
            if (!result) throw FlowException("Unable to add obligation to the persistent queue.")

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
            logger.info("IssueObligation.Responder: Signing received transaction")
            val flow = object : SignTransactionFlow(otherFlow) {
                @Suspendable
                override fun checkTransaction(stx: SignedTransaction) = Unit // TODO: Do some checking here.
            }
            val stx = subFlow(flow)
            return waitForLedgerCommit(stx.id)
        }
    }
}

/**
 * This flow assumes that the linearId provided was derived from the obligation queue.
 */
object SettleObligation {
    @InitiatingFlow
    @StartableByRPC
    /**
     * A flow for initiating the settlement of a bi-lateral obligation. Currently it only fully settles obligations.
     * It will fail if not enough cash is present in the vault or is not initiated by the borrower.
     *
     * @param linerId the linearId of the obligation to settle.
     */
    class Initiator(val linearId: UniqueIdentifier) : FlowLogic<SignedTransaction>() {

        companion object {
            object QUERYING : ProgressTracker.Step("Querying the vault for obligation.")
            object BUILDING : ProgressTracker.Step("Building and verifying transaction.")
            object SIGNING : ProgressTracker.Step("signing transaction.")
            object COLLECTING : ProgressTracker.Step("Collecting counterparty signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING : ProgressTracker.Step("Finalising transaction") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(QUERYING, BUILDING, SIGNING, COLLECTING, FINALISING)
        }

        override val progressTracker: ProgressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {
            logger.info("SettleObligation.Initiator: Building obligation settlement transaction")
            // Step 1. Retrieve the obligation state from the vault.
            progressTracker.currentStep = QUERYING
            val queryCriteria = QueryCriteria.LinearStateQueryCriteria(uuid = listOf(linearId.id))
            val obligationToSettle = serviceHub.vaultService.queryBy<Obligation.State>(queryCriteria).states.single()
            val (amount, lender, borrower) = obligationToSettle.state.data

            // Step 2. Check the party running this flow is the borrower.
            val borrowerIdentity = serviceHub.identityService.requireWellKnownPartyFromAnonymous(borrower)
            if (ourIdentity != borrowerIdentity)
                throw IllegalArgumentException("Obligation settlement flow must be initiated by the borrower.")

            // Step 3. Create a transaction builder.
            progressTracker.currentStep = BUILDING
            val notary = obligationToSettle.state.notary
            val utx = TransactionBuilder(notary = notary)

            // Step 4. Check we have enough cash to settle the requested amount.
            serviceHub.getCashBalances()[amount.token]?.let {
                if (it < amount) throw IllegalArgumentException("Borrower has only $it but needs $amount to settle.")
            } ?: throw IllegalArgumentException("Borrower has no ${amount.token} to settle.")

            // Step 5. Exchange certs and keys.
            val lenderIdentity = serviceHub.identityService.requireWellKnownPartyFromAnonymous(lender)
            val txIdentities = subFlow(SwapIdentitiesFlow(lenderIdentity))
            val anonymousLender = txIdentities[lenderIdentity]
                    ?: throw IllegalStateException("Couldn't get anonymous identity for lender")

            // Step 6. Get some cash from the vault and add a cash spend to our transaction builder.
            val (_, keys) = Cash.generateSpend(serviceHub, utx, amount, anonymousLender)

            // Step 7. Add the obligation input state and settle command to the transaction builder.
            // Don't add an output obligation as we are fully settling them.
            utx.addCommand(Obligation.Settle(), obligationToSettle.state.data.participants.map { it.owningKey })
            utx.addInputState(obligationToSettle)
            utx.setTimeWindow(serviceHub.clock.instant(), 30.seconds)

            // Step 8. Verify and sign the transaction.
            progressTracker.currentStep = SIGNING
            utx.verify(serviceHub)
            val ptx = serviceHub.signInitialTransaction(utx, keys + borrower.owningKey)

            // Step 9. Sync identities.
            val session = initiateFlow(lenderIdentity)
            subFlow(IdentitySyncFlow.Send(session, ptx.tx))

            // Step 10. Get counterparty signature.
            progressTracker.currentStep = COLLECTING
            println(ptx.tx.requiredSigningKeys.map { it.toBase58String() })
            println(ptx.tx.outputStates.map { state -> state.participants.map { } })
            val stx = subFlow(CollectSignaturesFlow(
                    ptx,
                    setOf(session),
                    keys + borrower.owningKey,
                    COLLECTING.childProgressTracker())
            )

            // Step 11. Finalise the transaction.
            progressTracker.currentStep = FINALISING
            val ftx = subFlow(FinalityFlow(stx, FINALISING.childProgressTracker()))

            // Step 12. Update the obligation queue.
            val customVaultQueryService = serviceHub.cordaService(PersistentObligationQueue::class.java)
            val result = customVaultQueryService.updateObligationStatus(linearId, OBLIGATION_STATUS.SETTLED.ordinal)
            if (!result) throw FlowException("Couldn't update obligation in database queue")

            return ftx
        }
    }

    /**
     * The other side of the above flow. For the purposes of this PoC, we won't add any additional checking.
     */
    @InitiatingFlow
    @InitiatedBy(Initiator::class)
    class Responder(val otherFlow: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            logger.info("SettleObligation.Responder: Syncing identities")
            subFlow(IdentitySyncFlow.Receive(otherFlow))
            logger.info("SettleObligation.Responder: Signing received transaction")
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
 * This flow assumes that the linearId provided was derived from the obligation queue.
 */
object CancelObligation {
    @StartableByRPC
    @InitiatingFlow
    class Initiator(val linearId: UniqueIdentifier) : FlowLogic<SignedTransaction>() {

        companion object {
            object QUERYING : ProgressTracker.Step("Querying the vault for obligation.")
            object BUILDING : ProgressTracker.Step("Building and verifying transaction.")
            object SIGNING : ProgressTracker.Step("Signing transaction.")
            object COLLECTING : ProgressTracker.Step("Collecting counterparty signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING : ProgressTracker.Step("Finalising transaction") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(QUERYING, BUILDING, SIGNING, COLLECTING, FINALISING)
        }

        override val progressTracker: ProgressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {
            logger.info("CancelObligation.Initiator: Building obligation cancellation transaction")
            // Step 1. Retrieve the obligation state from the vault.
            progressTracker.currentStep = QUERYING
            val queryCriteria = QueryCriteria.LinearStateQueryCriteria(uuid = listOf(linearId.id))
            val obligationToCancel = serviceHub.vaultService.queryBy<Obligation.State>(queryCriteria).states.single()
            val (_, lender, borrower) = obligationToCancel.state.data

            // Step 2. Check the party running this flow is the borrower.
            val borrowerIdentity = serviceHub.identityService.requireWellKnownPartyFromAnonymous(borrower)
            if (ourIdentity != borrowerIdentity) {
                throw IllegalArgumentException("Obligation Cancellation flow must be initiated by the borrower.")
            }

            // Step 3. Create a transaction builder.
            progressTracker.currentStep = BUILDING
            val notary = obligationToCancel.state.notary
            val utx = TransactionBuilder(notary = notary)
                    .addCommand(Obligation.Exit(), obligationToCancel.state.data.participants.map { it.owningKey })
                    .addInputState(obligationToCancel)
                    .setTimeWindow(serviceHub.clock.instant(), 30.seconds)

            // Step 4. Verify and sign the transaction.
            progressTracker.currentStep = SIGNING
            utx.verify(serviceHub)
            val ptx = serviceHub.signInitialTransaction(utx, borrower.owningKey)

            // Step 5. Get counterparty signature.
            progressTracker.currentStep = COLLECTING
            val lenderIdentity = serviceHub.identityService.requireWellKnownPartyFromAnonymous(lender)
            val session = initiateFlow(lenderIdentity)
            val stx = subFlow(CollectSignaturesFlow(
                    ptx,
                    setOf(session),
                    obligationToCancel.state.data.participants.map { it.owningKey },
                    COLLECTING.childProgressTracker())
            )

            // Step 6. Finalise the transaction.
            progressTracker.currentStep = FINALISING
            val ftx = subFlow(FinalityFlow(stx, FINALISING.childProgressTracker()))

            // Step 7. Update the obligation queue.
            val customVaultQueryService = serviceHub.cordaService(PersistentObligationQueue::class.java)
            val result = customVaultQueryService.updateObligationStatus(linearId, OBLIGATION_STATUS.CANCELLED.ordinal)
            if (!result) throw IllegalArgumentException("Couldn't update obligation in database queue")

            return ftx
        }
    }


    @InitiatingFlow
    @InitiatedBy(CancelObligation.Initiator::class)
    class Responder(val otherFlow: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            logger.info("CancelObligation.Responder: Signing received transaction")
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
 * Mutate priority of obligation item in queue
 */
@InitiatingFlow
@StartableByRPC
class UpdateObligationPriority(val linearId: UniqueIdentifier, val priority: OBLIGATION_PRIORITY) : FlowLogic<Boolean>() {
    @Suspendable
    override fun call(): Boolean {
        logger.info("ObligationFlows.UpdateObligationPriority: $linearId to $priority")
        val priorityIsValid = OBLIGATION_PRIORITY.values().contains(priority)
        if (!priorityIsValid) throw IllegalArgumentException("Priority given is invalid")
        val customVaultQueryService = serviceHub.cordaService(PersistentObligationQueue::class.java)
        var maybeUpdate = false
        return try {
            maybeUpdate = customVaultQueryService.updateObligationPriority(linearId, priority.ordinal)
            maybeUpdate
        } catch (ex: Exception) {
            return if (ex is SQLException && ex.message.toString().contains("Table \"OBLIGATIONS\" not found;"))
                maybeUpdate
            else {
                logger.error("UpdateObligationPriority: " + ex.message.toString())
                throw ex
            }
        }
    }
}

/**
 * Mutate status of obligation item in queue
 */
@StartableByRPC
@InitiatingFlow
class UpdateObligationStatus(val linearId: UniqueIdentifier, val status: OBLIGATION_STATUS) : FlowLogic<Boolean>() {
    @Suspendable
    override fun call(): Boolean {
        logger.info("ObligationFlows.UpdateObligationStatus: $linearId to $status")
        val priorityIsValid = OBLIGATION_STATUS.values().contains(status)
        if (!priorityIsValid) throw IllegalArgumentException("Priority given is invalid")
        var result = false
        try {
            val customVaultQueryService = serviceHub.cordaService(PersistentObligationQueue::class.java)
            result = customVaultQueryService.updateObligationStatus(linearId, status.ordinal)
        } catch (ex: Exception) {
            if (ex is SQLException && ex.message.toString().contains("Table \"OBLIGATIONS\" not found;"))
            else {
                logger.error("UpdateObligationPriority: " + ex.message.toString())
                throw ex
            }
        }
        if (!result) {
            throw FlowException("Couldn't update obligation in database queue")
        }
        return result
    }
}

/**
 * Flow for querying queue
 */
object GetQueue {
    @InitiatingFlow
    @StartableByRPC
    class Outgoing : FlowLogic<List<ObligationModel>>() {
        @Suspendable
        override fun call(): List<ObligationModel> {
            logger.info("GetQueue.Outgoing: Getting obligations")
            val customVaultQueryService = serviceHub.cordaService(PersistentObligationQueue::class.java)
            var outoingObligations = listOf<ObligationModel>()
            return try {
                outoingObligations = customVaultQueryService.retrieveOutgoingObligations()
                outoingObligations
            } catch (ex: Exception) {
                if (ex is SQLException && ex.message.toString().contains("Table \"OBLIGATIONS\" not found;"))
                    outoingObligations
                else {
                    logger.error("GetQueue.Outgoing(): " + ex.message.toString())
                    throw ex
                }
            }
        }
    }

    /**
     * Flow for getting outgoingObligationsFlow based on linearId
     */
    @InitiatingFlow
    @StartableByRPC
    class OutgoingById(val linearId: UniqueIdentifier) : FlowLogic<List<ObligationModel>>() {
        @Suspendable
        override fun call(): List<ObligationModel> {
            logger.info("GetQueue.Outgoing: Getting obligations")
            val customVaultQueryService = serviceHub.cordaService(PersistentObligationQueue::class.java)
            var outgoingObligations = listOf<ObligationModel>()
            return try {
                outgoingObligations = customVaultQueryService.getOutgoingObligationFromLinearId(linearId)
                outgoingObligations
            } catch (ex: Exception) {
                if (ex is SQLException && ex.message.toString().contains("Table \"OBLIGATIONS\" not found;"))
                    outgoingObligations
                else {
                    logger.error("GetQueue.OutgoingById(): " + ex.message.toString())
                    throw ex
                }
            }
        }
    }

    /**
     * Flow for getting outgoingObligationsFlow based on status
     */
    @InitiatingFlow
    @StartableByRPC
    class OutgoingWithStatus(val status: Int) : FlowLogic<List<ObligationModel>>() {
        @Suspendable
        override fun call(): List<ObligationModel> {
            logger.info("GetQueue.OutgoingWithStatus: Getting obligations with $status")
            val statusIsValid = OBLIGATION_STATUS.values().map { it.ordinal }.contains(status)
            if (!statusIsValid) throw IllegalArgumentException("Status given is invalid")
            return try {
                val customVaultQueryService = serviceHub.cordaService(PersistentObligationQueue::class.java)
                val outgoingObligations = customVaultQueryService.getOutgoingObligationFromStatus(status)
                outgoingObligations.toList()
            } catch (ex: Exception) {
                if (ex is SQLException && ex.message.toString().contains("Table \"OBLIGATIONS\" not found;"))
                    listOf()
                else {
                    logger.error("GetQueue.OutgoingWithStatus(): " + ex.message.toString())
                    throw ex
                }
            }
        }
    }

    /**
     * Flow for API to call to return incoming obligations in json
     */
    // TODO: This should be refactored.
    @StartableByRPC
    @InitiatingFlow
    class Incoming : FlowLogic<List<ObligationModel>>() {
        @Suspendable
        override fun call(): List<ObligationModel> {
            logger.info("GetQueue.Incoming: Getting obligations")
            val results: List<ObligationModel>
            try {
                val vaultStates = serviceHub.vaultService.queryBy<Obligation.State>().states
                val stateLinearId = vaultStates.map {
                    it.state.data.linearId
                }
                val verifiedTx = serviceHub.validatedTransactions.track().snapshot
                results =
                        verifiedTx.filter { tx ->
                            tx.tx.commands.contains(tx.tx.commands.find {
                                it.value is Obligation.Issue
                            })
                        }.filter { tx ->
                            tx.tx.outputs.first().data is Obligation.State
                        }.filter {
                            val compare = it.tx.outputs.single().data as Obligation.State
                            stateLinearId.contains(compare.linearId)
                        }.map { tx ->
                            @Suppress("UNCHECKED_CAST")
                            val outputs = tx.tx.outputs as List<TransactionState<Obligation.State>>
                            val sender = serviceHub.identityService.requireWellKnownPartyFromAnonymous(outputs.first().data.borrower)
                            val receiver = serviceHub.identityService.requireWellKnownPartyFromAnonymous(outputs.first().data.lender)
                            ObligationModel(
                                    transId = tx.tx.id.toString(),
                                    linearId = outputs.first().data.linearId.toString(),
                                    requestedDate = Date(outputs.first().data.issueDate.toEpochMilli()).toSimpleString(),
                                    updatedDate = Date(tx.tx.timeWindow!!.midpoint!!.toEpochMilli()).toSimpleString(),
                                    sender = sender.owningKey,
                                    receiver = receiver.owningKey,
                                    transactionAmount = (outputs.first().data.amount.quantity).to2Decimals(),
                                    currency = outputs.first().data.amount.token.currencyCode.toString())
                        }
                return results.filter { serviceHub.identityService.partyFromKey(it.receiver) == ourIdentity }.sortedBy { it.requestedDate }
            } catch(ex: Exception) {
                logger.error("GetQueue.Incoming(): " + ex.message.toString())
                throw ex
            }
        }
    }

    /**
     * Flow for API to call to return all obligations in json
     */
    @StartableByRPC
    @InitiatingFlow
    class AllUnconsumed : FlowLogic<List<ObligationModel>>() {
        @Suspendable
        override fun call(): List<ObligationModel> {
            logger.info("GetQueue.AllUnconsumed: Getting obligations")
            val results = mutableListOf<ObligationModel>()
            results.addAll(subFlow(GetQueue.OutgoingWithStatus(OBLIGATION_STATUS.ACTIVE.ordinal)))
            results.addAll(subFlow(GetQueue.OutgoingWithStatus(OBLIGATION_STATUS.HOLD.ordinal)))
            results.addAll(subFlow(GetQueue.Incoming()))
            return results
        }
    }

    /**
     * Flow for API to call to return all obligations in json
     */
    @StartableByRPC
    @InitiatingFlow
    class OutgoingUnconsumed : FlowLogic<List<ObligationModel>>() {
        @Suspendable
        override fun call(): List<ObligationModel> {
            logger.info("GetQueue.OutgoingUnconsumed: Getting obligations")
            val results = mutableListOf<ObligationModel>()
            results.addAll(subFlow(GetQueue.OutgoingWithStatus(OBLIGATION_STATUS.ACTIVE.ordinal)))
            results.addAll(subFlow(GetQueue.OutgoingWithStatus(OBLIGATION_STATUS.HOLD.ordinal)))
            return results
        }
    }
}

/**
 * Flow for API call to settle the next highest obligations in queue if possible (until insufficient balance is thrown)
 */
@StartableByRPC
@InitiatingFlow
class SettleNextObligations : FlowLogic<List<ObligationModel>>() {
    @Suspendable
    override fun call(): List<ObligationModel> {
        logger.info("SettleNextObligations: Getting obligations to settle")
        val obligationsResult = mutableListOf<ObligationModel>()

        // Assume balance is enough
        var enoughCoins = true
        var moreObligations = true

        // 4. Try to settle.
        while (enoughCoins && moreObligations) {
            /**
             * There may be a chance that the SQL do not return all rows >~1000, so we want to be able to re-query if we still have enough coins
             * to settle the next X rows available, enoughCoins will stay true if the while(iterator.hasNext() never exhausted the coins
             */
            val outgoingObligations = subFlow(GetQueue.OutgoingWithStatus(OBLIGATION_STATUS.ACTIVE.ordinal))
            val iterator = outgoingObligations.iterator()
            if (outgoingObligations.isNotEmpty()) {
                while (iterator.hasNext()) {
                    val obligation = iterator.next()
                    val balance = serviceHub.getCashBalances()[SGD]
                    if (balance!!.quantity >= obligation.transactionAmount.toPenny()) {
                        logger.info("SettleNextObligations: settling obligation with ID: " + obligation.transId)
                        logger.info("SettleNextObligations: Balance " + balance)
                        logger.info("SettleNextObligations: settling obligation with amount: " + obligation.transactionAmount)
                        subFlow(SettleObligation.Initiator(UniqueIdentifier.fromString(obligation.linearId)))
                        obligation.status = OBLIGATION_STATUS.SETTLED.ordinal
                        obligationsResult.add(obligation)
                    } else {
                        logger.info("Insufficient Balance: " + balance + " to settle " + obligation.transactionAmount)
                        // No more coins, stop trying
                        enoughCoins = false
                        break
                    }
                }
                // No more obligations, stop querying despite enough coins
            } else {
                moreObligations = false
            }
        }
        return obligationsResult
    }
}

/**
 * Flow to issue a new obligation and return a post response
 */
@StartableByRPC
@InitiatingFlow
class PostIssueObligationFlow(val value: TransactionModel) : FlowLogic<TransactionModel>() {
    @Suspendable
    override fun call(): TransactionModel {
        logger.info("PostIssueObligationFlow: Logic to check obligation issuance request is valid")
        val maybeLender = serviceHub.identityService.partiesFromName(value.receiver!!, exactMatch = true)
        if (maybeLender.size != 1) throw IllegalArgumentException("Unknown Party")
        val lender = maybeLender.single()
        val stx = subFlow(IssueObligation.Initiator(
                SGD(value.transactionAmount!!),
                lender,
                value.priority!!))
        val txId = stx.id
        val state = stx.tx.outputs.single().data as Obligation.State
        val sender = serviceHub.identityService.requireWellKnownPartyFromAnonymous(state.borrower)
        val receiver = serviceHub.identityService.requireWellKnownPartyFromAnonymous(state.lender)
        return TransactionModel(
                transId = txId.toString(),
                linearId = state.linearId.toString(),
                sender = sender.name.organisation,
                receiver = receiver.name.organisation,
                transactionAmount = state.amount.quantity.to2Decimals(),
                currency = state.amount.token.currencyCode.toString(),
                priority = value.priority)
    }
}

/**
 * Flow to query obligation states by transId
 */
@StartableByRPC
@InitiatingFlow
class GetObligationFromTransId(val transactionId: String) : FlowLogic<Obligation.State>() {
    @Suspendable
    override fun call(): Obligation.State {
        logger.info("GetObligationFromTransId: Query obligation from transId")
        val transaction = serviceHub.validatedTransactions.getTransaction(SecureHash.parse(transactionId))
                ?: throw IllegalArgumentException("Transaction $transactionId not found")

        if (transaction.tx.outputs.single().data !is Obligation.State) {
            throw IllegalArgumentException("Transaction $transactionId state is of invalid type")
        }

        val state = transaction.tx.outputs.single().data as Obligation.State
        val generalCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
        val transQueryCriteria = QueryCriteria.LinearStateQueryCriteria(uuid = listOf(UUID.fromString(state.linearId.toString())))
        val obligationStates = serviceHub.vaultService.queryBy<Obligation.State>(generalCriteria.and(transQueryCriteria))

        if (obligationStates.states.isEmpty()) {
            throw IllegalArgumentException("State within $transactionId has been consumed")
        }
        return state
    }
}