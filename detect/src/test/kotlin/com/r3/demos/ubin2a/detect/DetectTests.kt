package com.r3.demos.ubin2a.detect

import com.r3.demos.ubin2a.base.TemporaryKeyManager
import com.r3.demos.ubin2a.cash.SelfIssueCashFlow
import com.r3.demos.ubin2a.obligation.IssueObligation
import com.r3.demos.ubin2a.obligation.PersistentObligationQueue
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.Amount
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.USD
import net.corda.node.internal.StartedNode
import net.corda.testing.chooseIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.setCordappPackages
import net.corda.testing.unsetCordappPackages
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.*

class DetectTests {
    lateinit var net: MockNetwork
    lateinit var bank1: StartedNode<MockNetwork.MockNode>
    lateinit var bank2: StartedNode<MockNetwork.MockNode>
    lateinit var bank3: StartedNode<MockNetwork.MockNode>
    lateinit var bank4: StartedNode<MockNetwork.MockNode>
    lateinit var bank5: StartedNode<MockNetwork.MockNode>
    lateinit var bank6: StartedNode<MockNetwork.MockNode>
    lateinit var bank7: StartedNode<MockNetwork.MockNode>
    lateinit var bank8: StartedNode<MockNetwork.MockNode>
    lateinit var bank9: StartedNode<MockNetwork.MockNode>
    lateinit var bank10: StartedNode<MockNetwork.MockNode>
    lateinit var bank11: StartedNode<MockNetwork.MockNode>
    lateinit var bank12: StartedNode<MockNetwork.MockNode>
    lateinit var bank13: StartedNode<MockNetwork.MockNode>
    lateinit var bank14: StartedNode<MockNetwork.MockNode>
    lateinit var bank15: StartedNode<MockNetwork.MockNode>
    lateinit var bank16: StartedNode<MockNetwork.MockNode>
    lateinit var bank17: StartedNode<MockNetwork.MockNode>

    @Before
    fun setup() {
        setCordappPackages(
                "net.corda.finance",
                "com.r3.demos.ubin2a.obligation",
                "com.r3.demos.ubin2a.detect",
                "com.r3.demos.ubin2a.cash"
        )

        net = MockNetwork(threadPerNode = true)
        val nodes = net.createSomeNodes(18)
        bank1 = nodes.partyNodes[0] // Mock company 2
        bank2 = nodes.partyNodes[1] // Mock company 3
        bank3 = nodes.partyNodes[2] // Mock company 4
        bank4 = nodes.partyNodes[3] // Mock company 5
        bank5 = nodes.partyNodes[4] // Mock company 6
        bank6 = nodes.partyNodes[5] // Mock company 7
        bank7 = nodes.partyNodes[6] // Mock company 8
        bank8 = nodes.partyNodes[7] // Mock company 9
        bank9 = nodes.partyNodes[8] // Mock company 10
        bank10 = nodes.partyNodes[9] // Mock company 11
        bank11 = nodes.partyNodes[10] // Mock company 12
        bank12 = nodes.partyNodes[11] // Mock company 13
        bank13 = nodes.partyNodes[12] // Mock company 14
        bank14 = nodes.partyNodes[13] // Mock company 15
        bank15 = nodes.partyNodes[14] // Mock company 16
        bank16 = nodes.partyNodes[15] // Mock company 17
        bank17 = nodes.partyNodes[16] // Mock company 18

        nodes.partyNodes.forEach {
            it.registerInitiatedFlow(IssueObligation.Responder::class.java)
            it.registerInitiatedFlow(ReceiveScanRequest::class.java)
            it.registerInitiatedFlow(ReceiveScanAcknowledgement::class.java)
            it.registerInitiatedFlow(ReceiveScanResponse::class.java)
            it.registerInitiatedFlow(SendKeyFlow::class.java)
            it.database.transaction {
                it.internals.installCordaService(PersistentObligationQueue::class.java)
                it.internals.installCordaService(TemporaryKeyManager::class.java)
            }
        }

        addCash()
    }

    @After
    fun tearDown() {
        net.stopNodes()
        unsetCordappPackages()
    }

    fun createObligation(lender: StartedNode<MockNetwork.MockNode>,
                         borrower: StartedNode<MockNetwork.MockNode>,
                         amount: Amount<Currency>): CordaFuture<SignedTransaction> {
        val flow = IssueObligation.Initiator(amount, lender.info.chooseIdentity())
        return borrower.services.startFlow(flow).resultFuture
    }

