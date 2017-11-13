package com.r3.demos.ubin2a.pledge

import com.google.common.util.concurrent.UncheckedExecutionException
import com.r3.demos.ubin2a.base.CENTRAL_PARTY_X500
import com.r3.demos.ubin2a.base.REGULATOR_PARTY_X500
import com.r3.demos.ubin2a.base.SGD
import com.r3.demos.ubin2a.cash.AcceptPayment
import com.r3.demos.ubin2a.obligation.IssueObligation
import com.r3.demos.ubin2a.obligation.Obligation
import com.r3.demos.ubin2a.obligation.PersistentObligationQueue
import com.r3.demos.ubin2a.ubin2aTestHelpers.allObligations
import com.r3.demos.ubin2a.ubin2aTestHelpers.createObligation
import com.r3.demos.ubin2a.ubin2aTestHelpers.printObligations
import net.corda.core.contracts.Amount
import net.corda.core.utilities.getOrThrow
import net.corda.finance.contracts.asset.Cash
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

class PledgeTests {
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
                "com.r3.demos.ubin2a.pledge",
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
        it.database.transaction {
            it.internals.installCordaService(PersistentObligationQueue::class.java)
        }
    }

    private fun buildNetwork() {
        val sgd = java.util.Currency.getInstance("SGD")
        centralBank.services.startFlow(ApprovePledge.Initiator(Amount(100000, sgd), bank1.services.myInfo.chooseIdentity())).resultFuture.getOrThrow()
        centralBank.services.startFlow(ApprovePledge.Initiator(Amount(100000, sgd), bank2.services.myInfo.chooseIdentity())).resultFuture.getOrThrow()
        centralBank.services.startFlow(ApprovePledge.Initiator(Amount(100000, sgd), bank3.services.myInfo.chooseIdentity())).resultFuture.getOrThrow()
    }

    private fun printCashBalances() {
        val bank1 = bank1.database.transaction { bank1.services.getCashBalance(sgd) }
        val bank2 = bank2.database.transaction { bank2.services.getCashBalance(sgd) }
        val bank3 = bank3.database.transaction { bank3.services.getCashBalance(sgd) }
        println("Bank1: $bank1, bank2: $bank2, bank3: $bank3")
    }

    @Test
    fun `approve Pledge amount`() {
        println("----------------------")
        println("Test Approve pledge amount:")
        println("----------------------")
        val sgd = SGD
        printCashBalances()
        println()

        // Approve pledge amount to counter party
        val centralBalance = centralBank.database.transaction { centralBank.services.getCashBalance(sgd) }
        println("centralBalance "+centralBalance)
        assert(centralBalance.quantity/100==0L)
        println("Central Bank approve pledge amount of 500 to Bank1")
        val flow = ApprovePledge.Initiator(Amount(50000, sgd), bank1.info.chooseIdentity(), true)
        val stx = centralBank.services.startFlow(flow).resultFuture.getOrThrow()
        println(stx.tx.toString())
        net.waitQuiescent()
        printCashBalances()
        println()

        val state = stx.tx.outputsOfType<Cash.State>().first()
        println("state "+state.owner)
        val balance1 = bank1.database.transaction { bank1.services.getCashBalance(sgd) }
        val centralBalance2 = centralBank.database.transaction { centralBank.services.getCashBalance(sgd) }

        // Confirm bank1 receives the pledged amount
        assert(balance1.quantity / 100 == 500L)
        assert(centralBalance2.quantity / 100 == 0L)

        // Confirm bank3 cannot infer the owner of the pledged cash
        assertFailsWith<UncheckedExecutionException>("No transaction in context.") {
            bank3.services.identityService.requireWellKnownPartyFromAnonymous(state.owner)
        }
    }

    @Test
    fun `approve Pledge can bypass queue mechanism`() {
        println("----------------------")
        println("Test Approve pledge amount:")
        println("----------------------")
        val sgd = SGD
        printCashBalances()
        println()

        println("Central Bank issues obligation of 5000 to bank1")
        val fut1 = createObligation(bank1, centralBank, Amount(500000, sgd), 1)
        val tx = fut1.getOrThrow()
        val obligationState = tx.tx.outputStates.single() as Obligation.State
        println(obligationState.toString())
        net.waitQuiescent()
        printObligations(bank1)

        assert(bank1.database.transaction { bank1.services.getCashBalance(sgd) }.quantity/100 == 0L)
        // Confirm outgoing queue size increased for central bank
        val allObligations = allObligations(centralBank)
        println("Obligations from Bank1: " + allObligations)
        println("Count of obligation from Bank1: " + allObligations.size)
        assert(allObligations.size == 1)

        // Approve pledge amount to counter party
        val centralBalance = centralBank.database.transaction { centralBank.services.getCashBalance(sgd) }
        println("centralBalance "+centralBalance)
        assert(centralBalance.quantity/100==0L)
        println("Central Bank approve pledge amount of 500 to Bank2")
        val flow = ApprovePledge.Initiator(Amount(50000, sgd), bank1.info.chooseIdentity(), true)
        val stx = centralBank.services.startFlow(flow).resultFuture.getOrThrow()
        println(stx.tx.toString())
        net.waitQuiescent()
        printCashBalances()
        println()

        val state = stx.tx.outputsOfType<Cash.State>().first()
        println("state "+state.owner)
        val balance1 = bank1.database.transaction { bank1.services.getCashBalance(sgd) }
        val centralBalance2 = centralBank.database.transaction { centralBank.services.getCashBalance(sgd) }

        // Confirm bank1 receives the pledged amount
        assert(balance1.quantity / 100 == 500L)
        assert(centralBalance2.quantity / 100 == 0L)

        // Confirm bank3 cannot infer the owner of the pledged cash
        assertFailsWith<UncheckedExecutionException>("No transaction in context.") {
            bank3.services.identityService.requireWellKnownPartyFromAnonymous(state.owner)
        }
    }
}