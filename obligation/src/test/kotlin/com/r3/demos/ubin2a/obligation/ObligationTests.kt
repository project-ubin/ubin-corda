package com.r3.demos.ubin2a.obligation

import com.r3.demos.ubin2a.base.OBLIGATION_STATUS
import com.r3.demos.ubin2a.base.SGD
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.flows.CashIssueFlow
import net.corda.node.internal.StartedNode
import net.corda.testing.chooseIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.setCordappPackages
import net.corda.testing.unsetCordappPackages
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

class ObligationTests {
    lateinit var net: MockNetwork
    lateinit var a: StartedNode<MockNetwork.MockNode>
    lateinit var b: StartedNode<MockNetwork.MockNode>
    lateinit var c: StartedNode<MockNetwork.MockNode>

    @Before
    fun setup() {
        setCordappPackages("com.r3.demos.ubin2a.obligation", "net.corda.finance")
        net = MockNetwork(threadPerNode = true)
        val nodes = net.createSomeNodes(3)
        a = nodes.partyNodes[0]
        b = nodes.partyNodes[1]
        c = nodes.partyNodes[2]
        nodes.partyNodes.forEach { node ->
            node.internals.registerInitiatedFlow(IssueObligation.Responder::class.java)
            node.internals.registerInitiatedFlow(SettleObligation.Responder::class.java)
            node.database.transaction {
                node.internals.installCordaService(PersistentObligationQueue::class.java)
            }
        }
    }

    @After
    fun tearDown() {
        net.stopNodes()
        unsetCordappPackages()
    }

    private fun createObligation(borrower: StartedNode<MockNetwork.MockNode>,
                                 lender: StartedNode<MockNetwork.MockNode>,
                                 amount: Amount<Currency>,
                                 priority: Int = 0,
                                 anonymous: Boolean): CordaFuture<SignedTransaction> {
        val flow = IssueObligation.Initiator(amount, lender.info.chooseIdentity(), priority, anonymous = anonymous)
        return borrower.services.startFlow(flow).resultFuture
    }

    @Test
    fun issueObligation() {
        val tx1 = createObligation(a, b, 100.SGD, 0, anonymous = false).getOrThrow()
        net.waitQuiescent()
        val aObligation = a.services.loadState(tx1.tx.outRef<Obligation.State>(0).ref)
        println(aObligation)
        val bObligation = b.services.loadState(tx1.tx.outRef<Obligation.State>(0).ref)
        println(bObligation)
        assertEquals(aObligation, bObligation)
    }

    @Test
    fun issueAnonymousObligation() {
        val tx1 = createObligation(a, c, 20.SGD, 1, anonymous = true).getOrThrow()
        net.waitQuiescent()
        val obligation = tx1.tx.outputStates.single() as Obligation.State
        val aObligationStateA: Obligation.State = a.database.transaction {
            a.services.loadState(tx1.tx.outRef<Obligation.State>(0).ref).data as Obligation.State
        }
        val aObligationStateB = c.database.transaction {
            c.services.loadState(tx1.tx.outRef<Obligation.State>(0).ref).data as Obligation.State
        }
        println(aObligationStateA)
        println(aObligationStateB)
        assertEquals(aObligationStateA, aObligationStateB)
        val maybePartyAlookedUpByA = a.services.identityService.requireWellKnownPartyFromAnonymous(obligation.borrower)
        val maybePartyAlookedUpByC = c.services.identityService.requireWellKnownPartyFromAnonymous(obligation.borrower)
        assertEquals(a.services.myInfo.chooseIdentity(), maybePartyAlookedUpByA)
        assertEquals(a.services.myInfo.chooseIdentity(), maybePartyAlookedUpByC)
        val maybePartyClookedUpByA = a.services.identityService.requireWellKnownPartyFromAnonymous(obligation.lender)
        val maybePartyClookedUpByC = c.services.identityService.requireWellKnownPartyFromAnonymous(obligation.lender)
        assertEquals(c.services.myInfo.chooseIdentity(), maybePartyClookedUpByA)
        assertEquals(c.services.myInfo.chooseIdentity(), maybePartyClookedUpByC)
        println(tx1.tx)
        println("A in Company A: ${a.services.myInfo.chooseIdentity()}, $maybePartyAlookedUpByA")
        println("A in Company C: ${a.services.myInfo.chooseIdentity()}, $maybePartyAlookedUpByC")
        println("C in Company A: ${c.services.myInfo.chooseIdentity()}, $maybePartyClookedUpByA")
        println("C in Company C: ${c.services.myInfo.chooseIdentity()}, $maybePartyClookedUpByA")
    }

    @Test
    fun addObligationToQueue() {
        val tx1 = createObligation(a, c, 20.SGD, 1, anonymous = false).getOrThrow()
        net.waitQuiescent()
        val customVaultQueryService = a.services.cordaService(PersistentObligationQueue::class.java)
        val linearId1 = tx1.tx.outRefsOfType<Obligation.State>().single().state.data.linearId
        a.database.transaction {
            val queuedObligation1 = customVaultQueryService.getOutgoingObligationFromLinearId(linearId1)[0]
            assertEquals(UniqueIdentifier.fromString(queuedObligation1.linearId), linearId1)
        }
    }

    @Test
    fun settleAnonymousObligation() {
        val notary = a.services.networkMapCache.notaryIdentities.firstOrNull() ?: throw IllegalStateException("No available notary.")
        a.services.startFlow(CashIssueFlow(2000.SGD, OpaqueBytes.of(0), notary)).resultFuture.getOrThrow()
        val flowResult = createObligation(a, b, 200.SGD, 1, anonymous = true).getOrThrow()
        val obligation = flowResult.tx.outputs.single().data as Obligation.State
        val flow = SettleObligation.Initiator(obligation.linearId)
        val settleResult = a.services.startFlow(flow).resultFuture.getOrThrow()
        println(settleResult.tx)
        val settled = a.services.startFlow(GetQueue.OutgoingWithStatus(OBLIGATION_STATUS.SETTLED.ordinal)).resultFuture.getOrThrow().filter {
            println(it)
            it.linearId == obligation.linearId.toString()
        }
        assert(settled.isNotEmpty())
    }

    @Test
    fun cancelAnonymousObligation() {
        val flowResult = createObligation(a, b, 200.SGD, 1, anonymous = true).getOrThrow()
        val obligation = flowResult.tx.outputs.single().data as Obligation.State
        val flow = CancelObligation.Initiator(obligation.linearId)
        val settleResult = a.services.startFlow(flow).resultFuture.getOrThrow()
        println(settleResult.tx)
        val settled = a.services.startFlow(GetQueue.OutgoingWithStatus(OBLIGATION_STATUS.CANCELLED.ordinal)).resultFuture.getOrThrow().filter {
            println(it)
            it.linearId == obligation.linearId.toString()
        }
        assert(settled.isNotEmpty())
    }
}