//package com.r3.demos.ubin
//
//import com.google.common.util.concurrent.ListenableFuture
//import com.r3.demos.ubin2a.base.Edge
//import com.r3.demos.ubin2a.cash.SelfIssueCashFlow
//import com.r3.demos.ubin2a.obligation.IssueObligation
//import com.r3.demos.ubin2a.obligation.Obligation
//import net.corda.core.contracts.Amount
//import net.corda.core.contracts.DOLLARS
//import net.corda.core.getOrThrow
//import net.corda.core.identity.Party
//import net.corda.core.transactions.SignedTransaction
//import net.corda.testing.node.MockNetwork
//import org.junit.Before
//import org.junit.Test
//import java.util.*
//
//package com.r3.demos.ubin2a.plan
//
//import com.google.common.util.concurrent.ListenableFuture
//import com.r3.demos.ubin2a.base.Edge
//import com.r3.demos.ubin2a.cash.SelfIssueCashFlow
//import com.r3.demos.ubin2a.obligation.IssueObligation
//import com.r3.demos.ubin2a.obligation.Obligation
//import net.corda.core.contracts.Amount
//import net.corda.core.contracts.DOLLARS
//import net.corda.core.getOrThrow
//import net.corda.core.identity.Party
//import net.corda.core.transactions.SignedTransaction
//import net.corda.testing.node.MockNetwork
//import org.junit.Before
//import org.junit.Test
//import java.util.*
//
//class FlowTests {
//
//    lateinit var net: MockNetwork
//    lateinit var notary: MockNetwork.MockNode
//    lateinit var bank1: MockNetwork.MockNode
//    lateinit var bank2: MockNetwork.MockNode
//    lateinit var bank3: MockNetwork.MockNode
//    lateinit var bank4: MockNetwork.MockNode
//    lateinit var bank5: MockNetwork.MockNode
//    lateinit var bank6: MockNetwork.MockNode
//    lateinit var bank7: MockNetwork.MockNode
//    lateinit var bank8: MockNetwork.MockNode
//    lateinit var bank9: MockNetwork.MockNode
//
//    @Before
//    fun setup() {
//        net = net.corda.testing.node.MockNetwork()
//        val nodes = net.createSomeNodes(9)
//        notary = nodes.notaryNode
//        bank1 = nodes.partyNodes[0]
//        bank2 = nodes.partyNodes[1]
//        bank3 = nodes.partyNodes[2]
//        bank4 = nodes.partyNodes[3]
//        bank5 = nodes.partyNodes[4]
//        bank6 = nodes.partyNodes[5]
//        bank7 = nodes.partyNodes[6]
//        bank8 = nodes.partyNodes[7]
//        bank9 = nodes.partyNodes[8]
//
//        nodes.partyNodes.forEach {
//            it.registerInitiatedFlow(IssueObligation.Responder::class.java)
////            it.registerInitiatedFlow(BuildTxFlow.Responder::class.java)
////            it.registerInitiatedFlow(CollectStatesFlow.Responder::class.java)
//        }
//        net.runNetwork()
//        buildNetwork()
//    }
//
//    @Test
//    fun flowReturnsCorrectEndState() {
//        //lender, borrower
//        val obligations: Map<Edge, Long> = mapOf(
//                Pair(Edge(bank2.info.legalIdentity, bank1.info.legalIdentity), 1500L),
//                Pair(Edge(bank5.info.legalIdentity, bank1.info.legalIdentity), 1000L),
//                Pair(Edge(bank8.info.legalIdentity, bank1.info.legalIdentity), 900L),
//                Pair(Edge(bank3.info.legalIdentity, bank2.info.legalIdentity), 2000L),
//                Pair(Edge(bank7.info.legalIdentity, bank2.info.legalIdentity), 2500L),
//                Pair(Edge(bank9.info.legalIdentity, bank2.info.legalIdentity), 1100L),
//                Pair(Edge(bank1.info.legalIdentity, bank3.info.legalIdentity), 700L),
//                Pair(Edge(bank2.info.legalIdentity, bank3.info.legalIdentity), 800L),
//                Pair(Edge(bank4.info.legalIdentity, bank3.info.legalIdentity), 1300L),
//                Pair(Edge(bank6.info.legalIdentity, bank3.info.legalIdentity), 1400L),
//                Pair(Edge(bank5.info.legalIdentity, bank4.info.legalIdentity), 1700L),
//                Pair(Edge(bank2.info.legalIdentity, bank5.info.legalIdentity), 1600L),
//                Pair(Edge(bank4.info.legalIdentity, bank6.info.legalIdentity), 1050L),
//                Pair(Edge(bank9.info.legalIdentity, bank8.info.legalIdentity), 950L),
//                Pair(Edge(bank8.info.legalIdentity, bank9.info.legalIdentity), 850L))
//
//        val limits: Map<Party, Long> = mapOf(
//                Pair(bank1.info.legalIdentity, 600L),
//                Pair(bank2.info.legalIdentity, 600L),
//                Pair(bank3.info.legalIdentity, 600L),
//                Pair(bank4.info.legalIdentity, 600L),
//                Pair(bank5.info.legalIdentity, 600L),
//                Pair(bank6.info.legalIdentity, 600L),
//                Pair(bank7.info.legalIdentity, 600L),
//                Pair(bank8.info.legalIdentity, 600L),
//                Pair(bank9.info.legalIdentity, 600L))
//
//        val flow = PlanFlow(obligations, limits)
//        val fut = bank1.services.startFlow(flow).resultFuture
//        net.runNetwork()
//        fut.getOrThrow()
//    }
//
//    private fun createObligation(lender: MockNetwork.MockNode, borrower: MockNetwork.MockNode, amount: Amount<Currency>): ListenableFuture<SignedTransaction> {
//        val iou = Obligation.State(amount, lender.info.legalIdentity, borrower.info.legalIdentity)
//        val flow = IssueObligation.Initiator(iou)
//        return lender.services.startFlow(flow).resultFuture
//    }
//
//    @Test
//    fun flowReturnsCorrectlyFormedPartiallySignedTransaction() {
//        /*val obligationsMap1: LinkedHashMap<Party, Amount<Currency>> = LinkedHashMap()
//
//        obligationsMap1.put(bank2.info.legalIdentity, 5.DOLLARS)
//        obligationsMap1.put(bank3.info.legalIdentity, 5.DOLLARS)
//        obligationsMap1.put(bank4.info.legalIdentity, 5.DOLLARS)
//
//        val obligations = listOf(
//                Pair(bank1.info.legalIdentity, obligationsMap1))
//
//        val limits: LinkedHashMap<Party, Amount<Currency>> = LinkedHashMap()
//        limits.put(bank1.info.legalIdentity, 10.DOLLARS)
//        limits.put(bank2.info.legalIdentity, 10.DOLLARS)
//        limits.put(bank3.info.legalIdentity, 10.DOLLARS)
//        limits.put(bank4.info.legalIdentity, 10.DOLLARS)
//
//        val flow = ExecuteFlow.Initiator(obligations, limits)
//        val fut = bank1.services.startFlow(flow).resultFuture
//        net.runNetwork()
//        var finalIOUState = bank1.services.vaultQueryService.queryBy<Obligation.State>()
//        var finalCashState = bank1.services.vaultQueryService.queryBy<Cash.State>()
//        fut.getOrThrow()*/
//    }
//
//    @Test
//    fun collectStatesFlowReturnsCorrectStates() {
//        //val iouStates = bank1.vaultQuery.queryBy<IOUState>().states.filter { it.state.data.borrower.equals(bank1.info.legalIdentity) }
//        var edges: MutableList<Pair<Party, Party>> = mutableListOf()
//        edges.add(Pair(bank1.info.legalIdentity, bank2.info.legalIdentity))
//        edges.add(Pair(bank3.info.legalIdentity, bank4.info.legalIdentity))
//        edges.add(Pair(bank2.info.legalIdentity, bank3.info.legalIdentity))
//        edges.add(Pair(bank4.info.legalIdentity, bank1.info.legalIdentity))
//
//        //val graph = buildGraph(edges)
//        //val flow = CollectStatesFlow.Initiator(graph, true)
//        //val fut = bank1.services.startFlow(flow).resultFuture
//        //net.runNetwork()
//        //fut.getOrThrow()
//    }
//
//    /*private fun buildGraph(edges: MutableList<Pair<Party, Party>> = mutableListOf()) : GraphState
//    {
//        val allNodes = edges.map { it.first }.union(edges.map { it.second }).toList()
//        // The minimum spanning tree is calculated using a depth first graph traversal algorithm
//        val mst = DepthFirstSearch.java().calculate(edges, allNodes)
//        // The stack contains both the minimum spanning tree and it's reverse. This allows us to pre-calculate the full return path, collecting states and signatures in O(2n)
//        val mapStack = ExecuteFlow.mstToStackWithReverse(mst)
//        val obligationInput: MutableList<StateAndRef<Obligation.State>> = mutableListOf()
//        val obligationOutput: MutableList<Obligation.State> = mutableListOf()
//        val cashInput: MutableList<StateAndRef<Cash.State>> = mutableListOf()
//        val cashOutput: MutableList<Cash.State> = mutableListOf()
//        val states = States(obligationInput, cashInput, obligationOutput, cashOutput)
//        // Calculate optimal netting
//        //Create initial graph state
//        //return GraphState(mapStack, states, nettingSolution,  null)
//    }*/
//
//    private fun buildNetworkDaveExample()
//    {
//        bank1.services.startFlow(SelfIssueCashFlow(5.DOLLARS)).resultFuture.getOrThrow()
//        bank2.services.startFlow(SelfIssueCashFlow(12.DOLLARS)).resultFuture.getOrThrow()
//        bank3.services.startFlow(SelfIssueCashFlow(7.DOLLARS)).resultFuture.getOrThrow()
//        bank4.services.startFlow(SelfIssueCashFlow(9.DOLLARS)).resultFuture.getOrThrow()
//        val fut1 = createObligation(bank2, bank1, 10.DOLLARS)
//        val fut2 = createObligation(bank3, bank2, 15.DOLLARS)
//        val fut3 = createObligation(bank4, bank3, 20.DOLLARS)
//        val fut4 = createObligation(bank1, bank4, 25.DOLLARS)
//        net.runNetwork()
//        fut1.getOrThrow()
//        fut2.getOrThrow()
//        fut3.getOrThrow()
//        fut4.getOrThrow()
//    }
//
//    private fun buildNetwork()
//    {
//        bank1.services.startFlow(SelfIssueCashFlow(10000.DOLLARS)).resultFuture.getOrThrow()
//        bank2.services.startFlow(SelfIssueCashFlow(10000.DOLLARS)).resultFuture.getOrThrow()
//        bank3.services.startFlow(SelfIssueCashFlow(10000.DOLLARS)).resultFuture.getOrThrow()
//        bank4.services.startFlow(SelfIssueCashFlow(10000.DOLLARS)).resultFuture.getOrThrow()
//        bank5.services.startFlow(SelfIssueCashFlow(10000.DOLLARS)).resultFuture.getOrThrow()
//        bank6.services.startFlow(SelfIssueCashFlow(10000.DOLLARS)).resultFuture.getOrThrow()
//        bank7.services.startFlow(SelfIssueCashFlow(10000.DOLLARS)).resultFuture.getOrThrow()
//        bank8.services.startFlow(SelfIssueCashFlow(10000.DOLLARS)).resultFuture.getOrThrow()
//        bank9.services.startFlow(SelfIssueCashFlow(10000.DOLLARS)).resultFuture.getOrThrow()
//        val fut1 = createObligation(bank2, bank1, 1500.DOLLARS)
//        val fut2 = createObligation(bank5, bank1, 1000.DOLLARS)
//        val fut3 = createObligation(bank8, bank1, 900.DOLLARS)
//        val fut4 = createObligation(bank3, bank2, 2000.DOLLARS)
//        val fut5 = createObligation(bank7, bank2, 2500.DOLLARS)
//        val fut6 = createObligation(bank9, bank2, 1100.DOLLARS)
//        val fut7 = createObligation(bank1, bank3, 700.DOLLARS)
//        val fut8 = createObligation(bank2, bank3, 800.DOLLARS)
//        val fut9 = createObligation(bank4, bank3, 1300.DOLLARS)
//        val fut10 = createObligation(bank6, bank3, 1400.DOLLARS)
//        val fut11 = createObligation(bank5, bank4, 1700.DOLLARS)
//        val fut12 = createObligation(bank2, bank5, 1600.DOLLARS)
//        val fut13 = createObligation(bank4, bank6, 1050.DOLLARS)
//        val fut14 = createObligation(bank9, bank8, 950.DOLLARS)
//        val fut15 = createObligation(bank8, bank9, 850.DOLLARS)
//
//        net.runNetwork()
//        fut1.getOrThrow()
//        fut2.getOrThrow()
//        fut3.getOrThrow()
//        fut4.getOrThrow()
//        fut5.getOrThrow()
//        fut6.getOrThrow()
//        fut7.getOrThrow()
//        fut8.getOrThrow()
//        fut9.getOrThrow()
//        fut10.getOrThrow()
//        fut11.getOrThrow()
//        fut12.getOrThrow()
//        fut13.getOrThrow()
//        fut14.getOrThrow()
//        fut15.getOrThrow()
//    }
//}
