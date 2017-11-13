package com.r3.demos.ubin2a.cash

import com.google.common.util.concurrent.UncheckedExecutionException
import com.r3.demos.ubin2a.account.BalanceByBanksFlow
import com.r3.demos.ubin2a.base.*
import com.r3.demos.ubin2a.obligation.GetQueue
import com.r3.demos.ubin2a.obligation.IssueObligation
import com.r3.demos.ubin2a.obligation.Obligation
import com.r3.demos.ubin2a.obligation.PersistentObligationQueue
import com.r3.demos.ubin2a.pledge.ApprovePledge
import com.r3.demos.ubin2a.ubin2aTestHelpers.allObligations
import net.corda.core.contracts.Amount
import net.corda.core.flows.FlowException
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
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

class CashTests {
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
                "com.r3.demos.ubin2a.pledge",
                "com.r3.demos.ubin2a.account"
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
        buildNetwork()
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
        it.registerInitiatedFlow(BalanceByBanksFlow.Responder::class.java)
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

    private fun printObligationBalances(bank: StartedNode<MockNetwork.MockNode>) {
        bank.database.transaction {
            val queryCriteria = QueryCriteria.LinearStateQueryCriteria()
            val obligations = bank.services.vaultService.queryBy<Obligation.State>(queryCriteria).states.map {
                it.state.data
            }
            obligations.forEach { println("${it.borrower} owes ${it.lender} ${it.amount}") }
        }
    }

    /**
     * Any party can infer who the owner of the cash states despite not involved in the transaction
     */
    @Test
    fun `Send cash to known counterparty`() {
        println("----------------------")
        println("Test Send cash to known counterparty:")
        println("----------------------")
        val sgd = SGD
        printCashBalances()
        println()

        // Send money to counter party
        println("Bank1 sends 300 to Bank2")
        val flow = Pay(bank2.info.chooseIdentity(), Amount(30000, sgd), OBLIGATION_PRIORITY.NORMAL.ordinal, false)
        val stx = bank1.services.startFlow(flow).resultFuture.getOrThrow()
        println(stx.tx.toString())
        net.waitQuiescent()
        printCashBalances()
        println()

        val balance1 = bank1.database.transaction { bank1.services.getCashBalance(sgd) }
        val balance2 = bank2.database.transaction { bank2.services.getCashBalance(sgd) }

        // Confirm the banks' balance reflected the change of the funds transfer
        assert(balance1.quantity / 100 == 700L)
        assert(balance2.quantity / 100 == 1300L)
        stx.tx.commands.forEach {println("Signers: " + it.signers)}
        val state = stx.tx.outputsOfType<Cash.State>().first()
        val bank1Peeks = bank1.services.identityService.partyFromKey(state.owner.owningKey)?: 0
        val bank2Peeks = bank2.services.identityService.partyFromKey(state.owner.owningKey)?: 0
        val bank3Peeks = bank3.services.identityService.partyFromKey(state.owner.owningKey)?: 0
        println("bank1Peeks " +  bank1Peeks)
        println("bank2Peeks " + bank2Peeks)
        println("bank3Peeks " + bank3Peeks)

        // All banks can infer who the owner of the cash is
        assert(bank1Peeks != 0)
        assert( bank2Peeks != 0)
        assert(bank3Peeks != 0)
    }

    /**
     * Only parties that were involved in the transaction
     * can infer who the owner of the cash states is
     */
    @Test
    fun `Send cash to anonymous counterparty`() {
        println("----------------------")
        println("Test Send cash to anonymous counterparty:")
        println("----------------------")
        val sgd = SGD
        printCashBalances()
        println()

        // Send money to counter party
        println("Bank1 sends 300 to Bank2")
        val flow = Pay(bank2.info.chooseIdentity(), Amount(30000, sgd), OBLIGATION_PRIORITY.NORMAL.ordinal)
        val stx = bank1.services.startFlow(flow).resultFuture.getOrThrow()
        println(stx.tx.toString())
        net.waitQuiescent()
        printCashBalances()
        println()

        val balance1 = bank1.database.transaction { bank1.services.getCashBalance(sgd) }
        val balance2 = bank2.database.transaction { bank2.services.getCashBalance(sgd) }
        assert(balance1.quantity / 100 == 700L)
        assert(balance2.quantity / 100 == 1300L)
        stx.tx.commands.forEach {println("Signers: " + it.signers)}
        val state = stx.tx.outputsOfType<Cash.State>().first()

        // Bank1 and Bank2 can infer who the owner of the states
        assert(bank1.services.identityService.wellKnownPartyFromAnonymous(state.owner) != null)
        assert(bank2.services.identityService.wellKnownPartyFromAnonymous(state.owner) != null)

        // Bank3 cannot infer the owner of the state is
        assertFailsWith<UncheckedExecutionException>("No transaction in context.") {
            bank3.services.identityService.requireWellKnownPartyFromAnonymous(state.owner)
        }
    }


