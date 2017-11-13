package com.r3.demos.ubin2a.plan.solver

import com.r3.demos.ubin2a.base.SGD
import com.r3.demos.ubin2a.obligation.Obligation
import com.r3.demos.ubin2a.plan.Edge
import com.r3.demos.ubin2a.plan.NettingTests
import com.r3.demos.ubin2a.plan.obligationsForEdge
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CycleSolverTests : NettingTests() {

    @Test
    fun `get cycles from obligations`() {
        val (nodes, obligationsMap, cycles) = getGraph()
        val (A, B, C, D, E) = nodes

        assertEquals(8, cycles.size) // Cycles within first SCC
        assertEquals(9, obligationsMap.size)

        // Spot check a few of the map entries
        assertEquals(1, obligationsMap.obligationsForEdge(A, B).size)
        assertEquals(2, obligationsMap.obligationsForEdge(B, C).size)
        assertEquals(1, obligationsMap.obligationsForEdge(C, D).size)
        assertEquals(1, obligationsMap.obligationsForEdge(D, E).size)
        assertEquals(1, obligationsMap.obligationsForEdge(E, A).size)
        assertEquals(0, obligationsMap.obligationsForEdge(D, C).size)
    }

    @Test
    fun `solve cycle {A, B, C, D, E}`() {
        val (nodes, obligationsMap, cycles, limits) = getGraph()
        val (A, B, C, D, E) = nodes

        val cycle = cycles.find(A, B, C, D, E)
        assertEquals(6, cycle.size)

        val cycleSolver: CycleSolver = CycleSolverImpl()
        val result = cycleSolver.solveCycle(cycle, obligationsMap, limits)

        result.assertNewLimit(A, 6)
        result.assertNewLimit(B, 3)
        result.assertNewLimit(C, 3)
        result.assertNewLimit(D, 5)
        result.assertNewLimit(E, 2)

        assertEquals(5, result.settledObligations.size)
        result.assertSettled(A, B, 5)
        result.assertSettled(B, C, 6)
        result.assertSettled(C, D, 8)
        result.assertSettled(D, E, 7)
        result.assertSettled(E, A, 8)
        result.assertNotSettled(B, C, 30)
    }

    @Test
    fun `solve cycle {A, B, C, D, A}`() {
        val (nodes, obligationsMap, cycles, limits) = getGraph()
        val (A, B, C, D, E) = nodes

        val cycle = cycles.find(A, B, C, D)
        assertEquals(5, cycle.size)

        val cycleSolver: CycleSolver = CycleSolverImpl()
        val result = cycleSolver.solveCycle(cycle, obligationsMap, limits)

        result.assertNewLimit(A, 3)
        result.assertNewLimit(B, 3)
        result.assertNewLimit(C, 3)
        result.assertNewLimit(D, 7)
        result.assertNewLimit(E, 3)

        assertEquals(4, result.settledObligations.size)
        result.assertSettled(A, B, 5)
        result.assertSettled(B, C, 6)
        result.assertSettled(C, D, 8)
        result.assertSettled(D, A, 5)
        result.assertNotSettled(B, C, 30)
    }

    @Test
    fun `solve cycle {A, B, C, E, A} - not solvable`() {
        val (nodes, obligationsMap, cycles, limits) = getGraph()
        val (A, B, C, _, E) = nodes

        val cycle = cycles.find(A, B, C, E)
        assertEquals(5, cycle.size)

        val cycleSolver: CycleSolver = CycleSolverImpl()
        val result = cycleSolver.solveCycle(cycle, obligationsMap, limits)

        assertEquals(0, result.settledObligations.size)
    }

    @Test
    fun `solve cycle {A, C, D, E, A}`() {
        val (nodes, obligationsMap, cycles, limits) = getGraph()
        val (A, B, C, D, E) = nodes

        val cycle = cycles.find(A, C, D, E)
        assertEquals(5, cycle.size)

        val cycleSolver: CycleSolver = CycleSolverImpl()
        val result = cycleSolver.solveCycle(cycle, obligationsMap, limits)

        result.assertNewLimit(A, 5)
        result.assertNewLimit(B, 4)
        result.assertNewLimit(C, 3)
        result.assertNewLimit(D, 5)
        result.assertNewLimit(E, 2)

        assertEquals(4, result.settledObligations.size)
        result.assertSettled(A, C, 6)
        result.assertSettled(C, D, 8)
        result.assertSettled(D, E, 7)
        result.assertSettled(E, A, 8)
    }

    @Test
    fun `solve cycle {A, C, E, A} - not solvable`() {
        val (nodes, obligationsMap, cycles, limits) = getGraph()
        val (A, _, C, _, E) = nodes

        val cycle = cycles.find(A, C, E)
        assertEquals(4, cycle.size)

        val cycleSolver: CycleSolver = CycleSolverImpl()
        val result = cycleSolver.solveCycle(cycle, obligationsMap, limits)

        assertEquals(0, result.settledObligations.size)
    }

    @Test
    fun `solve cycle {A, C, D, A}`() {
        val (nodes, obligationsMap, cycles, limits) = getGraph()
        val (A, B, C, D, E) = nodes

        val cycle = cycles.find(A, C, D)
        assertEquals(4, cycle.size)

        val cycleSolver: CycleSolver = CycleSolverImpl()
        val result = cycleSolver.solveCycle(cycle, obligationsMap, limits)

        result.assertNewLimit(A, 2)
        result.assertNewLimit(B, 4)
        result.assertNewLimit(C, 3)
        result.assertNewLimit(D, 7)
        result.assertNewLimit(E, 3)

        assertEquals(3, result.settledObligations.size)
        result.assertSettled(A, C, 6)
        result.assertSettled(C, D, 8)
        result.assertSettled(D, A, 5)
    }

    @Test
    fun `solve cycle {B, C, E, A} - not solvable`() {
        val (nodes, obligationsMap, cycles, limits) = getGraph()
        val (_, B, C, _, E) = nodes

        val cycle = cycles.find(B, C, E)
        assertEquals(4, cycle.size)

        val cycleSolver: CycleSolver = CycleSolverImpl()
        val result = cycleSolver.solveCycle(cycle, obligationsMap, limits)

        assertEquals(0, result.settledObligations.size)
    }

    @Test
    fun `solve cycle {B, C, D, E, A} - not solvable`() {
        val (nodes, obligationsMap, cycles, limits) = getGraph()
        val (_, B, C, D, E) = nodes

        val cycle = cycles.find(B, C, D, E)
        assertEquals(5, cycle.size)

        val cycleSolver: CycleSolver = CycleSolverImpl()
        val result = cycleSolver.solveCycle(cycle, obligationsMap, limits)

        assertEquals(0, result.settledObligations.size)
    }

    @Test
    fun `populate edge`() {
        val (nodes, obligationsMap, _, limits) = getGraph()
        val (_, B, C) = nodes
        val mutableLimits = HashMap(limits.map { Pair(it.key, it.value.quantity) }.toMap())
        val settledObligations = HashSet<Obligation.State>()

        val resNoObligation = populateEdge(B, C, emptyMap(), mutableLimits, settledObligations)
        assertFalse(resNoObligation.modified)
        assertFalse(resNoObligation.failed)

        assertFalse { populateEdge(B, C, obligationsMap, mutableLimits, settledObligations).failed }

        assertEquals(-3200, mutableLimits[B])
        assertEquals(4100, mutableLimits[C])
        assertEquals(2, settledObligations.size)
        assertEquals(1, settledObligations.count { it.lender == B && it.borrower == C && it.amount == 30.SGD })
        assertEquals(1, settledObligations.count { it.lender == B && it.borrower == C && it.amount == 6.SGD })

    }

    @Test
    fun `check edge`() {
        val (nodes, obligationsMap, _, limits) = getGraph()
        val (_, B, C) = nodes
        val mutableLimits = HashMap(limits.map { Pair(it.key, it.value.quantity) }.toMap())
        val settledObligations = HashSet<Obligation.State>(obligationsMap[Edge(B, C)])
        val allGoodRes = checkEdge(B, C, obligationsMap, mutableLimits, settledObligations)
        assertFalse(allGoodRes.modified)
        assertFalse(allGoodRes.failed)
        assertEquals(mutableLimits[B], 400)
        assertEquals(mutableLimits[C], 500)
        assertEquals(2, settledObligations.size)
        assertEquals(1, settledObligations.count { it.lender == B && it.borrower == C && it.amount == 30.SGD })
        assertEquals(1, settledObligations.count { it.lender == B && it.borrower == C && it.amount == 6.SGD })

        // make it unsolvable - make sure nothing is changed
        mutableLimits[B] = -3200
        mutableLimits[C] = 4100
        val removedAllRes = checkEdge(B, C, obligationsMap, mutableLimits, settledObligations)
        assertFalse(removedAllRes.modified)
        assertTrue(removedAllRes.failed)
        assertEquals(-3200, mutableLimits[B])
        assertEquals(4100, mutableLimits[C])
        assertEquals(2, settledObligations.size)
        assertEquals(1, settledObligations.count { it.lender == B && it.borrower == C && it.amount == 30.SGD })
        assertEquals(1, settledObligations.count { it.lender == B && it.borrower == C && it.amount == 6.SGD })

        mutableLimits[B] = -2000
        val removed1Res = checkEdge(B, C, obligationsMap, mutableLimits, settledObligations)
        assertTrue(removed1Res.modified)
        assertFalse(removed1Res.failed)
        assertEquals(1000, mutableLimits[B])
        assertEquals(1100, mutableLimits[C])
        assertEquals(1, settledObligations.size)
        assertEquals(1, settledObligations.count { it.lender == B && it.borrower == C && it.amount == 6.SGD })
    }

    @Test
    fun `check edge re-add`() {
        val (nodes, obligationsMap, _, limits) =
            graph(2)
                .withLimits(3, 5)
                .withObligation(A, B, 5)
                .withObligation(A, B, 30)
                .withObligation(A, B, 3)
                .withObligation(B, A, 5)
                .generate()
        val (A, B) = nodes

        val mutableLimits = HashMap(limits.map { Pair(it.key, it.value.quantity) }.toMap())
        val settledObligations = HashSet<Obligation.State>(obligationsMap[Edge(A, B)])

        mutableLimits[A] = -1000

        val removed1Res = checkEdge(A, B, obligationsMap, mutableLimits, settledObligations)
        assertTrue(removed1Res.modified)
        assertFalse(removed1Res.failed)
        assertEquals(2000, mutableLimits[A])
        assertEquals(-2500, mutableLimits[B])
        assertEquals(2, settledObligations.size)
        assertEquals(1, settledObligations.count { it.lender == A && it.borrower == B && it.amount == 5.SGD })
        assertEquals(1, settledObligations.count { it.lender == A && it.borrower == B && it.amount == 3.SGD })
    }

    @Test
    fun `check edge second round`() {
        val (nodes, obligationsMap, _, limits) =
            graph(2)
                .withLimits(3, 5)
                .withObligation(A, B, 5)
                .withObligation(A, B, 30)
                .withObligation(A, B, 3)
                .withObligation(B, A, 5)
                .generate()
        val (A, B) = nodes

        val mutableLimits = HashMap(limits.map { Pair(it.key, it.value.quantity) }.toMap())
        val settledObligations = HashSet<Obligation.State>(obligationsMap[Edge(A, B)]!!.filter { it.amount != 30.SGD })
        assertEquals(settledObligations.size, 2)

        mutableLimits[A] = -(4.SGD.quantity)

        val removed1Res = checkEdge(A, B, obligationsMap, mutableLimits, settledObligations)
        assertTrue(removed1Res.modified)
        assertFalse(removed1Res.failed)
        assertEquals(100, mutableLimits[A])
        assertEquals(0, mutableLimits[B])
        assertEquals(1, settledObligations.size)
        assertEquals(1, settledObligations.count { it.lender == A && it.borrower == B && it.amount == 3.SGD })
    }

    @Test
    fun `loop cycle`() {
        val cycle = listOf(1, 2, 3, 4, 1)
        val record = mutableListOf<Pair<Int, Int>>()

        assertTrue(loopCycle(cycle, { c, n ->
            EdgeResult(record.add(Pair(c, n)), false)
        }).modified)
        assertEquals(record, listOf(Pair(1, 2), Pair(2, 3), Pair(3, 4), Pair(4, 1)))

        var called = 0
        var result = loopCycle(cycle, { c, _ -> ++called; EdgeResult(false, c == 2) })
        assertEquals(called, 2)
        assertTrue(result.failed)

        called = 0
        result = loopCycle(cycle, { c, _ -> ++called; EdgeResult(c == 2, false) })
        assertEquals(called, 4)
        assertFalse(result.failed)
        assertTrue(result.modified)
    }

    private fun getGraph(): Graph {
        return graph(5)
            .withLimits(3, 4, 5, 4, 3)
            .withObligation(A, B, 5)
            .withObligation(B, C, 6)
            .withObligation(B, C, 30)
            .withObligation(C, D, 8)
            .withObligation(C, E, 80)
            .withObligation(D, E, 7)
            .withObligation(A, C, 6)
            .withObligation(E, A, 8)
            .withObligation(E, B, 100)
            .withObligation(D, A, 5)
            .generate()
    }

}
