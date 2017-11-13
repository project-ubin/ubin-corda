package com.r3.demos.ubin2a.plan.solver

import com.r3.demos.ubin2a.base.SGD
import com.r3.demos.ubin2a.plan.Cycle
import com.r3.demos.ubin2a.plan.Edge
import com.r3.demos.ubin2a.plan.NettingTests
import com.r3.demos.ubin2a.plan.Obligations
import net.corda.core.contracts.Amount
import net.corda.core.identity.AbstractParty
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

class ComponentCycleSolverTests : NettingTests() {

    private data class CycleSolvingRun(val result: CycleResult, val expectedObligations: Map<Edge, Obligations>, val expectedLimits: Map<AbstractParty, Amount<Currency>>)

    private class MockCycleSolver(private val results: List<CycleSolvingRun>) : CycleSolver {
        override fun solveCycle(cycle: Cycle, obligations: Map<Edge, Obligations>, limits: Map<AbstractParty, Amount<Currency>>): CycleResult {
            if (counter > results.size) {
                throw IndexOutOfBoundsException("solve cycle called more often than expected")
            }
            assertEquals(results[counter].expectedObligations, obligations)
            assertEquals(results[counter].expectedLimits, limits)
            return results[counter++].result
        }

        var counter = 0
    }

    @Test
    fun `Solve one simple cycle`() {
        val (nodes, obligationsMap, cycles, limits) =
            graph(2)
                .withLimits(3, 5)
                .withObligation(A, B, 5)
                .withObligation(B, A, 5)
                .generate()
        val (A, B) = nodes
        val cycleSolver = MockCycleSolver(
            listOf(CycleSolvingRun(CycleResult(listOf(obligationsMap[Edge(A, B)]!!.first(), obligationsMap[Edge(B, A)]!!.first()), limits),
                obligationsMap, limits),
                CycleSolvingRun(CycleResult(emptyList(), limits), emptyMap(), limits)))
        val componentCycleSolver: ComponentCycleSolver = ComponentCycleSolverImpl(cycleSolver)
        val result = componentCycleSolver.solveCycles(cycles, obligationsMap, limits)
        assertEquals(result.newLimits, limits)
        result.assertSettled(A, B, 5)
        result.assertSettled(B, A, 5)
        assertEquals(cycleSolver.counter, 2)
    }

    @Test
    fun `Two cycles, both can be settled`() {
        val (nodes, obligationsMap, cycles, limits) =
            graph(3)
                .withLimits(3, 5)
                .withObligation(A, B, 5)
                .withObligation(B, A, 5)
                .withObligation(B, C, 4)
                .withObligation(C, B, 3)
                .generate()
        val (A, B, C) = nodes
        val limitsAfter2ndSettlement = mapOf(Pair(A, 5.SGD), Pair(B, 3.SGD), Pair(C, 4.SGD))
        val mapAfterFirstSettlement = obligationsMap.filter { it.key !in listOf(Edge(A, B), Edge(B, A)) }.toMap()

        val cycleSolver = MockCycleSolver(
            listOf(CycleSolvingRun(CycleResult(listOf(obligationsMap[Edge(A, B)]!!.first(), obligationsMap[Edge(B, A)]!!.first()), limits),
                obligationsMap, limits),
                CycleSolvingRun(CycleResult(listOf(obligationsMap[Edge(B, C)]!!.first(), obligationsMap[Edge(C, B)]!!.first()), limitsAfter2ndSettlement),
                    mapAfterFirstSettlement, limits),
                CycleSolvingRun(CycleResult(emptyList(), limitsAfter2ndSettlement), emptyMap(), limitsAfter2ndSettlement),
                CycleSolvingRun(CycleResult(emptyList(), limitsAfter2ndSettlement), emptyMap(), limitsAfter2ndSettlement)))
        val componentCycleSolver: ComponentCycleSolver = ComponentCycleSolverImpl(cycleSolver)
        val result = componentCycleSolver.solveCycles(cycles, obligationsMap, limits)
        assertEquals(result.newLimits, limitsAfter2ndSettlement)
        result.assertSettled(A, B, 5)
        result.assertSettled(B, A, 5)
        result.assertSettled(B, C, 4)
        result.assertSettled(C, B, 3)
        assertEquals(cycleSolver.counter, 4)
    }

    @Test
    fun `Two circles, both can be settled, but the first only after the second has been settled`() {
        val (nodes, obligationsMap, cycles, limits) =
            graph(3)
                .withLimits(5, 4, 3)
                .withObligation(A, B, 5)
                .withObligation(B, A, 5)
                .withObligation(B, C, 4)
                .withObligation(C, B, 3)
                .generate()
        val (A, B, C) = nodes
        val limitsAfter1stSettlement = mapOf(Pair(A, 5.SGD), Pair(B, 3.SGD), Pair(C, 4.SGD))
        val mapAfterFirstSettlement = obligationsMap.filter { it.key !in listOf(Edge(C, B), Edge(B, C)) }.toMap()


        val cycleSolver = MockCycleSolver(
            listOf(CycleSolvingRun(CycleResult(emptyList(), limits), obligationsMap, limits),
                CycleSolvingRun(CycleResult(listOf(obligationsMap[Edge(B, C)]!!.first(), obligationsMap[Edge(C, B)]!!.first()), limitsAfter1stSettlement),
                    obligationsMap, limits),
                CycleSolvingRun(CycleResult(listOf(obligationsMap[Edge(A, B)]!!.first(), obligationsMap[Edge(B, A)]!!.first()), limitsAfter1stSettlement),
                    mapAfterFirstSettlement, limitsAfter1stSettlement),
                CycleSolvingRun(CycleResult(emptyList(), limitsAfter1stSettlement), emptyMap(), limitsAfter1stSettlement),
                CycleSolvingRun(CycleResult(emptyList(), limitsAfter1stSettlement), emptyMap(), limitsAfter1stSettlement),
                CycleSolvingRun(CycleResult(emptyList(), limitsAfter1stSettlement), emptyMap(), limitsAfter1stSettlement)))
        val componentCycleSolver: ComponentCycleSolver = ComponentCycleSolverImpl(cycleSolver)
        val result = componentCycleSolver.solveCycles(cycles, obligationsMap, limits)
        assertEquals(result.newLimits, limitsAfter1stSettlement)
        result.assertSettled(A, B, 5)
        result.assertSettled(B, A, 5)
        result.assertSettled(B, C, 4)
        result.assertSettled(C, B, 3)
        assertEquals(cycleSolver.counter, 6)
    }

