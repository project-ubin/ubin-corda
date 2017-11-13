package com.r3.demos.ubin2a.plan

import com.r3.demos.ubin2a.base.SGD
import com.r3.demos.ubin2a.obligation.Obligation
import net.corda.core.contracts.Amount
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.node.internal.StartedNode
import net.corda.testing.node.MockNetwork
import org.junit.After
import org.junit.Before
import java.util.*

open class NetworkTests {

    private lateinit var net: MockNetwork
    lateinit var bank1: StartedNode<MockNetwork.MockNode>

    @Before
    fun setup() {
        net = MockNetwork(threadPerNode = true)
        val nodes = net.createSomeNodes(1)
        bank1 = nodes.partyNodes[0]
    }

    @After
    fun tearDown() {
        net.stopNodes()
    }

    fun getNodes(numberOfNodes: Int): List<Party> {
        val nodes = mutableListOf<Party>()
        for (i in 1..numberOfNodes) {
            val nodeLetter = 'A' + i - 1
            val key = bank1.services.keyManagementService.freshKey()
            val node = Party(CordaX500Name("Party$nodeLetter", "London", "GB"), key)
            nodes.add(node)
        }
        return nodes
    }

    fun obligation(from: AbstractParty, to: AbstractParty, amount: Long): DecoratedObligation {
        return DecoratedObligation(amount.SGD, from, to)
    }

    class DecoratedObligation(
        private val amount: Amount<Currency>,
        private val fromParty: AbstractParty,
        private val toParty: AbstractParty
    ) {
        val state = Obligation.State(amount, fromParty, toParty)

        override fun toString(): String {
            return "Obligation(" +
                "from=${fromParty.nameOrNull()!!.organisation}, " +
                "to=${toParty.nameOrNull()!!.organisation}, " +
                "amount=$amount, " +
                "id=${state.linearId})"
        }
    }

    fun setOfObligations(vararg obligations: DecoratedObligation): Set<Obligation.State> {
        return obligations.map { it.state }.toSet()
    }
}