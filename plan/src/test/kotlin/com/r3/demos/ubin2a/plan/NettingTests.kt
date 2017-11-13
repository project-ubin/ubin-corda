package com.r3.demos.ubin2a.plan

import com.r3.demos.ubin2a.base.SGD
import com.r3.demos.ubin2a.obligation.Obligation
import com.r3.demos.ubin2a.plan.generator.NettingResult
import com.r3.demos.ubin2a.plan.graph.NodeSequence
import com.r3.demos.ubin2a.plan.solver.CycleResult
import net.corda.core.contracts.Amount
import net.corda.core.identity.AbstractParty
import java.util.*
import kotlin.test.assertEquals

open class NettingTests : NetworkTests() {

    val A = 1
    val B = 2
    val C = 3
    val D = 4
    val E = 5
    val F = 6
    val G = 7
    val H = 8
    val I = 9
    val J = 10
    val K = 11

    operator fun List<AbstractParty>.component6() = this[5]
    operator fun List<AbstractParty>.component7() = this[6]
    operator fun List<AbstractParty>.component8() = this[7]
    operator fun List<AbstractParty>.component9() = this[8]
    operator fun List<AbstractParty>.component10() = this[9]
    operator fun List<AbstractParty>.component11() = this[10]

    fun assertSequence(actual: NodeSequence<AbstractParty>, vararg expected: AbstractParty) {
        val expectedEdges = expected.zip(expected.drop(1)) { a, b -> Pair(a, b) }
        val foundEdges = actual.edges { a, b -> expectedEdges.contains(Pair(a, b)) }.toList()
        val isEqual = expectedEdges.size == foundEdges.size && foundEdges.all { it }
        if (!isEqual) {
            throw AssertionError("Expected ${printSequence(expected.toList())}, found ${printSequence(actual)}")
        }
    }

    fun applyPaymentsToLimits(
        limits: Map<AbstractParty, Amount<Currency>>,
        payments: List<NettingPayment>
    ): Map<AbstractParty, Amount<Currency>> {
        val newLimits = limits.toMutableMap()
        for ((from, to, amount) in payments) {
            newLimits.computeIfPresent(from, { _, limit -> limit - amount })
            newLimits.computeIfPresent(to, { _, limit -> limit + amount })
        }
        return newLimits
    }


    fun graph(numberOfNodes: Int, extendComponents: Boolean = false): GraphBuilder = GraphBuilder(getNodes(numberOfNodes), extendComponents)

    class GraphBuilder(private val nodes: List<AbstractParty>, private val extendComponents: Boolean) {
        private var limits: Map<AbstractParty, Amount<Currency>>? = null
        private val obligations = mutableListOf<Obligation.State>()

        fun withLimits(vararg limitValues: Long): GraphBuilder {
            limits = limitValues.mapIndexed({ index, value ->
                nodes[index] to value.SGD
            }).toMap()
            return this
        }

        fun withObligation(from: Int, to: Int, amount: Long): GraphBuilder {
            obligations.add(Obligation.State(amount.SGD, nodes[from - 1], nodes[to - 1]))
            return this
        }

        fun generate(): Graph {
            val (_, obligationsMap, cycles) = obligationsGraphFrom(obligations, extendComponents)
            return Graph(nodes, obligationsMap, cycles.flatMap { it }, limits.orEmpty())
        }
    }

    data class Graph(
        val nodes: List<AbstractParty>,
        val obligationsMap: ObligationsMap,
        val cycles: List<Cycle>,
        val limits: Map<AbstractParty, Amount<Currency>>
    )

    fun CycleResult.assertNewLimit(node: AbstractParty, amount: Int) {
        assertEquals(amount.SGD, this.newLimits[node])
    }

    fun CycleResult.assertSettled(lender: AbstractParty, borrower: AbstractParty, amount: Int) {
        assertEquals(1, this.settledObligations.count {
            it.lender == lender && it.borrower == borrower && it.amount == amount.SGD
        })
    }

    fun CycleResult.assertNotSettled(lender: AbstractParty, borrower: AbstractParty, amount: Int) {
        assertEquals(0, this.settledObligations.count {
            it.lender == lender && it.borrower == borrower && it.amount == amount.SGD
        })
    }

    fun List<Cycle>.find(vararg nodes: AbstractParty): Cycle {
        val nodeSet = nodes.toSet()
        return this.find { it.size == nodeSet.size + 1 && it.containsAll(nodeSet) }!!
    }

    fun NettingResult.assertHasPayment(fromNode: AbstractParty, toNode: AbstractParty, amount: Long) {
        val quantity = amount.SGD.quantity
        val matches = this.payments.filter {
            it.from == fromNode &&
                it.to == toNode &&
                it.amount.quantity == quantity
        }
        assertEquals(1, matches.count())
    }

    private fun printSequence(sequence: Iterable<AbstractParty>): String {
        return "{${sequence.joinToString(", ") { it.nameOrNull()!!.organisation }}}"
    }
}
