package com.r3.demos.ubin2a.account

import com.r3.demos.ubin2a.base.*
import com.r3.demos.ubin2a.cash.AcceptPayment
import com.r3.demos.ubin2a.pledge.ApprovePledge
import com.r3.demos.ubin2a.cash.Pay
import com.r3.demos.ubin2a.detect.*
import com.r3.demos.ubin2a.execute.*
import com.r3.demos.ubin2a.account.GetTransactionHistory
import com.r3.demos.ubin2a.obligation.*
import com.r3.demos.ubin2a.plan.PlanFlow
import com.r3.demos.ubin2a.redeem.ApproveRedeem
import com.r3.demos.ubin2a.redeem.ExternalRedeemService
import com.r3.demos.ubin2a.redeem.IssueRedeem
import com.r3.demos.ubin2a.ubin2aTestHelpers.createObligation
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowException
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.finance.contracts.getCashBalance
import net.corda.node.internal.StartedNode
import net.corda.testing.chooseIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.setCordappPackages
import net.corda.testing.unsetCordappPackages
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.*
import kotlin.test.assertFailsWith

class AccountTests {

    fun cancelObligation(borrower: StartedNode<MockNetwork.MockNode>,
                         linearId: UniqueIdentifier): CordaFuture<SignedTransaction> {
        val flow = CancelObligation.Initiator(linearId)
        return borrower.services.startFlow(flow).resultFuture
    }

    fun updateObligation(borrower: StartedNode<MockNetwork.MockNode>,
                         transactionId: UniqueIdentifier,
                         obligationStatus: OBLIGATION_STATUS): CordaFuture<Boolean> {
        val flow = UpdateObligationStatus(transactionId, obligationStatus)
        return borrower.services.startFlow(flow).resultFuture
    }

    lateinit var net: MockNetwork
    lateinit var bank1: StartedNode<MockNetwork.MockNode>
    lateinit var bank2: StartedNode<MockNetwork.MockNode>
    lateinit var bank3: StartedNode<MockNetwork.MockNode>
    lateinit var bank4: StartedNode<MockNetwork.MockNode>
    lateinit var bank5: StartedNode<MockNetwork.MockNode>
    lateinit var regulator: StartedNode<MockNetwork.MockNode>
    lateinit var centralBank: StartedNode<MockNetwork.MockNode>

    val sgd = Currency.getInstance("SGD")

    @Before
    fun setup() {
        setCordappPackages(
                "net.corda.finance",
                "com.r3.demos.ubin2a.obligation",
                "com.r3.demos.ubin2a.cash",
                "com.r3.demos.ubin2a.detect",
                "com.r3.demos.ubin2a.plan",
                "com.r3.demos.ubin2a.execute",
                "com.r3.demos.ubin2a.pledge",
                "com.r3.demos.ubin2a.redeem",
                "com.r3.demos.ubin2a.account"
        )
        net = MockNetwork(threadPerNode = true)
        val nodes = net.createSomeNodes(6)
        bank1 = nodes.partyNodes[0] // Mock company 2
        bank2 = nodes.partyNodes[1] // Mock company 3
        bank3 = nodes.partyNodes[2] // Mock company 4
        bank4 = nodes.partyNodes[3] // Mock company 5
        bank5 = nodes.partyNodes[4] // Mock company 6
        regulator = net.createPartyNode(nodes.mapNode.network.myAddress, REGULATOR_PARTY_X500) // Regulator
        centralBank = net.createPartyNode(nodes.mapNode.network.myAddress, CENTRAL_PARTY_X500) // Central Bank

        nodes.partyNodes.forEach { it.register() }
        centralBank.register()

    }

    @After
    fun tearDown() {
        net.stopNodes()
        unsetCordappPackages()
    }

    private fun StartedNode<MockNetwork.MockNode>.register() {
        val it = this
        it.registerInitiatedFlow(IssueObligation.Responder::class.java)
        it.registerInitiatedFlow(AcceptPayment::class.java)
        it.registerInitiatedFlow(CancelObligation.Responder::class.java)
        it.registerInitiatedFlow(SettleObligation.Responder::class.java)
        it.registerInitiatedFlow(IssueRedeem.Responder::class.java)
        it.registerInitiatedFlow(ApproveRedeem.Responder::class.java)
        it.registerInitiatedFlow(BalanceByBanksFlow.Responder::class.java)
        it.registerInitiatedFlow(ReceiveScanRequest::class.java)
        it.registerInitiatedFlow(ReceiveScanAcknowledgement::class.java)
        it.registerInitiatedFlow(ReceiveScanResponse::class.java)
        it.registerInitiatedFlow(SendKeyFlow::class.java)
        it.registerInitiatedFlow(ReceiveNettingData::class.java)
        it.registerInitiatedFlow(ReceiveGatherStatesRequest::class.java)
        it.registerInitiatedFlow(ReceiveSignedTransactionFlow::class.java)
        it.registerInitiatedFlow(ReceiveFinalisedTransactionFlow::class.java)
        it.registerInitiatedFlow(ReceivePurgeRequest::class.java)
        it.database.transaction {
            it.internals.installCordaService(PersistentObligationQueue::class.java)
            it.internals.installCordaService(ExternalRedeemService.Service::class.java)
            it.internals.installCordaService(TemporaryKeyManager::class.java)
        }
    }

