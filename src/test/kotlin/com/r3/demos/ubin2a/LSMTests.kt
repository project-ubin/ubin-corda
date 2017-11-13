package com.r3.demos.ubin2a

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
import net.corda.core.identity.AbstractParty
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

class LSMTests {
    lateinit var net: MockNetwork
    lateinit var bank1: StartedNode<MockNetwork.MockNode>
    lateinit var bank2: StartedNode<MockNetwork.MockNode>
    lateinit var bank3: StartedNode<MockNetwork.MockNode>
    lateinit var bank4: StartedNode<MockNetwork.MockNode>
    lateinit var bank5: StartedNode<MockNetwork.MockNode>
    lateinit var bank6: StartedNode<MockNetwork.MockNode>

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
        bank6 = nodes.partyNodes[5] // Mock company 7

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

    fun createObligation(lender: StartedNode<MockNetwork.MockNode>,
                         borrower: StartedNode<MockNetwork.MockNode>,
                         amount: Amount<Currency>): CordaFuture<SignedTransaction> {
        val flow = IssueObligation.Initiator(amount, lender.services.myInfo.chooseIdentity(), priority = 0, anonymous = true)
        return borrower.services.startFlow(flow).resultFuture
    }

    @After
    fun tearDown() {
        net.stopNodes()
        unsetCordappPackages()
    }

