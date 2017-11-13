package com.r3.demos.ubin2a.execute

import com.r3.demos.ubin2a.base.SGD
import com.r3.demos.ubin2a.cash.SelfIssueCashFlow
import com.r3.demos.ubin2a.detect.ReceiveScanAcknowledgement
import com.r3.demos.ubin2a.detect.ReceiveScanRequest
import com.r3.demos.ubin2a.detect.ReceiveScanResponse
import com.r3.demos.ubin2a.obligation.IssueObligation
import com.r3.demos.ubin2a.obligation.Obligation
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.Amount
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.finance.contracts.getCashBalance
import net.corda.node.internal.StartedNode
import net.corda.testing.chooseIdentity
import net.corda.testing.node.MockNetwork
import org.junit.After
import org.junit.Before
import java.util.*

class ExecuteTests {
    lateinit var net: MockNetwork
    lateinit var bank1: StartedNode<MockNetwork.MockNode>
    lateinit var bank2: StartedNode<MockNetwork.MockNode>
    lateinit var bank3: StartedNode<MockNetwork.MockNode>

    private val sgd = Currency.getInstance("SGD")

    @Before
    fun setup() {
        net = MockNetwork(threadPerNode = true)
        val nodes = net.createSomeNodes(3)
        bank1 = nodes.partyNodes[0] // Mock company 2
        bank2 = nodes.partyNodes[1] // Mock company 3
        bank3 = nodes.partyNodes[2] // Mock company 4

        net.nodes.forEach {
            it.registerInitiatedFlow(IssueObligation.Responder::class.java)
            it.registerInitiatedFlow(ReceiveScanRequest::class.java)
            it.registerInitiatedFlow(ReceiveScanAcknowledgement::class.java)
            it.registerInitiatedFlow(ReceiveScanResponse::class.java)
        }
    }

    @After
    fun tearDown() {
        net.stopNodes()
    }

    private fun createObligation(borrower: StartedNode<MockNetwork.MockNode>,
                                 lender: StartedNode<MockNetwork.MockNode>,
                                 amount: Amount<Currency>,
                                 anonymous: Boolean,
                                 priority: Int = 0): CordaFuture<SignedTransaction> {
        val flow = IssueObligation.Initiator(amount, lender.info.chooseIdentity(), priority, anonymous = anonymous)
        return borrower.services.startFlow(flow).resultFuture
    }

    private fun createStates() {
        bank1.services.startFlow(SelfIssueCashFlow(600.SGD)).resultFuture.getOrThrow()
        bank2.services.startFlow(SelfIssueCashFlow(1500.SGD)).resultFuture.getOrThrow()
        bank3.services.startFlow(SelfIssueCashFlow(900.SGD)).resultFuture.getOrThrow()
        createObligation(bank2, bank1, 800.SGD, anonymous = true)
        createObligation(bank3, bank2, 2000.SGD, anonymous = true)
        createObligation(bank1, bank3, 1000.SGD, anonymous = true)
    }

    private fun printCashBalances() {
        val bank1 = bank1.database.transaction { bank1.services.getCashBalance(sgd) }
        val bank2 = bank2.database.transaction { bank2.services.getCashBalance(sgd) }
        val bank3 = bank3.database.transaction { bank3.services.getCashBalance(sgd) }
        println("Bank1: $bank1, bank2: $bank2, bank3: $bank3")
    }

    private fun printObligationBalances(bank: StartedNode<MockNetwork.MockNode>) {
        val me = bank.services.myInfo.chooseIdentity()
        bank.database.transaction {
            val queryCriteria = QueryCriteria.LinearStateQueryCriteria()
            val obligations = bank.services.vaultService.queryBy<Obligation.State>(queryCriteria).states.map {
                it.state.data
            }
            obligations.forEach { println("$me: ${it.borrower} owes ${it.lender} ${it.amount}") }
        }
    }

//    @Test
//    fun confidentialIdentitiesTest() {
//        // 1. Setup.
//        createStates()
//        printCashBalances()
//        printObligationBalances(bank1)
//        printObligationBalances(bank2)
//        printObligationBalances(bank3)
//        net.waitQuiescent()
//        // 2. Detect.
//        val one = bank1.services.startFlow(DetectFlow(sgd))
//        val two = bank2.services.startFlow(DetectFlow(sgd))
//        val three = bank3.services.startFlow(DetectFlow(sgd))
//        net.waitQuiescent()
//        val resultOne = one.resultFuture.getOrThrow()
//        val resultTwo = two.resultFuture.getOrThrow()
//        val resultThree = three.resultFuture.getOrThrow()
//        // 3. Handle detect result.
//        // For handling planning and execution.
//        fun doPlanAndExecute(bank: MockNetwork.MockNode, obligations: Set<Obligation.State>, limits: Map<AbstractParty, Long>) {
//            net.waitQuiescent()
//            println("-------------------------")
//            println("Detect algorithm results:")
//            println("-------------------------")
//            println(obligations)
//            println(limits)
//
//            // Perform netting.
//            println("--------------------")
//            println("Calculating netting:")
//            println("--------------------")
//            val (paymentsToMake, resultantObligations) = bank.services.startFlow(PlanFlow(obligations, limits, sgd)).resultFuture.getOrThrow()
//            net.waitQuiescent()
//            println(paymentsToMake)
//            println(resultantObligations)
//        }
//        // Choose which node runs the planning and execute stages based on the results of detect.
//        when {
//            resultOne.first.isNotEmpty() -> {
//                doPlanAndExecute(bank1, resultOne.first, resultOne.second)
//            }
//            resultTwo.first.isNotEmpty() -> {
//                doPlanAndExecute(bank1, resultOne.first, resultOne.second)
//            }
//            resultThree.first.isNotEmpty() -> {
//                doPlanAndExecute(bank1, resultOne.first, resultOne.second)
//            }
//            else -> throw IllegalStateException("Something went wrong. No winning scan!")
//        }
//
//        /**
//         * TODO: We need to change the detect flows to generate a random public key that is used for the obligations and the cash limits.
//         * We then use this key throughout the planning phase.
//         * We store ech key in an in memory structure as well as the identity management service as we need to get the key multiple times (eventually move it to the DB).
//         * When we come to the execution phase then we do lookups with the key to ascertain who needs to pay what.
//         */
//    }
//
//    @Test
//    fun testOne() {
//        // Setup.
//        createStates()
//        net.waitQuiescent()
//        // Parties.
//        val bankTwo = bank1.services.myInfo.legalIdentity
//        val bankThree = bank2.services.myInfo.legalIdentity
//        val bankFour = bank3.services.myInfo.legalIdentity
//
//        // Edges.
//        val edges = mapOf<Edge, Long>(
//                Edge(bankThree, bankTwo) to 800,
//                Edge(bankFour, bankThree) to 2000,
//                Edge(bankTwo, bankFour) to 1000
//        )
//
//        // Payments.
//        val payments = listOf(
//                Triple(bankThree, bankFour, Amount(100000, sgd)),
//                Triple(bankThree, bankTwo, Amount(20000, sgd))
//        )
//        println("PAYMENTS TO MAKE:")
//        println(payments)
//
//        // Obligations.
//        val obligations = mapOf<Edge, Long>()
//        println("RESULTANT OBLIGATIONS:")
//        println(obligations)
//
//        bank1.services.startFlow(ExecuteFlow(edges, payments, obligations))
//        net.waitQuiescent()
//
//    }
}