    private fun printCashBalances() {
        val bank1 = bank1.database.transaction { bank1.services.getCashBalance(sgd) }
        val bank2 = bank2.database.transaction { bank2.services.getCashBalance(sgd) }
        val bank3 = bank3.database.transaction { bank3.services.getCashBalance(sgd) }
        println("Bank1: $bank1, bank2: $bank2, bank3: $bank3")
    }

    @Test
    fun `Central bank can get balances by banks`() {
        println("----------------------")
        println("Test Get All Bank Balance")
        println("----------------------")
        val sgd = java.util.Currency.getInstance("SGD")
        centralBank.services.startFlow(ApprovePledge.Initiator(Amount(100000, sgd), bank1.services.myInfo.chooseIdentity())).resultFuture.getOrThrow()
        centralBank.services.startFlow(ApprovePledge.Initiator(Amount(100000, sgd), bank2.services.myInfo.chooseIdentity())).resultFuture.getOrThrow()
        centralBank.services.startFlow(ApprovePledge.Initiator(Amount(100000, sgd), bank3.services.myInfo.chooseIdentity())).resultFuture.getOrThrow()
        printCashBalances()
        println()

        val otherBankBalances = centralBank.services.startFlow(BalanceByBanksFlow.Initiator()).resultFuture.getOrThrow()
        println(otherBankBalances)
        val bank1Balance = otherBankBalances.find { it.bic == bank1.info.chooseIdentity().name.organisation }!!
        val bank2Balance = otherBankBalances.find { it.bic == bank2.info.chooseIdentity().name.organisation }!!
        val bank3Balance = otherBankBalances.find { it.bic == bank3.info.chooseIdentity().name.organisation }!!
        assert(bank1Balance.balance.toPenny()/100 == 1000L)
        assert(bank2Balance.balance.toPenny()/100 == 1000L)
        assert(bank3Balance.balance.toPenny()/100 == 1000L)
        net.waitQuiescent()
        println()
        assertFailsWith<FlowException>("Initiator is not central bank") {
            bank3.services.startFlow(BalanceByBanksFlow.Initiator()).resultFuture.getOrThrow()
        }

    }

    @Test
    fun `get Transactions History Simple`() {

        // Approve Pledge
        val stx1 = centralBank.services.startFlow(ApprovePledge.Initiator(SGD(1000.00), bank1.info.chooseIdentity())).resultFuture.getOrThrow()
        printCashBalances()

        // Bank 1 transfer $1 to Bank 2
        val stx2 = bank1.services.startFlow(Pay(bank2.info.chooseIdentity(), SGD(1.00), OBLIGATION_PRIORITY.NORMAL.ordinal)).resultFuture.getOrThrow()

        // Bank 1 create obligation $2.00 to Bank 2
        val stx3 = createObligation(bank2, bank1, SGD(2.00), 0).getOrThrow()
        val stx3_output = stx3.tx.outputsOfType<Obligation.State>().first()
        val stx3_settle = SettleObligation.Initiator(stx3_output.linearId)
        val stx3_settled = bank1.services.startFlow(stx3_settle).resultFuture.getOrThrow()

        // Bank 1 cancel obligation $3.00 to Bank 2
        val stx4 = createObligation(bank2, bank1, SGD(3.00), 0).getOrThrow()
        val stx4_output = stx4.tx.outputsOfType<Obligation.State>().first()
        val stx4_cancelled = cancelObligation(bank1, stx4_output.linearId).getOrThrow()
        net.waitQuiescent()

        // Bank 1 redeem $4.00 to MAS
        val stx5 = bank1.services.startFlow(IssueRedeem.Initiator(SGD(4.00))).resultFuture.getOrThrow()

        val txHistory = bank1.services.startFlow(GetTransactionHistory()).resultFuture.getOrThrow()
        txHistory.forEach {  println("$it") }
        assert(txHistory.size == 5)
    }

