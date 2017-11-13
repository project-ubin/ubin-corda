package com.r3.demos.ubin2a

import com.r3.demos.ubin2a.base.SGD
import com.r3.demos.ubin2a.base.TemporaryKeyManager
import com.r3.demos.ubin2a.cash.SelfIssueCashFlow
import com.r3.demos.ubin2a.detect.*
import com.r3.demos.ubin2a.execute.*
import com.r3.demos.ubin2a.obligation.IssueObligation
import com.r3.demos.ubin2a.obligation.Obligation
import com.r3.demos.ubin2a.obligation.PersistentObligationQueue
import com.r3.demos.ubin2a.obligation.SettleObligation
import com.r3.demos.ubin2a.plan.PlanFlow
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
import net.corda.testing.setCordappPackages
import net.corda.testing.unsetCordappPackages
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.*

class Scenario2 {
    lateinit var net: MockNetwork
    lateinit var bank1: StartedNode<MockNetwork.MockNode>
    lateinit var bank2: StartedNode<MockNetwork.MockNode>
    lateinit var bank3: StartedNode<MockNetwork.MockNode>
    lateinit var bank4: StartedNode<MockNetwork.MockNode>
    lateinit var bank5: StartedNode<MockNetwork.MockNode>

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
        bank4 = nodes.partyNodes[3] // Mock company 5
        bank5 = nodes.partyNodes[4] // Mock company 6

        nodes.partyNodes.forEach {
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
            it.database.transaction {
                it.internals.installCordaService(PersistentObligationQueue::class.java)
                it.internals.installCordaService(TemporaryKeyManager::class.java)
            }
        }
    }

    @After
    fun tearDown() {
        net.stopNodes()
        unsetCordappPackages()
    }

    private fun createCash() {
        bank1.services.startFlow(SelfIssueCashFlow(Amount(300, sgd))).resultFuture.getOrThrow()
        bank2.services.startFlow(SelfIssueCashFlow(Amount(400, sgd))).resultFuture.getOrThrow()
        bank3.services.startFlow(SelfIssueCashFlow(Amount(500, sgd))).resultFuture.getOrThrow()
        bank4.services.startFlow(SelfIssueCashFlow(Amount(400, sgd))).resultFuture.getOrThrow()
        bank5.services.startFlow(SelfIssueCashFlow(Amount(300, sgd))).resultFuture.getOrThrow()
    }

    fun createObligation(lender: StartedNode<MockNetwork.MockNode>,
                         borrower: StartedNode<MockNetwork.MockNode>,
                         amount: Amount<Currency>): CordaFuture<SignedTransaction> {
        val flow = IssueObligation.Initiator(amount, lender.services.myInfo.chooseIdentity(), priority = 0, anonymous = true)
        return borrower.services.startFlow(flow).resultFuture
    }

    private fun printCashBalances() {
        val bank1 = bank1.database.transaction { bank1.services.getCashBalance(sgd) }
        val bank2 = bank2.database.transaction { bank2.services.getCashBalance(sgd) }
        val bank3 = bank3.database.transaction { bank3.services.getCashBalance(sgd) }
        val bank4 = bank4.database.transaction { bank4.services.getCashBalance(sgd) }
        val bank5 = bank5.database.transaction { bank5.services.getCashBalance(sgd) }
        println("Bank1: $bank1, bank2: $bank2, bank3: $bank3, bank4: $bank4, bank5: $bank5")
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
    fun runTest() {
        println("----------------------")
        println("Starting scenario two:")
        println("----------------------")
        createCash()
        printCashBalances()
        println()

        createObligation(bank2, bank1, SGD(5)).getOrThrow().tx.outputStates.single() as Obligation.State
        createObligation(bank3, bank2, SGD(6)).getOrThrow().tx.outputStates.single() as Obligation.State
        createObligation(bank3, bank2, SGD(30)).getOrThrow().tx.outputStates.single() as Obligation.State
        createObligation(bank4, bank3, SGD(8)).getOrThrow().tx.outputStates.single() as Obligation.State
        createObligation(bank5, bank3, SGD(80)).getOrThrow().tx.outputStates.single() as Obligation.State
        createObligation(bank5, bank4, SGD(7)).getOrThrow().tx.outputStates.single() as Obligation.State
        createObligation(bank3, bank1, SGD(6)).getOrThrow().tx.outputStates.single() as Obligation.State
        createObligation(bank1, bank5, SGD(8)).getOrThrow().tx.outputStates.single() as Obligation.State
        createObligation(bank2, bank5, SGD(100)).getOrThrow().tx.outputStates.single() as Obligation.State
        createObligation(bank1, bank4, SGD(5)).getOrThrow().tx.outputStates.single() as Obligation.State
        /**
         * Expected final balance : Bank 1: $6, Bank 2: $3, Bank 3: $3, Bank 4: $5, Bank 5: $2
         */

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
    }
}