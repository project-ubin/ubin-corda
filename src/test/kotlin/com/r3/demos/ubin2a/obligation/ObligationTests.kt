package com.r3.demos.ubin2a.obligation

import com.r3.demos.ubin2a.base.*
import com.r3.demos.ubin2a.cash.AcceptPayment
import com.r3.demos.ubin2a.pledge.ApprovePledge
import com.r3.demos.ubin2a.cash.Pay
import com.r3.demos.ubin2a.ubin2aTestHelpers.allObligations
import com.r3.demos.ubin2a.ubin2aTestHelpers.createObligation
import com.r3.demos.ubin2a.ubin2aTestHelpers.printObligations
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
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

class ObligationTests {

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
        it.registerInitiatedFlow(CancelObligation.Responder::class.java)
        it.registerInitiatedFlow(SettleObligation.Responder::class.java)
        it.database.transaction {
            it.internals.installCordaService(PersistentObligationQueue::class.java)
        }
    }

    private fun buildNetwork() {
        val sgd = Currency.getInstance("SGD")
        centralBank.services.startFlow(ApprovePledge.Initiator(Amount(100000, sgd), bank1.services.myInfo.chooseIdentity())).resultFuture.getOrThrow()
        centralBank.services.startFlow(ApprovePledge.Initiator(Amount(100000, sgd), bank2.services.myInfo.chooseIdentity())).resultFuture.getOrThrow()
        centralBank.services.startFlow(ApprovePledge.Initiator(Amount(200000, sgd), bank3.services.myInfo.chooseIdentity())).resultFuture.getOrThrow()
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

    @Test
    fun `can Issue Multiple Obligations And Set Priority`() {
        // Create obligation between 1 and 2.
        println("Bank1 borrows 500 from Bank2")
        val fut1 = createObligation(bank2, bank1, Amount(50000, sgd), 1).getOrThrow()
        val state1 = fut1.tx.outputStates.single() as Obligation.State
        println(fut1.toString())
        val linearId1 = state1.linearId
        net.waitQuiescent()
        printCashBalances()
        println()

        val fut2 = createObligation(bank2, bank1, Amount(50000, sgd), 0).getOrThrow()
        val state2 = fut2.tx.outputStates.single() as Obligation.State
        println(fut2.toString())

        // Query outgoing queue
        val linearId2 = state2.linearId
        val outgoingObligations = bank1.services.startFlow(GetQueue.OutgoingUnconsumed()).resultFuture.getOrThrow()
        net.waitQuiescent()
        printCashBalances()
        println()

        // Confirm balance did not change
        val balance1 = bank1.database.transaction { bank1.services.getCashBalance(sgd) }
        val balance2 = bank2.database.transaction { bank2.services.getCashBalance(sgd) }
        assert(balance1.quantity / 100 == 1000L)
        assert(balance2.quantity / 100 == 1000L)

        val allObligations = allObligations(bank1)

        // Confirm outgoing queue size increased for bank 1
        println("Obligations from Bank1: " + allObligations)
        println("Count of obligation from Bank1: " + outgoingObligations.size)
        assert(outgoingObligations.size == 2)

        // Bank 1 has obligation amount of 1000
        var sum = 0L
        outgoingObligations.forEach {
            sum+=it.transactionAmount.toPenny()
        }
        println("The total sum of obligations amount is " + sum)
        assert(sum / 100 == 1000L)

        // Bank 1 has the correct priority on the obligation
        assert(outgoingObligations.first().priority == 1)
        assert(outgoingObligations.first().linearId == state1.linearId.toString())

        assert(outgoingObligations.last().priority == 0)
        assert(outgoingObligations.last().linearId == state2.linearId.toString())

    }

    @Test
    fun `can View Multiple Outgoing Obligations in FIFO Order`() {
        val currency = SGD

        // Create obligation between 1 and 2.
        println("Bank 1 issues obligation of 100 to Bank2")
        val fut1 = createObligation(bank2, bank1, Amount(100000, currency), 1)
        val tx = fut1.getOrThrow()
        val state = tx.tx.outputStates.single() as Obligation.State
        println(state.toString())

        println("Bank 1 issues obligation of 100 to Bank2")
        val fut2 = createObligation(bank2, bank1, Amount(200000, currency), 0)
        val tx2 = fut2.getOrThrow()
        val state2 = tx2.tx.outputStates.single() as Obligation.State
        println("Retrieving outgoing obligations from Queue of Bank1")


        println("Bank 1 issues obligation of 100 to Bank2")
        val fut3 = createObligation(bank2, bank1, Amount(300000, currency), 0)
        val tx3 = fut3.getOrThrow()
        val state3 = tx3.tx.outputStates.single() as Obligation.State
        println("Retrieving outgoing obligations from Queue of Bank1")

        val outgoingQueue = bank1.services.startFlow(GetQueue.OutgoingUnconsumed()).resultFuture.getOrThrow()
        net.waitQuiescent()
        printObligations(bank1)
        val allObligations = allObligations(bank1)

        // Confirm bank 1 has outgoing obligation of size 3
        val outgoingCount = outgoingQueue.size
        println("Count of outgoing obligations by Bank1 is " + outgoingCount)
        assert(outgoingCount == 3)

        val dateList = Array<Instant>(3, { Instant.now() })

        val iterator = outgoingQueue.iterator()
        val i = 0
        while (iterator.hasNext()) {
            dateList.set(i, iterator.next().requestedDate.toDate().toInstant())
            println("Queue Date " + dateList[i])
        }

        // Confirm Queue is in FIFO
        println("Date Compare " + dateList[0].compareTo(dateList[1]))
        println("Date Compare " + dateList[1].compareTo(dateList[2]))
        assert(dateList[0] <= (dateList[1]))
        assert(dateList[1] <= (dateList[2]))

        // Confirm Bank 1 has the correct priority on the obligation
        assert(outgoingQueue.first().priority == 1)
        assert(outgoingQueue.first().linearId == state.linearId.toString())

        assert(outgoingQueue.get(1).priority == 0)
        assert(outgoingQueue.get(1).linearId == state2.linearId.toString())

        assert(outgoingQueue.last().priority == 0)
        assert(outgoingQueue.last().linearId == state3.linearId.toString())

    }

    @Test
    fun `can View Incoming Queue in FIFO`() {
        val currency = SGD

        // Create obligation between 1 and 2.
        println("Bank1 issues obligation of 5000 to bank2")
        val fut1 = createObligation(bank2, bank1, Amount(500000, currency), 1)
        val tx = fut1.getOrThrow()
        val state = tx.tx.outputStates.single() as Obligation.State
        println(state.toString())
        net.waitQuiescent()
        printObligations(bank1)

        println("Bank1 issues obligation of 5000 to bank2")
        val fut2 = createObligation(bank2, bank1, Amount(500000, currency), 1)
        val tx2 = fut2.getOrThrow()
        val state2 = tx2.tx.outputStates.single() as Obligation.State
        println(state.toString())
        net.waitQuiescent()
        printObligations(bank1)

        //Confirm bank1 should not have incoming obligations
        println("Bank1 retrieving incoming obligations from Vault")
        val obligations = bank1.services.startFlow(GetQueue.Incoming()).resultFuture.getOrThrow()
        println("Bank1 incoming obligations: " + obligations)
        println("Bank1 incoming obligation count: " + obligations.size)
        assert(obligations.isEmpty())

        // Confirm bank 2 incoming increased by 2
        println("Bank2 retrieving incoming obligations from Vault")
        val obligations2 = bank2.services.startFlow(GetQueue.Incoming()).resultFuture.getOrThrow()
        println("Bank2 incoming obligations: " + obligations2)
        println("Bank2 incoming obligation count: " + obligations2.size)
        assert(obligations2.size == 2)

        val dateList = Array<Instant>(2, { Instant.now()})

        val iterator = obligations2.iterator()
        val i = 0
        while (iterator.hasNext()) {
            dateList.set(i, iterator.next().requestedDate.toDate().toInstant())
            println("Queue Date " + dateList[i])
        }

        // Queue is in FIFO
        println("Date Compare " + dateList[0].compareTo(dateList[1]))
        assert(dateList[0] <= (dateList[1]))
    }

    @Test
    fun `can Update priority of existing obligations`() {
        val currency = SGD
        // Create obligation between 1 and 2.
        println("Bank 1 issues obligation of 1000 to Bank2")
        val fut1 = createObligation(bank2, bank1, Amount(100000, currency), 1)
        val tx = fut1.getOrThrow()
        val state = tx.tx.outputStates.single() as Obligation.State
        println(state.toString())

        println("Bank 1 issues obligation of 2000 to Bank2")
        val fut2 = createObligation(bank2, bank1, Amount(200000, currency), 0)
        val tx2 = fut2.getOrThrow()
        val state2 = tx2.tx.outputStates.single() as Obligation.State
        println("Retrieving outgoing obligations from Queue of Bank1")

        println("Bank 1 issues obligation of 3000 to Bank2")
        val fut3 = createObligation(bank2, bank1, Amount(300000, currency), 0)
        val tx3 = fut3.getOrThrow()
        val state3 = tx3.tx.outputStates.single() as Obligation.State
        println("Retrieving outgoing obligations from Queue of Bank1")

        val outgoingQueue = bank1.services.startFlow(GetQueue.OutgoingUnconsumed()).resultFuture.getOrThrow()
        net.waitQuiescent()
        printObligations(bank1)
        val allObligations = allObligations(bank1)

        // Check queue size
        val outgoingCount = outgoingQueue.size
        println("Count of outgoing obligations by Bank1 is " + outgoingCount)
        assert(outgoingCount == 3)

        // Update the obligation #3 priority to high
        bank1.services.startFlow(UpdateObligationPriority(state3.linearId, OBLIGATION_PRIORITY.HIGH)).resultFuture.getOrThrow()
        val queueItem = bank1.services.startFlow(GetQueue.OutgoingById(state3.linearId)).resultFuture.getOrThrow()
        println("OBLIGATION_STATUS.ACTIVE.ordinal "+ queueItem.first().status)
        assert(queueItem.first().status == OBLIGATION_STATUS.ACTIVE.ordinal)
        println("queueItem.first().priority "+ queueItem.first().priority)
        assert(queueItem.first().priority == OBLIGATION_PRIORITY.HIGH.ordinal)

        // Try to send another payment transfer of high priority
        bank1.services.startFlow(Pay(bank2.info.chooseIdentity(), SGD(1000L), OBLIGATION_PRIORITY.HIGH.ordinal)).resultFuture.getOrThrow()
        val outgoingQueue2 = bank1.services.startFlow(GetQueue.OutgoingUnconsumed()).resultFuture.getOrThrow()
        val balance1 = bank1.database.transaction { bank1.services.getCashBalance(sgd) }
        net.waitQuiescent()

        // Check queue size
        // Queue should increase because #3 high priority obligation is preventing new transfer
        val outgoingCount2 = outgoingQueue2.size
        println("Count of outgoing obligations by Bank1 is " + outgoingCount2)
        println("balance1.quantity "+balance1.quantity)
        assert(outgoingCount2 == 4)
        assert(balance1.quantity/100==1000L)
    }

    @Test
    fun `borrower Can Cancel Obligation`() {
        val currency = SGD
        // Create obligation between 1 and 2.
        println("Bank1 issues obligation of 5000 to bank2")
        val fut1 = createObligation(bank2, bank1, Amount(500000, currency), 1)
        val tx = fut1.getOrThrow()
        val state = tx.tx.outputStates.single() as Obligation.State
        println(state.toString())
        net.waitQuiescent()
        printObligations(bank1)

        val cancelResult = cancelObligation(bank1, state.linearId)
        val cancelTx = cancelResult.getOrThrow()

        val queryResult = bank1.services.startFlow(GetQueue.OutgoingById(state.linearId)).resultFuture.getOrThrow()
        assert(queryResult.first().status == OBLIGATION_STATUS.CANCELLED.ordinal)
    }

    @Test
    fun `lender Cannot Cancel Obligation`() {
        val currency = SGD
        // Create obligation between 1 and 2.
        println("Bank1 issues obligation of 5000 to bank2")
        val fut1 = createObligation(bank2, bank1, Amount(500000, currency), 1)
        val tx = fut1.getOrThrow()
        val state = tx.tx.outputStates.single() as Obligation.State
        println(state.toString())
        net.waitQuiescent()
        printObligations(bank1)

        // Lender cannot initiate cancellation of obligation
        assertFailsWith<IllegalArgumentException>("Obligation Cancellation flow must be initiated by the borrower.") {
            val stx = cancelObligation(bank2, state.linearId).getOrThrow()
        }
    }

    @Test
    fun `can Update obligation to on hold`() {
        val currency = SGD
        // Create obligation between 1 and 2.
        println("Bank1 issues obligation of 5000 to bank2")
        val fut1 = createObligation(bank2, bank1, Amount(500000, currency), 1)
        val tx = fut1.getOrThrow()
        val state = tx.tx.outputStates.single() as Obligation.State
        println(state.toString())
        net.waitQuiescent()
        printObligations(bank1)


        val updateResult = updateObligation(bank1, state.linearId, OBLIGATION_STATUS.HOLD)
        val updateTx = updateResult.getOrThrow()

        assert(updateTx)

        val queryResult = bank1.services.startFlow(GetQueue.OutgoingById(state.linearId)).resultFuture.getOrThrow()
        assert(queryResult.first().status == OBLIGATION_STATUS.HOLD.ordinal)

    }

    @Test
    fun `can Update obligation to active`() {
        val currency = SGD
        // Create obligation between 1 and 2.
        println("Bank1 issues obligation of 5000 to bank2")
        val fut1 = createObligation(bank2, bank1, Amount(500000, currency), 1)
        val tx = fut1.getOrThrow()
        val state = tx.tx.outputStates.single() as Obligation.State
        println(state.toString())
        net.waitQuiescent()
        printObligations(bank1)


        val updateResult = updateObligation(bank1, state.linearId, OBLIGATION_STATUS.ACTIVE)
        val updateTx = updateResult.getOrThrow()

        assert(updateTx)

        val queryResult = bank1.services.startFlow(GetQueue.OutgoingById(state.linearId)).resultFuture.getOrThrow()
        assert(queryResult.first().status == OBLIGATION_STATUS.ACTIVE.ordinal)

    }

    @Test
    fun `can Settle Obligations`() {
        centralBank.services.startFlow(ApprovePledge.Initiator(1000.SGD, bank1.info.chooseIdentity())).resultFuture.getOrThrow()
        printCashBalances()
        val flowResult = createObligation(bank2, bank1, 200.SGD, 1, anonymous = true).getOrThrow()
        val obligation = flowResult.tx.outputs.single().data as Obligation.State
        val flow = SettleObligation.Initiator(obligation.linearId)
        val settleResult = bank1.services.startFlow(flow).resultFuture.getOrThrow()
        println(settleResult.tx)
        val settled = bank1.services.startFlow(GetQueue.OutgoingWithStatus(OBLIGATION_STATUS.SETTLED.ordinal)).resultFuture.getOrThrow().filter {
            it.linearId == obligation.linearId.toString()
        }

        // Confirm Obligation in queue updated to settled
        assert(settled.isNotEmpty())

        // Confirm obligation is settled.
        bank1.database.transaction {
            val ledgerTx = settleResult.tx.toLedgerTransaction(bank1.services)
            val ledgerState = ledgerTx.filterInputs<Obligation.State> { true }.single()
            val stateLinearId = ledgerState.linearId
            val QueryCriteria = QueryCriteria.LinearStateQueryCriteria(uuid = listOf(UUID.fromString(stateLinearId.toString())))
            val obligationStates = bank1.services.vaultService.queryBy<Obligation.State>(QueryCriteria).states
            assert(obligationStates.isEmpty())
        }
    }

}


