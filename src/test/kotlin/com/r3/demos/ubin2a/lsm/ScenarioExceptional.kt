package com.r3.demos.ubin2a.lsm

import com.r3.demos.ubin2a.account.DeadlockNotificationFlow
import com.r3.demos.ubin2a.account.DeadlockService
import com.r3.demos.ubin2a.account.StartLSMFlow
import com.r3.demos.ubin2a.base.*
import com.r3.demos.ubin2a.cash.Pay
import com.r3.demos.ubin2a.detect.*
import com.r3.demos.ubin2a.execute.ReceiveFinalisedTransactionFlow
import com.r3.demos.ubin2a.execute.ReceiveGatherStatesRequest
import com.r3.demos.ubin2a.execute.ReceiveNettingData
import com.r3.demos.ubin2a.execute.ReceiveSignedTransactionFlow
import com.r3.demos.ubin2a.obligation.IssueObligation
import com.r3.demos.ubin2a.obligation.Obligation
import com.r3.demos.ubin2a.obligation.PersistentObligationQueue
import com.r3.demos.ubin2a.obligation.SettleObligation
import com.r3.demos.ubin2a.pledge.ApprovePledge
import com.r3.demos.ubin2a.ubin2aTestHelpers.createObligation
import net.corda.core.flows.FlowException
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
import java.util.*
import kotlin.test.assertFailsWith

/**
 * ScenarioSix: 5 participants - not all obligations are settled
 */
