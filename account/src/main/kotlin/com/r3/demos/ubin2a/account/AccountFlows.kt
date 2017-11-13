package com.r3.demos.ubin2a.account

import co.paralleluniverse.fibers.Suspendable
import com.r3.demos.ubin2a.base.*
import com.r3.demos.ubin2a.obligation.GetQueue
import com.r3.demos.ubin2a.obligation.Obligation
import com.r3.demos.ubin2a.pledge.Pledge
import com.r3.demos.ubin2a.redeem.Redeem
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.contracts.getCashBalance
import java.util.*

/**
 * Get the balance, incoming sum, outgoing sum and position of the bank
 */
@StartableByRPC
@InitiatingFlow
class GetBalanceFlow : FlowLogic<BankModel>() {
    @Suspendable
    override fun call() : BankModel {
        logger.info("Getting cash balance")
        val maybeBalance = serviceHub.getCashBalance(SGD)
        var balance = 0L
        if (maybeBalance.quantity != balance) balance = maybeBalance.quantity

        val incomingObligations = subFlow(GetQueue.Incoming())
        var incomingSum = 0L
        incomingObligations.forEach {
            incomingSum += it.transactionAmount.toPenny()
        }

        val outgoingObligations = subFlow(GetQueue.OutgoingUnconsumed())
        var outgoingSum = 0L
        outgoingObligations.forEach {
            outgoingSum += it.transactionAmount.toPenny()
        }

        val position = balance + incomingSum - outgoingSum

        return BankModel(
                ourIdentity.name.organisation,
                ourIdentity.name.toString(),
                balance.to2Decimals(),
                incomingSum.to2Decimals(),
                outgoingSum.to2Decimals(),
                position.to2Decimals())
    }
}

/**
 * Get the balance of each of the banks in the network
 */
object BalanceByBanksFlow {
    @StartableByRPC
    @InitiatingFlow
    class Initiator : FlowLogic<List<BankModel>>() {
        @Suspendable
        override fun call(): List<BankModel> {
            logger.info("Querying other bank node")
            val bankList = ArrayList<BankModel>()
            val networkParticipants = serviceHub.networkMapCache.allNodes

            val filterBanksOnly = networkParticipants.filter {
                nodeInfo -> nodeInfo.isNotary(serviceHub).not() && nodeInfo.isCentralBank().not() && nodeInfo.isRegulator().not() && nodeInfo.isMe(serviceHub.myInfo).not() && nodeInfo.isNetworkMap().not()
            }

            filterBanksOnly.forEach {
                val session = initiateFlow(it.legalIdentities.first())
                logger.info("Requesting balance for bank ${it.legalIdentities.first().name.organisation}")
                session.send(Unit)
                val cashStates = subFlow(ReceiveStateAndRefFlow<Cash.State>(session))

                val balance = cashStates.map { it.state.data.amount.quantity }.sum().to2Decimals()
                val eachBank = BankModel(
                        bic = it.legalIdentities.first().name.organisation,
                        X500Name = it.legalIdentities.first().name.toString(),
                        balance = balance)
                bankList.add(eachBank)
            }
            return bankList
        }
    }

    /**
     * The other side of the above flow. For the purposes of this PoC, we won't add any additional checking.
     */
    @InitiatedBy(BalanceByBanksFlow.Initiator::class)
    class Responder(val session: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val counterParty = session.counterparty
            logger.info("BalanceByBanksFlow.Responder: Requested balance from bank ${counterParty.name.organisation}")
            if(counterParty.name != CENTRAL_PARTY_X500){
                throw FlowException("Initiator is not central bank")
            }

            session.receive<Unit>()
            val allMyCash = serviceHub.vaultService.queryBy<Cash.State>().states
            subFlow(SendStateAndRefFlow(session, allMyCash))
        }
    }
}

/**
 * Get the transaction history of the bank
 */