    /**
     * 10 total obligations (multiple cycles)
     * Nodes : 5 nodes
     * LSM runs: 2 run
     * Expected: 5 obligations are settled
     */
    @Test
    fun `get Transactions History After LSM `() {
        println("----------------------")
        println("Starting scenario 6.3:")
        println("----------------------")
        centralBank.services.startFlow(ApprovePledge.Initiator(Amount(300, sgd), bank1.services.myInfo.chooseIdentity())).resultFuture.getOrThrow()
        centralBank.services.startFlow(ApprovePledge.Initiator(Amount(400, sgd), bank2.services.myInfo.chooseIdentity())).resultFuture.getOrThrow()
        centralBank.services.startFlow(ApprovePledge.Initiator(Amount(500, sgd), bank3.services.myInfo.chooseIdentity())).resultFuture.getOrThrow()
        centralBank.services.startFlow(ApprovePledge.Initiator(Amount(400, sgd), bank4.services.myInfo.chooseIdentity())).resultFuture.getOrThrow()
        centralBank.services.startFlow(ApprovePledge.Initiator(Amount(300, sgd), bank5.services.myInfo.chooseIdentity())).resultFuture.getOrThrow()
        printCashBalances()
        println()

        createObligation(bank2, bank1, Amount(500, sgd),0).getOrThrow().tx.outputStates.single() as Obligation.State
        createObligation(bank3, bank2, Amount(600, sgd),0).getOrThrow().tx.outputStates.single() as Obligation.State
        createObligation(bank4, bank3, Amount(800, sgd),0).getOrThrow().tx.outputStates.single() as Obligation.State
        createObligation(bank5, bank3, Amount(8000, sgd),0).getOrThrow().tx.outputStates.single() as Obligation.State
        createObligation(bank5, bank4, Amount(700, sgd),0).getOrThrow().tx.outputStates.single() as Obligation.State
        createObligation(bank3, bank1, Amount(600, sgd),0).getOrThrow().tx.outputStates.single() as Obligation.State
        createObligation(bank1, bank5, Amount(800, sgd),0).getOrThrow().tx.outputStates.single() as Obligation.State
        createObligation(bank2, bank5, Amount(10000, sgd),0).getOrThrow().tx.outputStates.single() as Obligation.State
        createObligation(bank1, bank4, Amount(500, sgd),0).getOrThrow().tx.outputStates.single() as Obligation.State

        // Perform LSM.
        println("--------------------------")
        println("LSM Run #1")
        println("--------------------------")
        // Perform netting.
        println("--------------------------")
        println("Starting detect algorithm:")
        println("--------------------------")
        val four = bank1.services.startFlow(DetectFlow(sgd))
        net.waitQuiescent()
        val (obligations, limits) = four.resultFuture.getOrThrow()

        println("-------------------------")
        println("Detect algorithm results:")
        println("-------------------------")
        println(obligations)
        println(limits)
        // Perform netting.
        println("--------------------")
        println("Calculating netting:")
        println("--------------------")
        val flow = PlanFlow(obligations, limits, sgd)
        val (paymentsToMake, resultantObligations) = bank1.services.startFlow(flow).resultFuture.getOrThrow()
        net.waitQuiescent()
        println("Payments to make:")
        println(paymentsToMake)
        println("Resultant obligations:")
        println(resultantObligations)
        val executeFlow = ExecuteFlow(obligations, resultantObligations, paymentsToMake)
        bank1.services.startFlow(executeFlow).resultFuture.getOrThrow()
        net.waitQuiescent()
        println(ExecuteDataStore.obligations)
        println(ExecuteDataStore.payments)
        printCashBalances()

        val balance1_run1 = bank1.database.transaction { bank1.services.getCashBalance(sgd) }
        val balance2_run1 = bank2.database.transaction { bank2.services.getCashBalance(sgd) }
        val balance3_run1 = bank3.database.transaction { bank3.services.getCashBalance(sgd) }
        val balance4_run1 = bank4.database.transaction { bank4.services.getCashBalance(sgd) }
        val balance5_run1 = bank5.database.transaction { bank5.services.getCashBalance(sgd) }
        printCashBalances()
        assert(balance1_run1.quantity / 100 == 6L)
        assert(balance2_run1.quantity / 100 == 3L)
        assert(balance3_run1.quantity / 100 == 3L)
        assert(balance4_run1.quantity / 100 == 5L)
        assert(balance5_run1.quantity / 100 == 2L)

        val txHistory = bank1.services.startFlow(GetTransactionHistory()).resultFuture.getOrThrow()
        txHistory.forEach {  println("Transaction History: $it") }
        assert(txHistory.size == 3)
        net.waitQuiescent()
        println()
    }
}


