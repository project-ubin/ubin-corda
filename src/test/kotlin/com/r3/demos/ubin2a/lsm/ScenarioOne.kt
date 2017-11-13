package com.r3.demos.ubin2a.lsm

import com.r3.demos.ubin2a.base.CENTRAL_PARTY_X500
import com.r3.demos.ubin2a.base.REGULATOR_PARTY_X500
import com.r3.demos.ubin2a.base.SGD
import com.r3.demos.ubin2a.base.TemporaryKeyManager
import com.r3.demos.ubin2a.detect.*
import com.r3.demos.ubin2a.execute.*
import com.r3.demos.ubin2a.account.DeadlockNotificationFlow
import com.r3.demos.ubin2a.account.DeadlockService
import com.r3.demos.ubin2a.account.StartLSMFlow
import com.r3.demos.ubin2a.obligation.IssueObligation
import com.r3.demos.ubin2a.obligation.Obligation
import com.r3.demos.ubin2a.obligation.PersistentObligationQueue
import com.r3.demos.ubin2a.obligation.SettleObligation
import com.r3.demos.ubin2a.plan.PlanFlow
import com.r3.demos.ubin2a.pledge.ApprovePledge
import com.r3.demos.ubin2a.ubin2aTestHelpers.createObligation
import com.r3.demos.ubin2a.ubin2aTestHelpers.printObligations
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

/**
 * Scenario One: 2 participants - all obligations are settled
 */

