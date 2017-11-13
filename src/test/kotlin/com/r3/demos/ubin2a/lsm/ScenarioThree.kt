package com.r3.demos.ubin2a.lsm

import com.r3.demos.ubin2a.base.*
import com.r3.demos.ubin2a.detect.*
import com.r3.demos.ubin2a.execute.ReceiveFinalisedTransactionFlow
import com.r3.demos.ubin2a.execute.ReceiveGatherStatesRequest
import com.r3.demos.ubin2a.execute.ReceiveNettingData
import com.r3.demos.ubin2a.execute.ReceiveSignedTransactionFlow
import com.r3.demos.ubin2a.account.DeadlockNotificationFlow
import com.r3.demos.ubin2a.account.DeadlockService
import com.r3.demos.ubin2a.account.StartLSMFlow
import com.r3.demos.ubin2a.obligation.*
import com.r3.demos.ubin2a.plan.NettingException
import com.r3.demos.ubin2a.pledge.ApprovePledge
import com.r3.demos.ubin2a.ubin2aTestHelpers.createObligation
import com.r3.demos.ubin2a.ubin2aTestHelpers.printObligations
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
 * Scenario Two: 2 participants - not all obligations are settled
 */

class ScenarioThree {
    lateinit var net: MockNetwork
    lateinit var bank1: StartedNode<MockNetwork.MockNode>
    lateinit var bank2: StartedNode<MockNetwork.MockNode>
    lateinit var bank3: StartedNode<MockNetwork.MockNode>
    lateinit var bank4: StartedNode<MockNetwork.MockNode>
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
        val nodes = net.createSomeNodes(6)
        bank1 = nodes.partyNodes[0] // Mock company 2
        bank2 = nodes.partyNodes[1] // Mock company 3
        bank3 = nodes.partyNodes[2] // Mock company 4
        bank4 = nodes.partyNodes[3] // Mock company 4
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
        println("Bank1: $bank1, bank2: $bank2, bank3: $bank3, bank4: $bank4")
    }

    /**
     * 4 obligations, starting balance is 0 for all participants (2 cycles)
     * Nodes : 3 nodes
     * LSM runs: 1 run
     * Expected: No obligations are settled
     */
    @Test
    fun `Scenario 3_1`() {
        println("----------------------")
        println("Starting Scenario 3.1:")
        println("----------------------")
        val sgd = Currency.getInstance("SGD")
        printCashBalances()
        println()

        println("----------------------")
        println("Set up Starting Balance")
        println("----------------------")
        printCashBalances()
        println()

        println("----------------------")
        println("Set up obligations in a gridlock")
        println("----------------------")
        // Create obligation
        val fut1 = createObligation(bank3, bank1, SGD(2), 0).getOrThrow().tx.outputStates.single() as Obligation.State
        val fut2 = createObligation(bank3, bank2, SGD(3), 0).getOrThrow().tx.outputStates.single() as Obligation.State
        val fut3 = createObligation(bank2, bank3, SGD(5), 0).getOrThrow().tx.outputStates.single() as Obligation.State
        val fut4 = createObligation(bank1, bank2, SGD(2), 0).getOrThrow().tx.outputStates.single() as Obligation.State
        net.waitQuiescent()
        printCashBalances()
        printObligations(bank1)
        printObligations(bank2)
        printObligations(bank3)
        println()

        // Perform LSM.
        println("--------------------------")
        println("LSM Run #1")
        println("--------------------------")
        assertFailsWith<FlowException>("Deadlock!") {
            bank1.services.startFlow(StartLSMFlow()).resultFuture.getOrThrow() }
        net.waitQuiescent()
        printCashBalances()
        println()

        val balance1_run1 = bank1.database.transaction { bank1.services.getCashBalance(sgd) }
        val balance2_run1 = bank2.database.transaction { bank2.services.getCashBalance(sgd) }
        val balance3_run1 = bank3.database.transaction { bank3.services.getCashBalance(sgd) }
        assert(balance1_run1.quantity / 100 == 0L)
        assert(balance2_run1.quantity / 100 == 0L)
        assert(balance3_run1.quantity / 100 == 0L)
    }

    /**
     * 5 obligations (2 cycles)
     * LSM runs: 2 runs
     * Expected: No obligations are settled
     */
    @Test
    fun `Scenario 3_2`() {
        println("----------------------")
        println("Starting Scenario 3.2:")
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
        net.waitQuiescent()
        printCashBalances()
        println()

        println("----------------------")
        println("Set up obligations in a gridlock")
        println("----------------------")
        // Create obligation
        val fut1 = createObligation(bank2, bank1, SGD(5), 0).getOrThrow().tx.outputStates.single() as Obligation.State
        val fut2 = createObligation(bank3, bank1, SGD(6), 0).getOrThrow().tx.outputStates.single() as Obligation.State
        val fut3 = createObligation(bank2, bank1, SGD(10), 0).getOrThrow().tx.outputStates.single() as Obligation.State
        val fut4 = createObligation(bank1, bank2, SGD(25), 0).getOrThrow().tx.outputStates.single() as Obligation.State
        val fut5 = createObligation(bank2, bank3, SGD(7), 0).getOrThrow().tx.outputStates.single() as Obligation.State
        net.waitQuiescent()
        printCashBalances()
        printObligations(bank1)
        printObligations(bank2)
        printObligations(bank3)
        println()

        // Perform LSM.
        println("--------------------------")
        println("LSM Run #1")
        println("--------------------------")
        assertFailsWith<FlowException>("Deadlock!") {
            bank1.services.startFlow(StartLSMFlow()).resultFuture.getOrThrow() }
        net.waitQuiescent()
        printCashBalances()
        println()

        val balance1_run1 = bank1.database.transaction { bank1.services.getCashBalance(sgd) }
        val balance2_run1 = bank2.database.transaction { bank2.services.getCashBalance(sgd) }
        val balance3_run1 = bank3.database.transaction { bank3.services.getCashBalance(sgd) }
        assert(balance1_run1.quantity / 100 == 3L)
        assert(balance2_run1.quantity / 100 == 4L)
        assert(balance3_run1.quantity / 100 == 5L)



        println("----------------------")
        println("Reprioritise obligations in a gridlock")
        println("----------------------")
        val obligationToUpdate = fut3.linearId
        bank1.services.startFlow(UpdateObligationPriority(obligationToUpdate, OBLIGATION_PRIORITY.HIGH)).resultFuture.getOrThrow()
        net.waitQuiescent()
        printCashBalances()
        println()

        // Perform LSM.
        println("--------------------------")
        println("LSM Run #2")
        println("--------------------------")
        assertFailsWith<FlowException>("Deadlock!") {
            bank1.services.startFlow(StartLSMFlow()).resultFuture.getOrThrow() }
        net.waitQuiescent()
        printCashBalances()


        val balance1_run2 = bank1.database.transaction { bank1.services.getCashBalance(sgd) }
        val balance2_run2 = bank2.database.transaction { bank2.services.getCashBalance(sgd) }
        val balance3_run2 = bank3.database.transaction { bank3.services.getCashBalance(sgd) }
        assert(balance1_run2.quantity / 100 == 3L)
        assert(balance2_run2.quantity / 100 == 4L)
        assert(balance3_run2.quantity / 100 == 5L)
    }

    /**
     * 4 obligations (2 cycles with one common participant)
     * LSM runs: 1 runs
     * Expected: 4 obligations settled
     */
    @Test
    fun `Scenario 3_3`() {
        println("----------------------")
        println("Starting Scenario 3.3:")
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
        net.waitQuiescent()
        printCashBalances()
        printObligations(bank1)
        printObligations(bank2)
        printObligations(bank3)
        println()

        println("----------------------")
        println("Set up obligations in a gridlock")
        println("----------------------")
        // Create obligation
        val fut1 = createObligation(bank2, bank1, SGD(5), 0).getOrThrow().tx.outputStates.single() as Obligation.State
        val fut2 = createObligation(bank1, bank2, SGD(7), 0).getOrThrow().tx.outputStates.single() as Obligation.State
        val fut3 = createObligation(bank3, bank2, SGD(8), 0).getOrThrow().tx.outputStates.single() as Obligation.State
        val fut4 = createObligation(bank2, bank3, SGD(7), 0).getOrThrow().tx.outputStates.single() as Obligation.State
        net.waitQuiescent()
        printCashBalances()
        printObligations(bank1)
        printObligations(bank2)
        printObligations(bank3)
        println()

        // Perform LSM.
        println("--------------------------")
        println("LSM Run #1")
        println("--------------------------")
        bank2.services.startFlow(StartLSMFlow()).resultFuture.getOrThrow()
        net.waitQuiescent()
        printCashBalances()
        println()
        val balance1_run1 = bank1.database.transaction { bank1.services.getCashBalance(sgd) }
        val balance2_run1 = bank2.database.transaction { bank2.services.getCashBalance(sgd) }
        val balance3_run1 = bank3.database.transaction { bank3.services.getCashBalance(sgd) }
        assert(balance1_run1.quantity / 100 == 3L)
        assert(balance2_run1.quantity / 100 == 3L)
        assert(balance3_run1.quantity / 100 == 6L)
        printObligations(bank1)
        printObligations(bank2)
        printObligations(bank3)

        // Perform LSM.
        // TODO: For scenarios where 2 cycles are solvable, need to enhance LSM (planning & execute) to solve in one go instead of two
        println("--------------------------")
        println("LSM Run #2")
        println("--------------------------")
        bank2.services.startFlow(StartLSMFlow()).resultFuture.getOrThrow()
        net.waitQuiescent()
        printCashBalances()
        println()
        val balance1_run2 = bank1.database.transaction { bank1.services.getCashBalance(sgd) }
        val balance2_run2 = bank2.database.transaction { bank2.services.getCashBalance(sgd) }
        val balance3_run2 = bank3.database.transaction { bank3.services.getCashBalance(sgd) }

        assert(balance1_run2.quantity / 100 == 5L)
        assert(balance2_run2.quantity / 100 == 1L)
        assert(balance3_run2.quantity / 100 == 6L)
    }
}