@StartableByRPC
@InitiatingFlow
class GetTransactionHistory(val selectedTransType: Int? = null) : FlowLogic<List<TransactionModel>>() {
    @Suspendable
    override fun call(): List<TransactionModel> {
        logger.info("GetTransactionHistory: Querying transaction snapshot")
        val transactionsSnapshot = serviceHub.validatedTransactions.track().snapshot
        if(transactionsSnapshot.isEmpty()) return emptyList()

        val transactionsHistory = ArrayList<TransactionModel>()
        transactionsSnapshot.forEach {

            var transType = ""
            val transId = it.tx.id.toString()
            var linearId: String? = null
            var sender  = "Anonymous Sender"
            var receiver = "Anonymous Receiver"
            var transactionAmount = 0.00
            var requestedDate = ""
            var updatedDate = ""
            var status: String? = null

            // Only 1 command in the TX
            if(it.tx.commands.size == 1) {
                if (selectedTransType != TRANSACTION_TYPE.TRANSFER.ordinal && selectedTransType != null){ return@forEach }
                /** Peer-to-peer fund transfer with Cash Move command - funds move bilaterally**/
                if (it.tx.commands.any { it.value is Cash.Commands.Move }) {
                    logger.info("GetTransactionHistory: Querying Cash movement")
                    val cashOutputs = it.tx.outputsOfType<Cash.State>()
                    val newCashOwner = serviceHub.identityService.wellKnownPartyFromAnonymous(cashOutputs.first().owner)
                    val firstSigner = serviceHub.identityService.partyFromKey(it.tx.requiredSigningKeys.first())

                    transType = TRANSACTION_TYPE.TRANSFER.name.toLowerCase()
                    status = OBLIGATION_STATUS.SETTLED.name.toLowerCase()
                    transactionAmount = cashOutputs.first().amount.quantity.to2Decimals()
                    requestedDate = Date(it.tx.timeWindow!!.midpoint!!.toEpochMilli()).toSimpleString()
                    updatedDate = Date(it.tx.timeWindow!!.midpoint!!.toEpochMilli()).toSimpleString()

                    if (firstSigner != null && newCashOwner != null) {
                        sender = firstSigner.name.organisation
                        receiver = newCashOwner.name.organisation
                    }
                }

                /** Obligation cancellation - no funds movement **/
                else if (it.tx.commands.any { it.value is Obligation.Exit }) {
                    logger.info("GetTransactionHistory: Querying Obligation Exit movement")
                    val ltx = it.tx.toLedgerTransaction(serviceHub)
                    val obligationState = ltx.inputsOfType<Obligation.State>().first()

                    transType = TRANSACTION_TYPE.TRANSFER.name.toLowerCase()
                    status = OBLIGATION_STATUS.CANCELLED.name.toLowerCase()
                    linearId = obligationState.linearId.toString()
                    transactionAmount = obligationState.amount.quantity.to2Decimals()
                    requestedDate = Date(obligationState.issueDate.toEpochMilli()).toSimpleString()
                    updatedDate = Date(it.tx.timeWindow!!.midpoint!!.toEpochMilli()).toSimpleString()

                    // Get sender and receiver
                    val senderParty = serviceHub.identityService.wellKnownPartyFromAnonymous(obligationState.borrower)
                    val receiverParty = serviceHub.identityService.wellKnownPartyFromAnonymous(obligationState.lender)

                    if(senderParty != null && receiverParty != null) {
                        sender = senderParty.name.organisation
                        receiver = receiverParty.name.organisation
                    }
                }

                if (sender == (serviceHub.myInfo.legalIdentities.first().name.organisation) ||
                        receiver == (serviceHub.myInfo.legalIdentities.first().name.organisation)) {
                    val transaction = TransactionModel(
                            transType = transType,
                            transId = transId,
                            linearId = linearId,
                            sender = sender,
                            receiver = receiver,
                            transactionAmount = transactionAmount,
                            requestedDate = requestedDate,
                            updatedDate = updatedDate,
                            status = status)
                    transactionsHistory.add(transaction)
                }
            } // End command == 1 condtion

            // Only 2 commands in the TX
            else if(it.tx.commands.size == 2) {

                /** Obligation settlement - funds move in bilateral direction **/
                if (it.tx.commands.any { it.value is Obligation.Settle }) {
                    logger.info("GetTransactionHistory: Querying Obligation settle movement")
                    if (selectedTransType != TRANSACTION_TYPE.TRANSFER.ordinal && selectedTransType != null){ return@forEach }
                    val tlx = it.tx.toLedgerTransaction(serviceHub)
                    val inputObligations = tlx.inputsOfType<Obligation.State>()

                    transType = TRANSACTION_TYPE.TRANSFER.name.toLowerCase()
                    status = OBLIGATION_STATUS.SETTLED.name.toLowerCase()
                    updatedDate = Date(it.tx.timeWindow!!.midpoint!!.toEpochMilli()).toSimpleString()

                    // Get sender and receiver
                    inputObligations.forEach { (amount, lender, borrower, issueDate, linearId1) ->
                        linearId = linearId1.toString()
                        requestedDate = Date(issueDate.toEpochMilli()).toSimpleString()
                        transactionAmount = amount.quantity.to2Decimals()

                        val obligationSender = serviceHub.identityService.wellKnownPartyFromAnonymous(borrower)
                        val obligationReceiver = serviceHub.identityService.wellKnownPartyFromAnonymous(lender)

                        // I only add those that I either am the sender or receiver
                        if(obligationSender != null && obligationReceiver != null){
                            sender = obligationSender.name.organisation
                            receiver = obligationReceiver.name.organisation
                            val transaction = TransactionModel(
                                    transType = transType,
                                    transId = transId,
                                    linearId = linearId,
                                    sender = sender,
                                    receiver = receiver,
                                    transactionAmount = transactionAmount,
                                    requestedDate = requestedDate,
                                    updatedDate = updatedDate,
                                    status = status)
                            transactionsHistory.add(transaction)
                        }
                    }
                }
                /**
                 * Redeem transaction type - funds move from bank out to MAS
                 */
                else if (it.tx.commands.any { it.value is Redeem.Issue }) {
                    logger.info("GetTransactionHistory: Querying Redeem issuance movement")
                    if (selectedTransType != TRANSACTION_TYPE.REDEEM.ordinal && selectedTransType != null){ return@forEach }
                    val ltx = it.tx.toLedgerTransaction(serviceHub)
                    val redeemStates = ltx.outputsOfType<Redeem.State>()

                    transType = TRANSACTION_TYPE.REDEEM.name.toLowerCase()
                    linearId = redeemStates.first().linearId.toString()
                    transactionAmount = redeemStates.first().amount.quantity.to2Decimals()
                    requestedDate = Date(redeemStates.first().issueDate.toEpochMilli()).toSimpleString()
                    updatedDate = Date(it.tx.timeWindow!!.midpoint!!.toEpochMilli()).toSimpleString()
                    status = OBLIGATION_STATUS.SETTLED.name.toLowerCase().toLowerCase()

                    // Get sender and receiver
                    val anonymousRequester = redeemStates.first().requester
                    val senderParty = serviceHub.identityService.wellKnownPartyFromAnonymous(anonymousRequester)
                    val anonymousApprover = redeemStates.first().approver
                    val receiverParty = serviceHub.identityService.wellKnownPartyFromAnonymous(anonymousApprover)

                    // Add tx into history
                    if(receiverParty != null && senderParty != null ) {
                        val transaction = TransactionModel(
                                transType = transType,
                                transId = transId,
                                linearId = linearId,
                                sender = senderParty.name.organisation,
                                receiver = receiverParty.name.organisation,
                                transactionAmount = transactionAmount,
                                requestedDate = requestedDate,
                                updatedDate = updatedDate,
                                status = status)
                        transactionsHistory.add(transaction)
                    }

                    /**
                     * Pledge transaction type - funds move into bank from MAS
                     */
                } else if (it.tx.commands.any { it.value is Pledge.Issue }) {
                    logger.info("GetTransactionHistory: Querying Pledge issuance movement")
                    if (selectedTransType != TRANSACTION_TYPE.PLEDGE.ordinal && selectedTransType != null){ return@forEach }
                    val ltx = it.tx.toLedgerTransaction(serviceHub)
                    val pledgeStates = ltx.outputsOfType<Pledge.State>()

                    transType = TRANSACTION_TYPE.PLEDGE.name.toLowerCase()
                    linearId = pledgeStates.first().linearId.toString()
                    transactionAmount = pledgeStates.first().amount.quantity.to2Decimals()
                    requestedDate = Date(pledgeStates.first().issueDate.toEpochMilli()).toSimpleString()
                    updatedDate = Date(it.tx.timeWindow!!.midpoint!!.toEpochMilli()).toSimpleString()
                    status = OBLIGATION_STATUS.SETTLED.name.toLowerCase().toLowerCase()

                    // Get sender and receiver
                    val anonymousRequester = pledgeStates.first().requester
                    val receiverParty = serviceHub.identityService.wellKnownPartyFromAnonymous(anonymousRequester)
                    val anonymousApprover = pledgeStates.first().approver
                    val senderParty = serviceHub.identityService.wellKnownPartyFromAnonymous(anonymousApprover)

                    // Add tx into history
                    if(receiverParty != null && senderParty != null ) {
                        val transaction = TransactionModel(
                                transType = transType,
                                transId = transId,
                                linearId = linearId,
                                sender  = senderParty.name.organisation,
                                receiver = receiverParty.name.organisation,
                                transactionAmount = transactionAmount,
                                requestedDate = requestedDate,
                                updatedDate = updatedDate,
                                status = status)
                        transactionsHistory.add(transaction)
                    }
                }
            } // End command == 2 condition
        } // End transactionsSnapshot.forEach
        return transactionsHistory.sortedByDescending { it.updatedDate }
    }
}