class ScenarioExceptional {
    lateinit var net: MockNetwork
    lateinit var bank1: StartedNode<MockNetwork.MockNode>
    lateinit var bank2: StartedNode<MockNetwork.MockNode>
    lateinit var bank3: StartedNode<MockNetwork.MockNode>
    lateinit var bank4: StartedNode<MockNetwork.MockNode>
    lateinit var bank5: StartedNode<MockNetwork.MockNode>
    lateinit var bank6: StartedNode<MockNetwork.MockNode>
    lateinit var bank7: StartedNode<MockNetwork.MockNode>
    lateinit var bank8: StartedNode<MockNetwork.MockNode>
    lateinit var bank9: StartedNode<MockNetwork.MockNode>
    lateinit var bank10: StartedNode<MockNetwork.MockNode>
    lateinit var bank11: StartedNode<MockNetwork.MockNode>
    lateinit var bank12: StartedNode<MockNetwork.MockNode>
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
                "com.r3.demos.ubin2a.pledge"
        )
        net = MockNetwork(threadPerNode = true)
        val nodes = net.createSomeNodes(12)
        bank1 = nodes.partyNodes[0] // Mock company 2
        bank2 = nodes.partyNodes[1] // Mock company 3
        bank3 = nodes.partyNodes[2] // Mock company 4
        bank4 = nodes.partyNodes[3] // Mock company 5
        bank5 = nodes.partyNodes[4] // Mock company 6
        bank6 = nodes.partyNodes[5] // Mock company 7
        bank7 = nodes.partyNodes[6] // Mock company 8
        bank8 = nodes.partyNodes[7] // Mock company 9
        bank9 = nodes.partyNodes[8] // Mock company 10
        bank10 = nodes.partyNodes[9] // Mock company 11
        bank11 = nodes.partyNodes[10] // Mock company 12
        bank12 = nodes.partyNodes[11] // Mock company 13
        regulator = net.createPartyNode(nodes.mapNode.network.myAddress, REGULATOR_PARTY_X500) // Regulator
        centralBank = net.createPartyNode(nodes.mapNode.network.myAddress, CENTRAL_PARTY_X500) // Central Bank

        nodes.partyNodes.forEach { it.register() }
        centralBank.register()
        regulator.register()

    }

    @After
    fun tearDown() {
        net.stopNodes()
        unsetCordappPackages()
    }

    private fun StartedNode<MockNetwork.MockNode>.register() {
        val it = this
        it.registerInitiatedFlow(IssueObligation.Responder::class.java)
        it.registerInitiatedFlow(ReceiveScanRequest::class.java)
        it.registerInitiatedFlow(ReceiveScanAcknowledgement::class.java)
        it.registerInitiatedFlow(ReceiveScanResponse::class.java)
        it.registerInitiatedFlow(SettleObligation.Responder::class.java)
        it.registerInitiatedFlow(SendKeyFlow::class.java)
        it.registerInitiatedFlow(ReceiveNettingData::class.java)
        it.registerInitiatedFlow(ReceiveGatherStatesRequest::class.java)
        it.registerInitiatedFlow(ReceiveSignedTransactionFlow::class.java)
        it.registerInitiatedFlow(ReceiveFinalisedTransactionFlow::class.java)
        it.registerInitiatedFlow(ReceivePurgeRequest::class.java)
        it.registerInitiatedFlow(DeadlockNotificationFlow.Responder::class.java)
        it.database.transaction {
            it.internals.installCordaService(PersistentObligationQueue::class.java)
            it.internals.installCordaService(TemporaryKeyManager::class.java)
            it.internals.installCordaService(DeadlockService::class.java)
        }
    }

    private fun printCashBalances() {
        val bank1 = bank1.database.transaction { bank1.services.getCashBalance(sgd) }
        val bank2 = bank2.database.transaction { bank2.services.getCashBalance(sgd) }
        val bank3 = bank3.database.transaction { bank3.services.getCashBalance(sgd) }
        val bank4 = bank4.database.transaction { bank4.services.getCashBalance(sgd) }
        val bank5 = bank5.database.transaction { bank5.services.getCashBalance(sgd) }
        val bank6 = bank6.database.transaction { bank6.services.getCashBalance(sgd) }
        val bank7 = bank7.database.transaction { bank7.services.getCashBalance(sgd) }
        val bank8 = bank8.database.transaction { bank8.services.getCashBalance(sgd) }
        val bank9 = bank9.database.transaction { bank9.services.getCashBalance(sgd) }
        val bank10 = bank10.database.transaction { bank10.services.getCashBalance(sgd) }
        val bank11 = bank11.database.transaction { bank11.services.getCashBalance(sgd) }
        println("Bank1: $bank1, bank2: $bank2, bank3: $bank3, bank4: $bank4, bank5: $bank5")
        println("Bank6: $bank6, bank7: $bank7, bank8: $bank8, bank9: $bank9, bank10: $bank10, bank11: $bank11")
    }

    /**
     * 11 participants with 1 focus receiver and 1 focus sender
     * LSM runs: 1 runs
     * Expected: 0 obligations are settled (Deadlock)
     */
    @Test
    fun `Scenario E8_1`() {
        println("----------------------")
        println("Starting Scenario E8.1:")
        println("----------------------")
        val sgd = Currency.getInstance("SGD")
        printCashBalances()
        println()

        println("----------------------")
        println("Set up Starting Balance")
        println("----------------------")
        centralBank.services.startFlow(ApprovePledge.Initiator(SGD(2), bank1.services.myInfo.chooseIdentity())).resultFuture.getOrThrow()
        centralBank.services.startFlow(ApprovePledge.Initiator(SGD(2), bank2.services.myInfo.chooseIdentity())).resultFuture.getOrThrow()
        centralBank.services.startFlow(ApprovePledge.Initiator(SGD(2), bank3.services.myInfo.chooseIdentity())).resultFuture.getOrThrow()
        centralBank.services.startFlow(ApprovePledge.Initiator(SGD(2), bank4.services.myInfo.chooseIdentity())).resultFuture.getOrThrow()
        centralBank.services.startFlow(ApprovePledge.Initiator(SGD(2), bank5.services.myInfo.chooseIdentity())).resultFuture.getOrThrow()
        centralBank.services.startFlow(ApprovePledge.Initiator(SGD(6), bank6.services.myInfo.chooseIdentity())).resultFuture.getOrThrow()
        centralBank.services.startFlow(ApprovePledge.Initiator(SGD(2), bank7.services.myInfo.chooseIdentity())).resultFuture.getOrThrow()
        centralBank.services.startFlow(ApprovePledge.Initiator(SGD(2), bank8.services.myInfo.chooseIdentity())).resultFuture.getOrThrow()
        centralBank.services.startFlow(ApprovePledge.Initiator(SGD(2), bank9.services.myInfo.chooseIdentity())).resultFuture.getOrThrow()
        centralBank.services.startFlow(ApprovePledge.Initiator(SGD(2), bank10.services.myInfo.chooseIdentity())).resultFuture.getOrThrow()
        centralBank.services.startFlow(ApprovePledge.Initiator(SGD(2), bank11.services.myInfo.chooseIdentity())).resultFuture.getOrThrow()
        net.waitQuiescent()
        printCashBalances()
        println()

        println("----------------------")
        println("Set up obligations in a gridlock")
        println("----------------------")
        // Create obligation
        val fut1 = createObligation(bank2, bank1, SGD(500), 0).getOrThrow().tx.outputStates.single() as Obligation.State
        val fut2 = createObligation(bank3, bank2, SGD(10), 0).getOrThrow().tx.outputStates.single() as Obligation.State
        val fut3 = createObligation(bank4, bank2, SGD(20), 0).getOrThrow().tx.outputStates.single() as Obligation.State
        val fut4 = createObligation(bank5, bank2, SGD(30), 0).getOrThrow().tx.outputStates.single() as Obligation.State
        val fut5 = createObligation(bank6, bank2, SGD(40), 0).getOrThrow().tx.outputStates.single() as Obligation.State
        val fut6 = createObligation(bank7, bank2, SGD(50), 0).getOrThrow().tx.outputStates.single() as Obligation.State
        val fut7 = createObligation(bank8, bank2, SGD(60), 0).getOrThrow().tx.outputStates.single() as Obligation.State
        val fut8 = createObligation(bank9, bank2, SGD(70), 0).getOrThrow().tx.outputStates.single() as Obligation.State
        val fut9 = createObligation(bank10, bank2, SGD(80), 0).getOrThrow().tx.outputStates.single() as Obligation.State
        val fut10 = createObligation(bank11, bank2, SGD(140), 0).getOrThrow().tx.outputStates.single() as Obligation.State
        val fut11 = createObligation(bank1, bank3, SGD(60), 0).getOrThrow().tx.outputStates.single() as Obligation.State
        val fut12 = createObligation(bank1, bank4, SGD(70), 0).getOrThrow().tx.outputStates.single() as Obligation.State
        val fut13 = createObligation(bank1, bank5, SGD(80), 0).getOrThrow().tx.outputStates.single() as Obligation.State
        val fut14 = createObligation(bank1, bank6, SGD(90), 0).getOrThrow().tx.outputStates.single() as Obligation.State
        val fut15 = createObligation(bank1, bank7, SGD(200), 0).getOrThrow().tx.outputStates.single() as Obligation.State
        val fut16 = createObligation(bank3, bank8, SGD(50), 0).getOrThrow().tx.outputStates.single() as Obligation.State
        val fut17 = createObligation(bank4, bank9, SGD(50), 0).getOrThrow().tx.outputStates.single() as Obligation.State
        val fut18 = createObligation(bank5, bank10, SGD(50), 0).getOrThrow().tx.outputStates.single() as Obligation.State
        val fut19 = createObligation(bank6, bank11, SGD(50), 0).getOrThrow().tx.outputStates.single() as Obligation.State
        val fut20 = createObligation(bank7, bank10, SGD(10), 0).getOrThrow().tx.outputStates.single() as Obligation.State
        val fut21 = createObligation(bank7, bank11, SGD(140), 0).getOrThrow().tx.outputStates.single() as Obligation.State
        val fut22 = createObligation(bank11, bank8, SGD(10), 0).getOrThrow().tx.outputStates.single() as Obligation.State
        val fut23 = createObligation(bank11, bank9, SGD(20), 0).getOrThrow().tx.outputStates.single() as Obligation.State
        val fut24 = createObligation(bank11, bank10, SGD(30), 0).getOrThrow().tx.outputStates.single() as Obligation.State
        val fut25 = createObligation(bank10, bank11, SGD(10), 0).getOrThrow().tx.outputStates.single() as Obligation.State
        net.waitQuiescent()
        println("--------------------------")
        println("Balance before LSM")
        println("--------------------------")
        printCashBalances()
        println()

        // Perform LSM.
        println("--------------------------")
        println("LSM Run #1")
        println("--------------------------")
        bank1.services.startFlow(StartLSMFlow()).resultFuture.getOrThrow()
        net.waitQuiescent()
        printCashBalances()
        println()

//        // Perform LSM.
//        println("--------------------------")
//        println("LSM Run #1")
//        println("--------------------------")
//        // Perform netting.
//        println("--------------------------")
//        println("Starting detect algorithm:")
//        println("--------------------------")
//        val four = bank1.services.startFlow(DetectFlow(sgd))
//        net.waitQuiescent()
//        val (obligations, limits) = four.resultFuture.getOrThrow()
//
//        println("-------------------------")
//        println("Detect algorithm results:")
//        println("-------------------------")
//        println(obligations)
//        println(limits)
//        // Perform netting.
//        println("--------------------")
//        println("Calculating netting:")
//        println("--------------------")
//        val flow = PlanFlow(obligations, limits, sgd)
//        val (paymentsToMake, resultantObligations) = bank1.services.startFlow(flow).resultFuture.getOrThrow()
//        net.waitQuiescent()
//        println("Payments to make:")
//        println(paymentsToMake)
//        println("Resultant obligations:")
//        println(resultantObligations)
//        val executeFlow = ExecuteFlow(obligations, resultantObligations, paymentsToMake)
//        bank1.services.startFlow(executeFlow).resultFuture.getOrThrow()
//        net.waitQuiescent()
//        println(ExecuteDataStore.obligations)
//        println(ExecuteDataStore.payments)
        printCashBalances()

//        val balance1_run1 = bank1.database.transaction { bank1.services.getCashBalance(sgd) }
//        val balance2_run1 = bank2.database.transaction { bank2.services.getCashBalance(sgd) }
//        val balance3_run1 = bank3.database.transaction { bank3.services.getCashBalance(sgd) }
//        val balance4_run1 = bank4.database.transaction { bank4.services.getCashBalance(sgd) }
//        val balance5_run1 = bank5.database.transaction { bank5.services.getCashBalance(sgd) }
//        assert(balance1_run1.quantity / 100 == 3L)
//        assert(balance2_run1.quantity / 100 == 4L)
//        assert(balance3_run1.quantity / 100 == 5L)
//        assert(balance4_run1.quantity / 100 == 4L)
//        assert(balance5_run1.quantity / 100 == 3L)
    }

    @Test
    fun `viable Cycle After a Deadlock Scenario`() {
        println("----------------------")
        println("Starting Scenario:")
        println("----------------------")
        val sgd = Currency.getInstance("SGD")
        printCashBalances()
        println()

        println("----------------------")
        println("Set up Starting Balance")
        println("----------------------")
        centralBank.services.startFlow(ApprovePledge.Initiator(SGD(3), bank1.services.myInfo.chooseIdentity())).resultFuture.getOrThrow()
        centralBank.services.startFlow(ApprovePledge.Initiator(SGD(4), bank2.services.myInfo.chooseIdentity())).resultFuture.getOrThrow()
        centralBank.services.startFlow(ApprovePledge.Initiator(SGD(5), bank3.services.myInfo.chooseIdentity())).resultFuture.getOrThrow()
        centralBank.services.startFlow(ApprovePledge.Initiator(SGD(4), bank4.services.myInfo.chooseIdentity())).resultFuture.getOrThrow()
        centralBank.services.startFlow(ApprovePledge.Initiator(SGD(3), bank5.services.myInfo.chooseIdentity())).resultFuture.getOrThrow()
        net.waitQuiescent()
        printCashBalances()
        println()

        println("----------------------")
        println("Set up obligations in a gridlock 7.1")
        println("----------------------")
        // Create obligation
        val fut1 = createObligation(bank2, bank1, SGD(8), 0).getOrThrow().tx.outputStates.single() as Obligation.State
        val fut2 = createObligation(bank3, bank2, SGD(10), 0).getOrThrow().tx.outputStates.single() as Obligation.State
        val fut3 = createObligation(bank4, bank3, SGD(20), 0).getOrThrow().tx.outputStates.single() as Obligation.State
        val fut4 = createObligation(bank5, bank4, SGD(30), 0).getOrThrow().tx.outputStates.single() as Obligation.State
        val fut5 = createObligation(bank1, bank5, SGD(40), 0).getOrThrow().tx.outputStates.single() as Obligation.State

        net.waitQuiescent()
        println("--------------------------")
        println("Balance before LSM")
        println("--------------------------")
        printCashBalances()
        println()

        // Perform LSM.
        println("--------------------------")
        println("LSM Run #1")
        println("--------------------------")
        assertFailsWith<FlowException>("Deadlock!") {
            bank1.services.startFlow(StartLSMFlow()).resultFuture.getOrThrow() }
        net.waitQuiescent()
        println("--------------------------")
        println("Run #1 balances")
        println("--------------------------")
        printCashBalances()
        println()

        println("--------------------------")
        println("Inject funds to break deadlock of 7.1")
        println("--------------------------")
        centralBank.services.startFlow(ApprovePledge.Initiator(SGD(10), bank3.services.myInfo.chooseIdentity())).resultFuture.getOrThrow()
        centralBank.services.startFlow(ApprovePledge.Initiator(SGD(10), bank4.services.myInfo.chooseIdentity())).resultFuture.getOrThrow()
        centralBank.services.startFlow(ApprovePledge.Initiator(SGD(10), bank5.services.myInfo.chooseIdentity())).resultFuture.getOrThrow()
        printCashBalances()

        println("--------------------------")
        println("LSM Run #2")
        println("--------------------------")
        bank1.services.startFlow(StartLSMFlow()).resultFuture.getOrThrow()
        net.waitQuiescent()
        println("--------------------------")
        println("Run #2 balances")
        println("--------------------------")
        printCashBalances()

        val balance1_run2 = bank1.database.transaction { bank1.services.getCashBalance(sgd) }
        val balance2_run2 = bank2.database.transaction { bank2.services.getCashBalance(sgd) }
        val balance3_run2 = bank3.database.transaction { bank3.services.getCashBalance(sgd) }
        val balance4_run2 = bank4.database.transaction { bank4.services.getCashBalance(sgd) }
        val balance5_run2 = bank5.database.transaction { bank5.services.getCashBalance(sgd) }
        assert(balance1_run2.quantity / 100 == 35L)
        assert(balance2_run2.quantity / 100 == 2L)
        assert(balance3_run2.quantity / 100 == 5L)
        assert(balance4_run2.quantity / 100 == 4L)
        assert(balance5_run2.quantity / 100 == 3L)

        println("--------------------------")
        println("Zerorising Balances for next LSM scenario")
        println("--------------------------")
        bank1.services.startFlow(Pay(bank6.info.chooseIdentity(), SGD(35), OBLIGATION_PRIORITY.NORMAL.ordinal)).resultFuture.getOrThrow()
        bank2.services.startFlow(Pay(bank6.info.chooseIdentity(), SGD(2), OBLIGATION_PRIORITY.NORMAL.ordinal)).resultFuture.getOrThrow()
        bank3.services.startFlow(Pay(bank6.info.chooseIdentity(), SGD(5), OBLIGATION_PRIORITY.NORMAL.ordinal)).resultFuture.getOrThrow()
        bank4.services.startFlow(Pay(bank6.info.chooseIdentity(), SGD(4), OBLIGATION_PRIORITY.NORMAL.ordinal)).resultFuture.getOrThrow()
        bank5.services.startFlow(Pay(bank6.info.chooseIdentity(), SGD(3), OBLIGATION_PRIORITY.NORMAL.ordinal)).resultFuture.getOrThrow()
        printCashBalances()


        println("----------------------")
        println("Set up Starting Balance")
        println("----------------------")
        centralBank.services.startFlow(ApprovePledge.Initiator(SGD(3), bank1.services.myInfo.chooseIdentity())).resultFuture.getOrThrow()
        centralBank.services.startFlow(ApprovePledge.Initiator(SGD(4), bank2.services.myInfo.chooseIdentity())).resultFuture.getOrThrow()
        centralBank.services.startFlow(ApprovePledge.Initiator(SGD(5), bank3.services.myInfo.chooseIdentity())).resultFuture.getOrThrow()
        centralBank.services.startFlow(ApprovePledge.Initiator(SGD(4), bank4.services.myInfo.chooseIdentity())).resultFuture.getOrThrow()
        centralBank.services.startFlow(ApprovePledge.Initiator(SGD(3), bank5.services.myInfo.chooseIdentity())).resultFuture.getOrThrow()
        net.waitQuiescent()
        printCashBalances()
        println()

        println("----------------------")
        println("Set up obligations in a gridlock for 7.4")
        println("----------------------")
        // Create obligation
        val fut1_1 = createObligation(bank2, bank1, SGD(30), 0).getOrThrow().tx.outputStates.single() as Obligation.State
        val fut2_1 = createObligation(bank3, bank2, SGD(5), 0).getOrThrow().tx.outputStates.single() as Obligation.State
        val fut3_1 = createObligation(bank1, bank3, SGD(6), 0).getOrThrow().tx.outputStates.single() as Obligation.State
        val fut4_1 = createObligation(bank5, bank1, SGD(7), 0).getOrThrow().tx.outputStates.single() as Obligation.State
        val fut5_1 = createObligation(bank4, bank5, SGD(4), 0).getOrThrow().tx.outputStates.single() as Obligation.State
        val fut6_1 = createObligation(bank1, bank4, SGD(5), 0).getOrThrow().tx.outputStates.single() as Obligation.State
        net.waitQuiescent()
        printCashBalances()

        println("--------------------------")
        println("LSM Run #3")
        println("--------------------------")
        bank3.services.startFlow(StartLSMFlow()).resultFuture.getOrThrow()
        net.waitQuiescent()
        println("--------------------------")
        println("Run #3 balances")
        println("--------------------------")
        printCashBalances()



    }
}