    @Test

    fun `Two cycles, only once can be settled`() {
        val (nodes, obligationsMap, cycles, limits) =
            graph(3)
                .withLimits(5, 4, 3)
                .withObligation(A, B, 5)
                .withObligation(B, A, 5)
                .withObligation(B, C, 4)
                .withObligation(C, B, 3)
                .generate()
        val (A, B, C) = nodes
        val limitsAfter1stSettlment = mapOf(Pair(A, 5.SGD), Pair(B, 3.SGD), Pair(C, 4.SGD))
        val cycleSolver = MockCycleSolver(
            listOf(CycleSolvingRun(CycleResult(emptyList(), limits), obligationsMap, limits),
                CycleSolvingRun(CycleResult(listOf(obligationsMap[Edge(B, C)]!!.first(), obligationsMap[Edge(C, B)]!!.first()), limitsAfter1stSettlment),
                    obligationsMap, limits),
                CycleSolvingRun(CycleResult(emptyList(), limitsAfter1stSettlment), obligationsMap.filter { it.key !in listOf(Edge(C, B), Edge(B, C)) }.toMap(), limitsAfter1stSettlment),
                CycleSolvingRun(CycleResult(emptyList(), limitsAfter1stSettlment), obligationsMap.filter { it.key !in listOf(Edge(C, B), Edge(B, C)) }.toMap(), limitsAfter1stSettlment)))
        val componentCycleSolver: ComponentCycleSolver = ComponentCycleSolverImpl(cycleSolver)
        val result = componentCycleSolver.solveCycles(cycles, obligationsMap, limits)
        assertEquals(result.newLimits, limitsAfter1stSettlment)
        result.assertSettled(B, C, 4)
        result.assertSettled(C, B, 3)
        assertEquals(cycleSolver.counter, 4)
    }

    @Test
    fun `Solve for sample component using real CycleSolverImpl`() {
        val (nodes, obligationsMap, cycles, limits) = getGraph()
        val (A, B, C, D, E) = nodes

        val componentCycleSolver = ComponentCycleSolverImpl()
        val result = componentCycleSolver.solveCycles(cycles, obligationsMap, limits)

        assertEquals(7, result.settledObligations.size)
        result.assertSettled(A, B, 5)
        result.assertSettled(B, C, 6)
        result.assertSettled(C, D, 8)
        result.assertSettled(D, E, 7)
        result.assertSettled(E, A, 8)
        result.assertSettled(A, C, 6)
        result.assertSettled(D, A, 5)

        result.assertNewLimit(A, 5)
        result.assertNewLimit(B, 3)
        result.assertNewLimit(C, 9)
        result.assertNewLimit(D, 0)
        result.assertNewLimit(E, 2)
    }

    @Test
    fun `Largest settled sum cycle only`() {
        val (nodes, obligationsMap, cycles, limits) = getGraph()
        val (A, B, C, D, E) = nodes

        val componentCycleSolver = LargestSumCycleOnlySolverImpl()
        val result = componentCycleSolver.solveCycles(cycles, obligationsMap, limits)

        assertEquals(5, result.settledObligations.size)
        result.assertSettled(A, B, 5)
        result.assertSettled(B, C, 6)
        result.assertSettled(C, D, 8)
        result.assertSettled(D, E, 7)
        result.assertSettled(E, A, 8)

        result.assertNewLimit(A, 6)
        result.assertNewLimit(B, 3)
        result.assertNewLimit(C, 3)
        result.assertNewLimit(D, 5)
        result.assertNewLimit(E, 2)

    }

    @Test
    fun `Extend across two components`() {
        val (nodes, obligationsMap, cycles, limits) = graph(4, true)
            .withLimits(4, 5, 2, 5)
            .withObligation(A, B, 5)
            .withObligation(B, A, 4)
            .withObligation(B, C, 2)
            .withObligation(C, D, 6)
            .withObligation(D, C, 3)
            .generate()
        val (A, B, C, D) = nodes

        val componentCycleSolver = ComponentCycleSolverImpl()
        val result = componentCycleSolver.solveCycles(cycles, obligationsMap, limits)

        assertEquals(5, result.settledObligations.size)
        result.assertSettled(A, B, 5)
        result.assertSettled(B, C, 2)
        result.assertSettled(C, D, 6)
        result.assertSettled(D, C, 3)
        result.assertSettled(B, A, 4)

        result.assertNewLimit(A, 3)
        result.assertNewLimit(B, 4)
        result.assertNewLimit(C, 1)
        result.assertNewLimit(D, 8)
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
