package com.r3.demos.ubin2a.privacy

import com.google.common.util.concurrent.UncheckedExecutionException
import com.r3.demos.ubin2a.base.*
import com.r3.demos.ubin2a.cash.AcceptPayment
import com.r3.demos.ubin2a.pledge.ApprovePledge
import com.r3.demos.ubin2a.cash.Pay
import com.r3.demos.ubin2a.obligation.GetQueue
import com.r3.demos.ubin2a.obligation.IssueObligation
import com.r3.demos.ubin2a.obligation.Obligation
import com.r3.demos.ubin2a.obligation.PersistentObligationQueue
import com.r3.demos.ubin2a.ubin2aTestHelpers.createObligation
import com.r3.demos.ubin2a.ubin2aTestHelpers.getCashStates
import com.r3.demos.ubin2a.ubin2aTestHelpers.getVerifiedTransaction
import com.r3.demos.ubin2a.ubin2aTestHelpers.printObligations
import net.corda.core.contracts.Amount
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.AnonymousParty
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

class PrivacyTests {
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
        it.database.transaction {
            it.internals.installCordaService(PersistentObligationQueue::class.java)
        }
    }

    private fun buildNetwork() {
        val sgd = java.util.Currency.getInstance("SGD")
        println("Central bank issuing coins to banks")
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

    //TODO: Descoped from ubin2a
//    @Test
//    fun `Regulator can receive verified transaction`(){
//        println("----------------------")
//        println("Test Regulator can receive verified transaction:")
//        println("----------------------")
//        val sgd = SGD
//        printCashBalances()
//        println()
//
//        // Send money to counter party
//        println("Bank1 sends 300 to Bank2")
//        val flow = Pay(bank2.info.chooseIdentity(), Amount(30000, sgd), OBLIGATION_PRIORITY.NORMAL.ordinal)
//        val stx = bank1.services.startFlow(flow).resultFuture.getOrThrow()
//        println(stx.tx.toString())
//        net.waitQuiescent()
//        printCashBalances()
//        println()
//
//        // Confirm balance of the banks reflected the funds transfer
//        val verifiedTxId = stx.tx.id
//        val balance1 = bank1.database.transaction { bank1.services.getCashBalance(sgd) }
//        val balance2 = bank2.database.transaction { bank2.services.getCashBalance(sgd) }
//        assert(balance1.quantity / 100 == 700L)
//        assert(balance2.quantity / 100 == 1300L)
//
//        // Confirm regulator recevied the TX in vault
//        val copiedTx = getVerifiedTransaction(regulator,verifiedTxId)
//        assert(copiedTx.tx.id == verifiedTxId)
//
//        // Confirm the TX has the exact same output as the tx initiated by Bank 1 to Bank 2
//        val output = stx.tx.outputs.first().data as Cash.State
//        val copiedOutput = copiedTx.tx.outputs.first().data as Cash.State
//        assert(output.owner == copiedOutput.owner)
//        assert(output.amount.quantity==copiedOutput.amount.quantity)
//    }

    @Test
    fun `Non-participant cannot view transaction`(){
        println("----------------------")
        println("Test Non-participant cannot view transaction:")
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

        // Confirm balances of the banks reflected the funds transfer
        val verifiedTxId = stx.tx.id
        val balance1 = bank1.database.transaction { bank1.services.getCashBalance(sgd) }
        val balance2 = bank2.database.transaction { bank2.services.getCashBalance(sgd) }
        assert(balance1.quantity / 100 == 700L)
        assert(balance2.quantity / 100 == 1300L)

        // Confirm that bank3 is not involved in the transaction and should not receive the transaction
        assertFailsWith<UncheckedExecutionException>("No transaction in context.") {
            getVerifiedTransaction(bank3,verifiedTxId)
        }

        // Confirm that bank3 cannot infer who the signers are of the transaction
        val signer = stx.tx.commands.first().signers.single()
        val maybeDecoded = bank3.database.transaction { bank3.services.identityService.partyFromKey(signer) }?: 0
        println("maybeDecoded: " + maybeDecoded)
        assert(maybeDecoded == 0)
    }

    @Test
    fun `Privacy of owner in cash states`(){
        println("----------------------")
        println("Test Privacy of owner in cash states:")
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
        val me = bank1.services.myInfo.chooseIdentity()
        val verifiedTxId = stx.tx.id
        val balance1 = bank1.database.transaction { bank1.services.getCashBalance(sgd) }
        val balance2 = bank2.database.transaction { bank2.services.getCashBalance(sgd) }

        // Confirm that bank2 did indeed received the tx
        val stx2 = getVerifiedTransaction(bank2,verifiedTxId)

        // Confirm that bank3 did not receive the transaction
        assertFailsWith<UncheckedExecutionException>("No transaction in context.") {
            getVerifiedTransaction(bank3,verifiedTxId)
        }

        // Confirm that during the splitting of cash states, the new owner (otherParty) of the transferred cash is anonymous
        // And the owner (me) of remaining cash is still anonymous as well, will throw exception if not AbstractParty(anonymous)
        val otherCash = stx2.tx.outputs.filter { (data) ->
            val cash = data as Cash.State
            bank1.services.identityService.requireWellKnownPartyFromAnonymous(cash.owner) != me
        }.single().data as Cash.State
        val myCash = stx2.tx.outputs.filter { (data) ->
            val cash = data as Cash.State
            bank1.services.identityService.requireWellKnownPartyFromAnonymous(cash.owner) == me
        }.single().data as Cash.State

        // Confirm that the cash state for both parties are anonymous parties
        assert(otherCash.owner is AnonymousParty)
        assert(myCash.owner is AnonymousParty)

        // Confirm only parties involved in the transaction can infer the owner of the state
        // Both banks should be able to infer the identity of the new owner of the transferred cash
        assert(bank1.services.identityService.wellKnownPartyFromAnonymous(otherCash.owner) != null)
        assert(bank2.services.identityService.wellKnownPartyFromAnonymous(otherCash.owner) != null)

        // Only the owner of the remaining cash should be able to infer the identity of the owner of the remaining cash
        assert(bank1.services.identityService.wellKnownPartyFromAnonymous(myCash.owner) != null)

        // Counter party shouldnt even know the owner of the remaining balance
        assertFailsWith<UncheckedExecutionException>("No transaction in context.") {
            bank2.services.identityService.requireWellKnownPartyFromAnonymous(myCash.owner)
        }

        // Non partipants cannot infer the identities of the owners
        assertFailsWith<UncheckedExecutionException>("No transaction in context.") {
            bank3.services.identityService.requireWellKnownPartyFromAnonymous(otherCash.owner)
        }

        assertFailsWith<UncheckedExecutionException>("No transaction in context.") {
            bank3.services.identityService.requireWellKnownPartyFromAnonymous(myCash.owner)
        }

        // Confirm the balance of the banks reflected the funds transfer
        assert(balance1.quantity / 100 == 700L)
        assert(balance2.quantity / 100 == 1300L)
    }

    @Test
    fun `Privacy of account balance`(){
        println("----------------------")
        println("Test Privacy of account balance:")
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

        // Confirm the balances
        val balance1 = bank1.database.transaction { bank1.services.getCashBalance(sgd) }
        val balance2 = bank2.database.transaction { bank2.services.getCashBalance(sgd) }
        val balance3 = bank3.database.transaction { bank3.services.getCashBalance(sgd) }
        assert(balance1.quantity / 100 == 700L)
        assert(balance2.quantity / 100 == 1300L)
        assert(balance3.quantity / 100 == 1000L)

        // Confirm bank3 should not be able to have states with cash owner != bank3
        val cashStates = getCashStates(bank3,sgd)
        val peekBank1 = cashStates.filter { it.state.data.owner == bank1 }
        val peekBank2 = cashStates.filter { it.state.data.owner == bank2 }
        assert(peekBank1.isEmpty())
        assert(peekBank2.isEmpty())
    }

    @Test
    fun `Anonymity in multiple cash transfer handshakes`() {
        println("----------------------")
        println("Test Anonymity in multiple cash transfer handshakes:")
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
        stx.tx.commands.forEach {println("TX 1 A-B Signers: " + it.signers.map{it.toStringShort()})}

        val signer1 = stx.tx.commands.single().signers.single()
        val me = bank1.services.myInfo.chooseIdentity()

        // Confirm the signing key is not my usual pubkey
        println("signer: " + signer1.toStringShort() )
        println("me.owningKey: " + me.owningKey.toStringShort() )
        assert(signer1 != me.owningKey)

        // Confirm the signing key when derived using identityService from Anonymous is still equal to me
        val maybeMe = bank1.services.identityService.partyFromKey(signer1)!!
        println("maybeMe " + maybeMe)
        println("me " + me)
        stx.tx.commands.forEach {println("TX 1 A-B Signers: " + it.signers.map{it.toStringShort()})}
        assert(me.name == maybeMe.name)

        // Send money to next bank
        println("Bank2 sends 1300 to Bank3")
        val flow2 = Pay(bank3.info.chooseIdentity(), Amount(130000, sgd), OBLIGATION_PRIORITY.NORMAL.ordinal)
        val stx2 = bank2.services.startFlow(flow2).resultFuture.getOrThrow()
        println(stx2.tx.toString())
        net.waitQuiescent()
        printCashBalances()
        println()

        val balance1 = bank1.database.transaction { bank1.services.getCashBalance(sgd) }
        val balance2 = bank2.database.transaction { bank2.services.getCashBalance(sgd) }
        val balance3 = bank3.database.transaction { bank3.services.getCashBalance(sgd) }

        // Confirm balances reflected the funds transfer
        assert(balance1.quantity / 100 == 700L)
        assert(balance2.quantity / 100 == 0L)
        assert(balance3.quantity / 100 == 2300L)

        val signers = stx2.tx.commands.single().signers
        val me2 = bank2.services.myInfo.chooseIdentity()

        // Confirm the signing key is not my usual pubkey
        signers.forEach{ println("signers: " + it.toStringShort()) }
        println("me2.owningKey: " + me2.owningKey.toStringShort() )
        assert(signers.first() != me2.owningKey)
        assert(signers.last() != me2.owningKey)

        // Confirm the signing key when derived using identityService from Anonymous is still equal to me
        val maybeMe2_first = bank2.services.identityService.partyFromKey(signers.first())!!
        val maybeMe2_last = bank2.services.identityService.partyFromKey(signers.last())!!
        println("maybeMe2_first " + maybeMe2_first)
        println("maybeMe2_last " + maybeMe2_last)
        println("me " + me)
        assert(me2.name == maybeMe2_first.name)
        assert(me2.name == maybeMe2_last.name)

        stx.tx.commands.forEach {println("TX 1 A-B Signers: " + it.signers.map{it.toStringShort()})}
        stx2.tx.commands.forEach {println("TX 2 B-C Signers: " +  it.signers.map{it.toStringShort()})}
    }

    @Test
    fun `can Verify Transaction Privacy in Queue`() {
        val currency = SGD
        // Create obligation between 1 and 2.
        println("Bank1 issues obligation of 5000 to bank2")
        val fut1 = createObligation(bank2, bank1, Amount(500000, currency), 1)
        val tx = fut1.getOrThrow()
        val state = tx.tx.outputStates.single() as Obligation.State
        println(state.toString())
        net.waitQuiescent()
        printObligations(bank1)

        val queryResult = bank1.services.startFlow(GetQueue.OutgoingById(state.linearId)).resultFuture.getOrThrow()
        val getObligation = queryResult.first()
        println("state " + state)
        println("getObligation" + getObligation)
        assert(queryResult.first().status == OBLIGATION_STATUS.ACTIVE.ordinal)
        val sender = queryResult.first().sender
        val receiver = queryResult.first().receiver

        // Confirm only parties involved in the transaction can infer the owner of the state
        assert(bank1.services.identityService.partyFromKey(sender) != null)
        assert(bank1.services.identityService.partyFromKey(receiver) != null)
        assert(bank2.services.identityService.partyFromKey(sender) != null)
        assert(bank2.services.identityService.partyFromKey(receiver) != null)

        // Non participants cannot infer the sender and receiver in the queue using the owningKey without the certs
        assertFailsWith<UncheckedExecutionException>("No transaction in context.") {
            bank3.services.identityService.partyFromKey(sender) }
        assertFailsWith<UncheckedExecutionException>("No transaction in context.") {
            bank3.services.identityService.partyFromKey(receiver) }

        val balance1 = bank1.database.transaction { bank1.services.getCashBalance(sgd) }
        val balance2 = bank2.database.transaction { bank2.services.getCashBalance(sgd) }
        val balance3 = bank3.database.transaction { bank3.services.getCashBalance(sgd) }
        // Confirm the balance of the banks reflected the funds transfer
        assert(balance1.quantity / 100 == 1000L)
        assert(balance2.quantity / 100 == 1000L)
        assert(balance3.quantity / 100 == 1000L)

        // Confirm that bank3 did not receive the transaction
        assertFailsWith<UncheckedExecutionException>("No transaction in context.") {
            getVerifiedTransaction(bank3,tx.id)
        }
    }



}