class ScenarioOne {
    lateinit var net: MockNetwork
    lateinit var bank1: StartedNode<MockNetwork.MockNode>
    lateinit var bank2: StartedNode<MockNetwork.MockNode>
    lateinit var bank3: StartedNode<MockNetwork.MockNode>
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
        println("Bank1: $bank1, bank2: $bank2, bank3: $bank3")
    }

    /**
     * 2 obligations, starting balance is 0 for both participants
     * LSM runs: 1 run
     * Expected: All obligation fully settled
     */
    @Test
    fun `Scenario 1_1`() {
        println("----------------------")
        println("Starting Scenario 1.1:")
        println("----------------------")
        val sgd = Currency.getInstance("SGD")
        printCashBalances()
        println()

        println("----------------------")
        println("Set up Starting Balance")
        println("----------------------")
        net.waitQuiescent()
        println()

        println("----------------------")
        println("Set up obligations in a gridlock")
        println("----------------------")
        // Create obligation
        val fut1 = createObligation(bank1, bank2, SGD(2),0).getOrThrow().tx.outputStates.single() as Obligation.State
        println("Bank1 owes 2 to Bank2")
        val fut2 = createObligation(bank2, bank1, SGD(2),0).getOrThrow().tx.outputStates.single() as Obligation.State
        net.waitQuiescent()
        printCashBalances()
        printObligations(bank2)
        printObligations(bank1)
        println()

        // Perform LSM.
        println("--------------------------")
        println("LSM Run #1")
        println("--------------------------")
//        bank1.services.startFlow(StartLSMFlow()).resultFuture.getOrThrow()


        val fut = bank1.services.startFlow(DetectFlow(sgd))
        net.waitQuiescent()
        val (obligations, limits) = fut.resultFuture.getOrThrow()
        println(obligations)
        println(limits)

        val (paymentsToMake, resultantObligations) = bank1.services.startFlow(PlanFlow(obligations, limits, sgd)).resultFuture.getOrThrow()
        net.waitQuiescent()
        println("Payments to make:")
        println(paymentsToMake)
        println("Resultant obligations:")
        println(resultantObligations)

        bank1.services.startFlow(ExecuteFlow(obligations, resultantObligations, paymentsToMake)).resultFuture.getOrThrow()
        net.waitQuiescent()
        printCashBalances()
        println()
        println(ExecuteDataStore.obligations)
        println(ExecuteDataStore.payments)


        val balance1_run1 = bank1.database.transaction { bank1.services.getCashBalance(sgd) }
        val balance2_run2 = bank2.database.transaction { bank2.services.getCashBalance(sgd) }
        assert(balance1_run1.quantity / 100 == 0L)
        assert(balance2_run2.quantity / 100 == 0L)
    }

    /**
     * 2 obligations
     * LSM runs: 1 run
     * Expected: All obligation fully settled
     */
    @Test
    fun `Scenario 1_2`() {
        println("----------------------")
        println("Starting Scenario 1.2:")
        println("----------------------")
        val sgd = Currency.getInstance("SGD")
        printCashBalances()
        println()

        println("----------------------")
        println("Set up Starting Balance")
        println("----------------------")
        centralBank.services.startFlow(ApprovePledge.Initiator(SGD(3), bank1.services.myInfo.chooseIdentity())).resultFuture.getOrThrow()
        centralBank.services.startFlow(ApprovePledge.Initiator(SGD(4), bank2.services.myInfo.chooseIdentity())).resultFuture.getOrThrow()
        net.waitQuiescent()
        printCashBalances()
        println()

        println("----------------------")
        println("Set up obligations in a gridlock")
        println("----------------------")
        // Create obligation
        val fut1 = createObligation(bank2, bank1, SGD(5),0).getOrThrow().tx.outputStates.single() as Obligation.State
        val fut2 = createObligation(bank1, bank2, SGD(6),0).getOrThrow().tx.outputStates.single() as Obligation.State
        net.waitQuiescent()
        printCashBalances()
        printObligations(bank1)
        printObligations(bank2)
        println()

        // Perform LSM.
        println("--------------------------")
        println("LSM Run #1")
        println("--------------------------")
        bank1.services.startFlow(StartLSMFlow()).resultFuture.getOrThrow()
        net.waitQuiescent()
        printCashBalances()
        println()

        val balance1_run1 = bank1.database.transaction { bank1.services.getCashBalance(sgd) }
        val balance2_run1 = bank2.database.transaction { bank2.services.getCashBalance(sgd) }
        assert(balance1_run1.quantity / 100 == 4L)
        assert(balance2_run1.quantity / 100 == 3L)
    }

    /**
     * 4 obligations
     * LSM runs: 2 runs
     * Expected: All obligation fully settled
     */
    @Test
    fun `Scenario 1_3`() {
        println("----------------------")
        println("Starting Scenario 1.3:")
        println("----------------------")
        val sgd = Currency.getInstance("SGD")
        printCashBalances()
        println()

        println("----------------------")
        println("Set up Starting Balance")
        println("----------------------")
        centralBank.services.startFlow(ApprovePledge.Initiator(SGD(5), bank1.services.myInfo.chooseIdentity())).resultFuture.getOrThrow()
        centralBank.services.startFlow(ApprovePledge.Initiator(SGD(6), bank2.services.myInfo.chooseIdentity())).resultFuture.getOrThrow()
        net.waitQuiescent()
        printCashBalances()
        println()

        println("----------------------")
        println("Set up obligations in a gridlock")
        println("----------------------")
        // Create obligation
        val fut1 = createObligation(bank2, bank1, SGD(8),0).getOrThrow().tx.outputStates.single() as Obligation.State
        val fut2 = createObligation(bank1, bank2, SGD(10),0).getOrThrow().tx.outputStates.single() as Obligation.State
        val fut3 = createObligation(bank1, bank2, SGD(20),0).getOrThrow().tx.outputStates.single() as Obligation.State
        val fut4 = createObligation(bank2, bank1, SGD(17),0).getOrThrow().tx.outputStates.single() as Obligation.State
        net.waitQuiescent()
        printCashBalances()
        printObligations(bank1)
        printObligations(bank2)
        println()

        // Perform LSM.
        println("--------------------------")
        println("LSM Run #1")
        println("--------------------------")
        bank1.services.startFlow(StartLSMFlow()).resultFuture.getOrThrow()
        net.waitQuiescent()
        printCashBalances()
        println()
        val balance1_run1 = bank1.database.transaction { bank1.services.getCashBalance(sgd) }
        val balance2_run1 = bank2.database.transaction { bank2.services.getCashBalance(sgd) }
        assert(balance1_run1.quantity / 100 == 7L)
        assert(balance2_run1.quantity / 100 == 4L)

        // Perform LSM.
        println("--------------------------")
        println("LSM Run #2")
        println("--------------------------")
        bank1.services.startFlow(StartLSMFlow()).resultFuture.getOrThrow()
        net.waitQuiescent()
        printCashBalances()
        println()

        val balance1_run2 = bank1.database.transaction { bank1.services.getCashBalance(sgd) }
        val balance2_run2 = bank2.database.transaction { bank2.services.getCashBalance(sgd) }
        assert(balance1_run2.quantity / 100 == 10L)
        assert(balance2_run2.quantity / 100 == 1L)
    }
}