    @Test
    fun `Automatically issue obligation when insufficient balance`() {
        println("----------------------")
        println("Test Automatically issue obligation when insufficient balance:")
        println("----------------------")
        val sgd = java.util.Currency.getInstance("SGD")
        printCashBalances()
        println()

        // Send money to counter party
        println("Bank1 sends 2000 to Bank2")
        val flow = Pay(bank2.info.chooseIdentity(), Amount(200000, sgd), OBLIGATION_PRIORITY.NORMAL.ordinal)
        val stx = bank1.services.startFlow(flow).resultFuture.getOrThrow()
        println(stx.toString())
        net.waitQuiescent()
        printCashBalances()
        println()

        val balance1 = bank1.database.transaction { bank1.services.getCashBalance(sgd) }
        val balance2 = bank2.database.transaction { bank2.services.getCashBalance(sgd) }

        // Confirm that balance should not change after transfer
        assert(balance1.quantity / 100 == 1000L)
        assert(balance2.quantity / 100 == 1000L)
        val obligations = allObligations(bank1)

        // Querying bank1 outgoing queue of obligations
        val outgoingQueue = bank1.services.startFlow(GetQueue.OutgoingUnconsumed()).resultFuture.getOrThrow()
        val incomingQueue = bank2.services.startFlow(GetQueue.Incoming()).resultFuture.getOrThrow()
        net.waitQuiescent()

        // Confirm outgoing queue size increased by 1
        println("Obligations from Bank1: " + obligations)
        println("Count of outgoing obligations from Bank1: " + outgoingQueue.size)
        assert(outgoingQueue.size == 1)

        // Confirm incoming queue size increased by 1
        println("Count of incoming obligations of Bank2: " + incomingQueue.size)
        assert(incomingQueue.size == 1)

    }

    @Test
    fun `Automatically issue obligation when higher or equal priorities in queue`() {
        println("----------------------")
        println("Test Automatically issue obligation when higher or equal priorities in queue:")
        println("----------------------")
        val sgd = java.util.Currency.getInstance("SGD")
        printCashBalances()
        println()

        // Send money to counter party
        println("Bank1 sends 2000 to Bank2")
        val flow = Pay(bank2.info.chooseIdentity(), Amount(200000, sgd), OBLIGATION_PRIORITY.NORMAL.ordinal)
        val stx = bank1.services.startFlow(flow).resultFuture.getOrThrow()
        println(stx.toString())

        // Querying bank1 outgoing queue of obligations
        val outgoingQueue = bank1.services.startFlow(GetQueue.OutgoingUnconsumed()).resultFuture.getOrThrow()
        val incomingQueue = bank2.services.startFlow(GetQueue.Incoming()).resultFuture.getOrThrow()
        net.waitQuiescent()
        printCashBalances()
        println()

        // Confirm that balance should not change after transfer
        val balance1 = bank1.database.transaction { bank1.services.getCashBalance(sgd) }
        val balance2 = bank2.database.transaction { bank2.services.getCashBalance(sgd) }
        assert(balance1.quantity / 100 == 1000L)
        assert(balance2.quantity / 100 == 1000L)
        val obligations = allObligations(bank1)

        // Confirm outgoing queue size increased by 1
        println("Obligations from Bank1: " + obligations)
        println("Count of outgoing obligations from Bank1: " + outgoingQueue.size)
        assert(outgoingQueue.size == 1)

        // Confirm incoming queue size increased by 1
        println("Count of incoming obligations of Bank2: " + incomingQueue.size)
        assert(incomingQueue.size == 1)

        // Send money to counter party
        println("Bank1 sends 500 to Bank2")
        val flow2 = Pay(bank2.info.chooseIdentity(), Amount(50000, sgd), OBLIGATION_PRIORITY.NORMAL.ordinal)
        val stx2 = bank1.services.startFlow(flow2).resultFuture.getOrThrow()
        println(stx2.toString())

        // Querying bank1 outgoing queue of obligations
        val outgoingQueue2 = bank1.services.startFlow(GetQueue.OutgoingUnconsumed()).resultFuture.getOrThrow()
        val incomingQueue2 = bank2.services.startFlow(GetQueue.Incoming()).resultFuture.getOrThrow()
        net.waitQuiescent()
        printCashBalances()
        println()

        // Confirm balances should not change
        val balance3 = bank1.database.transaction { bank1.services.getCashBalance(sgd) }
        val balance4 = bank2.database.transaction { bank2.services.getCashBalance(sgd) }
        assert(balance3.quantity / 100 == 1000L)
        assert(balance4.quantity / 100 == 1000L)
        val obligations2 = allObligations(bank1)

        // Confirm outgoing queue size of bank1 increased by 1 more
        println("Obligations from Bank1: " + obligations2)
        println("Count of outgoing obligations from Bank1: " + outgoingQueue2.size)
        assert(outgoingQueue2.size == 2)

        // Confirm incoming queue size of bank2 increased by 1 more
        println("Count of incoming obligations of Bank2: " + incomingQueue2.size)
        assert(incomingQueue2.size == 2)
    }

}