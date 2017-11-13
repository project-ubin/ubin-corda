package com.r3.demos.ubin2a.redeem

import com.r3.demos.ubin2a.base.CENTRAL_PARTY_X500
import com.r3.demos.ubin2a.base.OBLIGATION_PRIORITY
import com.r3.demos.ubin2a.base.REGULATOR_PARTY_X500
import com.r3.demos.ubin2a.base.SGD
import com.r3.demos.ubin2a.cash.AcceptPayment
import com.r3.demos.ubin2a.cash.Pay
import com.r3.demos.ubin2a.pledge.ApprovePledge
import com.r3.demos.ubin2a.obligation.IssueObligation
import com.r3.demos.ubin2a.obligation.PersistentObligationQueue
import net.corda.core.node.services.queryBy
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

class RedeemTests {
    lateinit var net: MockNetwork
    lateinit var bank1: StartedNode<MockNetwork.MockNode>
    lateinit var bank2: StartedNode<MockNetwork.MockNode>
    lateinit var bank3: StartedNode<MockNetwork.MockNode>
    lateinit var regulator: StartedNode<MockNetwork.MockNode>
    lateinit var centralBank: StartedNode<MockNetwork.MockNode>

    val sgd = java.util.Currency.getInstance("SGD")

    @Before
    fun setup() {
        setCordappPackages(
                "net.corda.finance",
                "com.r3.demos.ubin2a.obligation",
                "com.r3.demos.ubin2a.redeem",
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
        it.registerInitiatedFlow(IssueRedeem.Responder::class.java)
        it.registerInitiatedFlow(ApproveRedeem.Responder::class.java)
        it.database.transaction {
            it.internals.installCordaService(PersistentObligationQueue::class.java)
            it.internals.installCordaService(ExternalRedeemService.Service::class.java)
        }
    }

    private fun printCashBalances() {
        val centralBank = centralBank.database.transaction { centralBank.services.getCashBalance(sgd) }
        val bank1 = bank1.database.transaction { bank1.services.getCashBalance(sgd) }
        val bank2 = bank2.database.transaction { bank2.services.getCashBalance(sgd) }
        val bank3 = bank3.database.transaction { bank3.services.getCashBalance(sgd) }
        println("CentralBank: $centralBank Bank1: $bank1, bank2: $bank2, bank3: $bank3")
    }

    @Test
    fun `issue Redeem request`() {
        println("----------------------")
        println("Test issue Redeem request:")
        println("----------------------")
        val sgd = SGD
        printCashBalances()
        println()

        // Approve pledge amount to counter party
        centralBank.services.startFlow(ApprovePledge.Initiator(SGD(3), bank1.services.myInfo.chooseIdentity())).resultFuture.getOrThrow()
        net.waitQuiescent()
        printCashBalances()
        println()
        // Confirm bank1 receives the pledged amount
        val balance1 = bank1.database.transaction { bank1.services.getCashBalance(sgd) }
        assert(balance1.quantity / 100 == 3L)

        println("----------------------")
        println("Issue Redeem")
        println("----------------------")
        val amount = SGD(3)
        val flow = IssueRedeem.Initiator(amount, true)
        val fut1 = bank1.services.startFlow(flow).resultFuture.getOrThrow()
        val linearId = fut1.tx.outputsOfType<Redeem.State>().single().linearId
        net.waitQuiescent()
        printCashBalances()
        println()

        // Confirm bank 1 has reduced balance after a redeem request
        val balance2 = bank1.database.transaction { bank1.services.getCashBalance(sgd) }
        assert(balance2.quantity / 100 == 0L)
        val allRedeem = bank1.database.transaction { bank1.services.vaultService.queryBy<Redeem.State>().states }

        // Confirm the redeem state is requested
        println("Number of redeem state created " + allRedeem.size)
        assert(allRedeem.size == 1)
        println("LinearId " + allRedeem.single().state.data.linearId)
        assert(linearId == allRedeem.single().state.data.linearId )
    }

    @Test
    fun `approve Redeem Request`() {
        println("----------------------")
        println("Test approve Redeem request:")
        println("----------------------")
        val sgd = SGD
        val me = bank1.services.myInfo.chooseIdentity()
        printCashBalances()
        println()

        // Approve pledge amount to counter party
        centralBank.services.startFlow(ApprovePledge.Initiator(SGD(300), bank1.services.myInfo.chooseIdentity())).resultFuture.getOrThrow()
        net.waitQuiescent()
        printCashBalances()
        println()
        // Confirm bank1 receives the pledged amount
        val balance1_before = bank1.database.transaction { bank1.services.getCashBalance(sgd) }
        assert(balance1_before.quantity / 100 == 300L)

        println("----------------------")
        println("Issue Redeem")
        println("----------------------")
        val amount = SGD(150)
        val flow = IssueRedeem.Initiator(amount, true)
        val fut1 = bank1.services.startFlow(flow).resultFuture.getOrThrow()
        val linearId = fut1.tx.outputsOfType<Redeem.State>().single().linearId
        net.waitQuiescent()
        printCashBalances()
        println()

        // Confirm bank 1 has reduced balance after a redeem request
        val balance1_after = bank1.database.transaction { bank1.services.getCashBalance(sgd) }
        assert(balance1_after.quantity / 100 == 150L)
        val balanceCentral_before = centralBank.database.transaction { centralBank.services.getCashBalance(sgd) }
        assert(balanceCentral_before.quantity / 100 == 150L)

        // Confirm the redeem state is requested
        val allRedeems = bank1.database.transaction { bank1.services.vaultService.queryBy<Redeem.State>().states }
        println("Number of redeem state created " + allRedeems.size)
        assert(allRedeems.size == 1)
        println("LinearId " + allRedeems.single().state.data.linearId)
        assert(linearId == allRedeems.single().state.data.linearId )


        println("----------------------")
        println("Approve Redeem")
        println("----------------------")
        val flow2 = ApproveRedeem.Initiator(fut1.tx.id.toString(),true)
        val fut2 = centralBank.services.startFlow(flow2).resultFuture.getOrThrow()
        net.waitQuiescent()
        printCashBalances()
        println()

        // Confirm the redeem state is requested
        val allRedeems_after = bank1.database.transaction { bank1.services.vaultService.queryBy<Redeem.State>().states }
        println("Number of redeem in vault " + allRedeems_after.size)
        assert(allRedeems_after.isEmpty())

        val balanceCentral_after = centralBank.database.transaction { centralBank.services.getCashBalance(sgd) }
        assert(balanceCentral_after.quantity / 100 == 0L)
    }

    /**
     * TODO: There's a bug that this is failing after MAS receive a normal transfer
     */
    @Test
    fun `approve Redeem after Receive Normal Transfer`() {
        println("----------------------")
        println("Test approve Redeem request:")
        println("----------------------")
        val sgd = SGD
        val me = bank1.services.myInfo.chooseIdentity()
        printCashBalances()
        println()

        // Approve pledge amount to counter party
        centralBank.services.startFlow(ApprovePledge.Initiator(SGD(100000000), bank1.services.myInfo.chooseIdentity())).resultFuture.getOrThrow()
        net.waitQuiescent()
        printCashBalances()
        println()

        println("----------------------")
        println("Issue Redeem")
        println("----------------------")
        val issueRedeem_1 = IssueRedeem.Initiator(SGD(11111), true)
        val issueOutput_1 = bank1.services.startFlow(issueRedeem_1).resultFuture.getOrThrow()
        val linearId_1 = issueOutput_1.tx.outputsOfType<Redeem.State>().single().linearId
        net.waitQuiescent()
        printCashBalances()
        println()

        val payFlow_1 = Pay(centralBank.info.chooseIdentity(), SGD(22222), OBLIGATION_PRIORITY.NORMAL.ordinal)
        val payOutput_1 = bank1.services.startFlow(payFlow_1).resultFuture.getOrThrow()
        println(payOutput_1.tx.toString())
        net.waitQuiescent()
        printCashBalances()
        println()

        val payFlow_2 = Pay(centralBank.info.chooseIdentity(), SGD(33333), OBLIGATION_PRIORITY.NORMAL.ordinal)
        val payOutput_2 = bank1.services.startFlow(payFlow_2).resultFuture.getOrThrow()
        println(payOutput_2.tx.toString())
        net.waitQuiescent()
        printCashBalances()
        println()

        println("----------------------")
        println("Issue Redeem")
        println("----------------------")
        val issueRedeem_2 = IssueRedeem.Initiator(SGD(44444), true)
        val issueOutput_2 = bank1.services.startFlow(issueRedeem_2).resultFuture.getOrThrow()
        val linearId_2 = issueOutput_2.tx.outputsOfType<Redeem.State>().single().linearId
        net.waitQuiescent()
        printCashBalances()
        println()

        val payFlow_3 = Pay(centralBank.info.chooseIdentity(), SGD(55555), OBLIGATION_PRIORITY.NORMAL.ordinal)
        val payOutput_3 = bank1.services.startFlow(payFlow_3).resultFuture.getOrThrow()
        println(payOutput_3.tx.toString())
        net.waitQuiescent()
        printCashBalances()
        println()

        println("----------------------")
        println("Issue Redeem")
        println("----------------------")
        val issueRedeem_3 = IssueRedeem.Initiator(SGD(66666), true)
        val issueOutput_3 = bank1.services.startFlow(issueRedeem_3).resultFuture.getOrThrow()
        val linearId_3 = issueOutput_3.tx.outputsOfType<Redeem.State>().single().linearId
        net.waitQuiescent()
        printCashBalances()
        println()

        println("----------------------")
        println("Approve Redeem")
        println("----------------------")
        val approveRedeem_1 = ApproveRedeem.Initiator(issueOutput_1.tx.id.toString(),true)
        val approveOutput_1 = centralBank.services.startFlow(approveRedeem_1).resultFuture.getOrThrow()
        net.waitQuiescent()
        printCashBalances()
        println()

        println("----------------------")
        println("Approve Redeem")
        println("----------------------")
        val approveRedeem_2 = ApproveRedeem.Initiator(issueOutput_2.tx.id.toString(),true)
        val approveOutput_2 = centralBank.services.startFlow(approveRedeem_2).resultFuture.getOrThrow()
        net.waitQuiescent()
        printCashBalances()
        println()

        println("----------------------")
        println("Approve Redeem")
        println("----------------------")
        val approveRedeem_3 = ApproveRedeem.Initiator(issueOutput_3.tx.id.toString(),true)
        val approveOutput_3 = centralBank.services.startFlow(approveRedeem_3).resultFuture.getOrThrow()
        net.waitQuiescent()
        printCashBalances()
        println()
    }

    @Test
    fun `approve Redeem Requests From Different Banks`() {
        println("----------------------")
        println("Test approve Redeem request:")
        println("----------------------")
        val sgd = SGD
        val me = bank1.services.myInfo.chooseIdentity()
        printCashBalances()
        println()

        // Approve pledge amount to counter party
        centralBank.services.startFlow(ApprovePledge.Initiator(SGD(100000000), bank1.services.myInfo.chooseIdentity())).resultFuture.getOrThrow()
        centralBank.services.startFlow(ApprovePledge.Initiator(SGD(100000000), bank2.services.myInfo.chooseIdentity())).resultFuture.getOrThrow()
        centralBank.services.startFlow(ApprovePledge.Initiator(SGD(100000000), bank3.services.myInfo.chooseIdentity())).resultFuture.getOrThrow()
        net.waitQuiescent()
        printCashBalances()
        println()

        println("----------------------")
        println("Issue Redeem")
        println("----------------------")
        val issueRedeem_1 = IssueRedeem.Initiator(SGD(11111), true)
        val issueOutput_1 = bank1.services.startFlow(issueRedeem_1).resultFuture.getOrThrow()
        val linearId_1 = issueOutput_1.tx.outputsOfType<Redeem.State>().single().linearId
        net.waitQuiescent()
        printCashBalances()
        println()

        println("----------------------")
        println("Issue Redeem")
        println("----------------------")
        val issueRedeem_2 = IssueRedeem.Initiator(SGD(22222), true)
        val issueOutput_2 = bank2.services.startFlow(issueRedeem_2).resultFuture.getOrThrow()
        val linearId_2 = issueOutput_2.tx.outputsOfType<Redeem.State>().single().linearId
        net.waitQuiescent()
        printCashBalances()
        println()

        println("----------------------")
        println("Issue Redeem")
        println("----------------------")
        val issueRedeem_3 = IssueRedeem.Initiator(SGD(33333), true)
        val issueOutput_3 = bank3.services.startFlow(issueRedeem_3).resultFuture.getOrThrow()
        val linearId_3 = issueOutput_3.tx.outputsOfType<Redeem.State>().single().linearId
        net.waitQuiescent()
        printCashBalances()
        println()

        println("----------------------")
        println("Issue Redeem")
        println("----------------------")
        val issueRedeem_4 = IssueRedeem.Initiator(SGD(44444), true)
        val issueOutput_4 = bank2.services.startFlow(issueRedeem_4).resultFuture.getOrThrow()
        val linearId_4 = issueOutput_4.tx.outputsOfType<Redeem.State>().single().linearId
        net.waitQuiescent()
        printCashBalances()
        println()

        println("----------------------")
        println("Issue Redeem")
        println("----------------------")
        val issueRedeem_5 = IssueRedeem.Initiator(SGD(55555), true)
        val issueOutput_5 = bank1.services.startFlow(issueRedeem_5).resultFuture.getOrThrow()
        val linearId_5 = issueOutput_5.tx.outputsOfType<Redeem.State>().single().linearId
        net.waitQuiescent()
        printCashBalances()
        println()

        println("----------------------")
        println("Approve Redeem")
        println("----------------------")
        val approveRedeem_1 = ApproveRedeem.Initiator(issueOutput_1.tx.id.toString(),true)
        val approveOutput_1 = centralBank.services.startFlow(approveRedeem_1).resultFuture.getOrThrow()
        net.waitQuiescent()
        printCashBalances()
        println()

        println("----------------------")
        println("Approve Redeem")
        println("----------------------")
        val approveRedeem_2 = ApproveRedeem.Initiator(issueOutput_2.tx.id.toString(),true)
        val approveOutput_2 = centralBank.services.startFlow(approveRedeem_2).resultFuture.getOrThrow()
        net.waitQuiescent()
        printCashBalances()
        println()

        println("----------------------")
        println("Approve Redeem")
        println("----------------------")
        val approveRedeem_3 = ApproveRedeem.Initiator(issueOutput_3.tx.id.toString(),true)
        val approveOutput_3 = centralBank.services.startFlow(approveRedeem_3).resultFuture.getOrThrow()
        net.waitQuiescent()
        printCashBalances()
        println()

        println("----------------------")
        println("Approve Redeem")
        println("----------------------")
        val approveRedeem_4 = ApproveRedeem.Initiator(issueOutput_4.tx.id.toString(),true)
        val approveOutput_4 = centralBank.services.startFlow(approveRedeem_4).resultFuture.getOrThrow()
        net.waitQuiescent()
        printCashBalances()
        println()

        println("----------------------")
        println("Approve Redeem")
        println("----------------------")
        val approveRedeem_5 = ApproveRedeem.Initiator(issueOutput_5.tx.id.toString(),true)
        val approveOutput_5 = centralBank.services.startFlow(approveRedeem_5).resultFuture.getOrThrow()
        net.waitQuiescent()
        printCashBalances()
        println()
    }

}