    private fun addCash() {
        // Issue cash.
        bank1.services.startFlow(SelfIssueCashFlow(100.DOLLARS)).resultFuture.getOrThrow()
        bank2.services.startFlow(SelfIssueCashFlow(100.DOLLARS)).resultFuture.getOrThrow()
        bank3.services.startFlow(SelfIssueCashFlow(100.DOLLARS)).resultFuture.getOrThrow()
    }

    @Test
    fun testOne() {
        // Issue obligations.
        createObligation(bank2, bank1, 10.DOLLARS).getOrThrow()
        createObligation(bank3, bank2, 20.DOLLARS).getOrThrow()
        createObligation(bank1, bank4, 25.DOLLARS).getOrThrow()
        createObligation(bank3, bank5, 50.DOLLARS).getOrThrow()
        createObligation(bank2, bank1, 30.DOLLARS).getOrThrow()
        createObligation(bank4, bank3, 40.DOLLARS).getOrThrow()
        createObligation(bank4, bank1, 200.DOLLARS).getOrThrow()
        createObligation(bank6, bank1, 400.DOLLARS).getOrThrow()
        createObligation(bank3, bank6, 100.DOLLARS).getOrThrow()
        net.waitQuiescent()
        // Start N concurrent scans where N = total number of nodes on the network.
        bank1.services.startFlow(DetectFlow(USD))
        bank2.services.startFlow(DetectFlow(USD))
        bank3.services.startFlow(DetectFlow(USD))
        bank4.services.startFlow(DetectFlow(USD))
        bank5.services.startFlow(DetectFlow(USD))
        bank6.services.startFlow(DetectFlow(USD))
        // Wait for all flows to finish. Don't remove this.
        net.waitQuiescent()
        // Print the results for each node.
        println(DataStore.scanRequest)
        println(DataStore.neighbours)
        println(DataStore.obligations)
        println(DataStore.limits)
    }

    @Test
    fun testTwo() {
        // Issue obligations.
        createObligation(bank1, bank2, 1500.DOLLARS)
        createObligation(bank2, bank16, 1000.DOLLARS)
        createObligation(bank3, bank2, 900.DOLLARS)
        createObligation(bank4, bank2, 2000.DOLLARS)
        createObligation(bank4, bank3, 2500.DOLLARS)
        createObligation(bank4, bank6, 1100.DOLLARS)
        createObligation(bank5, bank4, 700.DOLLARS)
        createObligation(bank5, bank6, 800.DOLLARS)
        createObligation(bank6, bank10, 1300.DOLLARS)
        createObligation(bank6, bank7, 1400.DOLLARS)
        createObligation(bank7, bank8, 1700.DOLLARS)
        createObligation(bank7, bank9, 1600.DOLLARS)
        createObligation(bank8, bank5, 1050.DOLLARS)
        createObligation(bank10, bank11, 950.DOLLARS)
        createObligation(bank11, bank7, 850.DOLLARS)
        createObligation(bank12, bank5, 980.DOLLARS)
        createObligation(bank12, bank8, 880.DOLLARS)
        createObligation(bank12, bank13, 1020.DOLLARS)
        createObligation(bank13, bank14, 220.DOLLARS)
        createObligation(bank13, bank15, 1310.DOLLARS)
        createObligation(bank14, bank15, 770.DOLLARS)
        createObligation(bank15, bank16, 660.DOLLARS)
        createObligation(bank16, bank12, 830.DOLLARS)
        createObligation(bank17, bank12, 430.DOLLARS)
        createObligation(bank17, bank15, 330.DOLLARS)
        net.waitQuiescent()
        // Start M concurrent scans where N = total number of nodes on the network and M < N.
        bank1.services.startFlow(DetectFlow(USD))
        bank2.services.startFlow(DetectFlow(USD))
        bank3.services.startFlow(DetectFlow(USD))
        bank4.services.startFlow(DetectFlow(USD))
        bank5.services.startFlow(DetectFlow(USD))
        bank6.services.startFlow(DetectFlow(USD))
        bank7.services.startFlow(DetectFlow(USD))
        bank8.services.startFlow(DetectFlow(USD))
        bank9.services.startFlow(DetectFlow(USD))
        // Wait for all flows to finish. Don't remove this.
        net.waitQuiescent()
        // Print the results for each node.
        println(DataStore.scanRequest)
        println(DataStore.neighbours)
        println(DataStore.obligations)
        println(DataStore.limits)
    }
}