    private fun createCash() {
        bank1.services.startFlow(SelfIssueCashFlow(Amount(100000, sgd))).resultFuture.getOrThrow()
        bank2.services.startFlow(SelfIssueCashFlow(Amount(100000, sgd))).resultFuture.getOrThrow()
        bank3.services.startFlow(SelfIssueCashFlow(Amount(100000, sgd))).resultFuture.getOrThrow()
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
    fun scenarioOne() {
        createCash()
        println("----------------------")
        println("Starting scenario one:")
        println("----------------------")
        val sgd = Currency.getInstance("SGD")
        printCashBalances()
        println()

        // Create obligation between 1 and 2.
        println("Bank1 borrows 500 from Bank2")
        val fut1 = createObligation(bank2, bank1, Amount(50000, sgd)).getOrThrow().tx.outputStates.single() as Obligation.State
        println(fut1.toString())
        val linearId1 = fut1.linearId
        // Settle obligation between 1 and 2.
        println("Bank1 pays 500 to Bank2")
        val settle1 = bank1.services.startFlow(SettleObligation.Initiator(linearId1)).resultFuture.getOrThrow()
        net.waitQuiescent()
        println(settle1.tx)
        printCashBalances()
        println()

        // Create obligation between 3 and 1.
        println("Bank3 borrows 100 from Bank1")
        val fut2 = createObligation(bank1, bank3, Amount(10000, sgd)).getOrThrow().tx.outputStates.single() as Obligation.State
        println(fut2.toString())
        val linearId2 = fut2.linearId
        // Settle obligation between 3 and 1.
        println("Bank3 pays 100 to Bank1")
        val settle2 = bank3.services.startFlow(SettleObligation.Initiator(linearId2)).resultFuture.getOrThrow()
        println(settle2.tx)
        net.waitQuiescent()
        printCashBalances()
        println()


        // Create obligation between 1 and 2.
        println("Bank1 borrows 800 from Bank2")
        val fut3 = createObligation(bank2, bank1, Amount(80000, sgd)).getOrThrow().tx.outputStates.single() as Obligation.State
        println(fut3.toString())
        val linearId3 = fut3.linearId
        // Settle obligation between 1 and 2.
        println("Bank1 pays 800 to Bank2")
        try {
            bank1.services.startFlow(SettleObligation.Initiator(linearId3)).resultFuture.getOrThrow()
        } catch (e: IllegalArgumentException) {
            println("Oh no! Bank1 doesn't have enough cash to settle!")
        }
        net.waitQuiescent()
        printCashBalances()
        printObligationBalances(bank1)
        println()

        // Create obligation between 2 and 1.
        println("Bank2 borrows 2000 from Bank3")
        val fut4 = createObligation(bank3, bank2, Amount(200000, sgd)).getOrThrow().tx.outputStates.single() as Obligation.State
        println(fut4.toString())
        val linearId4 = fut4.linearId
        // Settle obligation between 1 and 2.
        println("Bank2 pays 2000 to Bank3")
        try {
            bank2.services.startFlow(SettleObligation.Initiator(linearId4)).resultFuture.getOrThrow()
        } catch (e: IllegalArgumentException) {
            println("Oh no! Bank2 doesn't have enough cash to settle!")
        }
        net.waitQuiescent()
        printCashBalances()
        printObligationBalances(bank2)
        println()

        // ATTEMPT NETTING - NO CHANGE
        // TODO: What do we have to do here?

        // Create obligation between 3 and 1.
        println("Bank3 borrows 1000 from Bank1")
        val fut5 = createObligation(bank1, bank3, Amount(100000, sgd)).getOrThrow().tx.outputStates.single() as Obligation.State
        println(fut5.toString())
        val linearId5 = fut5.linearId
        // Settle obligation between 1 and 2.
        println("Bank3 pays 1000 to Bank1")
        try {
            bank3.services.startFlow(SettleObligation.Initiator(linearId5)).resultFuture.getOrThrow()
        } catch (e: IllegalArgumentException) {
            println("Oh no! Bank3 doesn't have enough cash to settle!")
        }
        net.waitQuiescent()
        printCashBalances()
        printObligationBalances(bank3)
        println()

        // Perform netting.
        println("--------------------------")
        println("Starting detect algorithm:")
        println("--------------------------")
        val one = bank1.services.startFlow(DetectFlow(sgd))
        val two = bank2.services.startFlow(DetectFlow(sgd))
        val three = bank3.services.startFlow(DetectFlow(sgd))
        net.waitQuiescent()
        val resultOne = one.resultFuture.getOrThrow()
        val resultTwo = two.resultFuture.getOrThrow()
        val resultThree = three.resultFuture.getOrThrow()

        fun planAndExecute(bank: StartedNode<MockNetwork.MockNode>,
                           obligations: Set<Obligation.State>,
                           limits: Map<AbstractParty, Long>) {
            net.waitQuiescent()
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
            val (paymentsToMake, resultantObligations) = bank.services.startFlow(flow).resultFuture.getOrThrow()
            net.waitQuiescent()
            println("Payments to make:")
            println(paymentsToMake)
            println("Resultant obligations:")
            println(resultantObligations)
            val executeFlow = ExecuteFlow(obligations, resultantObligations, paymentsToMake)
            bank.services.startFlow(executeFlow).resultFuture.getOrThrow()
            net.waitQuiescent()
        }

        when {
            resultOne.first.isNotEmpty() -> {
                planAndExecute(bank1, resultOne.first, resultOne.second)
            }
            resultTwo.first.isNotEmpty() -> {
                planAndExecute(bank2, resultTwo.first, resultTwo.second)
            }
            resultThree.first.isNotEmpty() -> {
                planAndExecute(bank3, resultThree.first, resultThree.second)
            }
            else -> throw IllegalStateException("Something went wrong. No winning scan!")
        }
//        // Building transaction.
//        println("----------------")
//        println("Execution phase:")
//        println("----------------")
//        bank1.services.startFlow(ExecuteFlow(obligations, paymentsToMake))
//        net.waitQuiescent()
////        net.waitQuiescent()
////        bank1.database.transaction {
////            bank1.services.validatedTransactions.track().snapshot.forEach { transaction ->
////                println(transaction.tx)
////            }
////            bank1.services.validatedTransactions.updates.subscribe { transaction ->
////                println(transaction.tx)
////            }
////        }
//        println("-------------------")
//        println("Execution finished:")
//        println("-------------------")
//        printCashBalances()
//        printObligationBalances(bank1)
//        printObligationBalances(bank2)
//        printObligationBalances(bank3)
    }

    @Test
    fun scenarioTwo() {
        // Create cash.
        bank1.services.startFlow(SelfIssueCashFlow(Amount(60000, sgd))).resultFuture.getOrThrow()
        bank2.services.startFlow(SelfIssueCashFlow(Amount(150000, sgd))).resultFuture.getOrThrow()
        bank3.services.startFlow(SelfIssueCashFlow(Amount(90000, sgd))).resultFuture.getOrThrow()

        println("----------------------")
        println("Starting scenario two:")
        println("----------------------")
        val sgd = Currency.getInstance("SGD")
        printCashBalances()
        println()

        println("Bank1 borrows 800 from Bank2")
        createObligation(bank2, bank1, Amount(80000, sgd)).getOrThrow().tx.outputStates.single() as Obligation.State

        println("Bank2 borrows 2000 from Bank3")
        createObligation(bank3, bank2, Amount(200000, sgd)).getOrThrow().tx.outputStates.single() as Obligation.State

        println("Bank3 borrows 1000 from Bank1")
        createObligation(bank1, bank3, Amount(100000, sgd)).getOrThrow().tx.outputStates.single() as Obligation.State

        println("Bank4 borrows 1 from Bank1")
        createObligation(bank4, bank1, Amount(100, sgd)).getOrThrow().tx.outputStates.single() as Obligation.State

        println("Bank5 borrows 1 from Bank4")
        createObligation(bank5, bank4, Amount(100, sgd)).getOrThrow().tx.outputStates.single() as Obligation.State

        println("Bank5 borrows 1 from Bank2")
        createObligation(bank5, bank2, Amount(100, sgd)).getOrThrow().tx.outputStates.single() as Obligation.State

        println("Bank5 borrows 1 from Bank6")
        createObligation(bank5, bank6, Amount(100, sgd)).getOrThrow().tx.outputStates.single() as Obligation.State


        // Perform netting.
        println("--------------------------")
        println("Starting detect algorithm:")
        println("--------------------------")
        val four = bank6.services.startFlow(DetectFlow(sgd))
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
        val (paymentsToMake, resultantObligations) = bank6.services.startFlow(flow).resultFuture.getOrThrow()
        net.waitQuiescent()
        println("Payments to make:")
        println(paymentsToMake)
        println("Resultant obligations:")
        println(resultantObligations)
        val executeFlow = ExecuteFlow(obligations, resultantObligations, paymentsToMake)
        bank6.services.startFlow(executeFlow).resultFuture.getOrThrow()
        net.waitQuiescent()
        println(ExecuteDataStore.obligations)
        println(ExecuteDataStore.payments)
    }
}