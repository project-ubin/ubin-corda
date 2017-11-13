package com.r3.demos.ubin2a.plan.graph

import org.junit.Test
import kotlin.test.assertEquals

class SimpleCycleDetectionTests {

    @Test
    fun `get cycles in component with two nodes`() {
        val cycles = detectSimpleCycles(stronglyConnectedComponentOf(
            Edge(1, 2),
            Edge(2, 1)
        ))

        assertHasSimpleCycle(cycles, 1, 2, 1)
        assertEquals(1, cycles.size)
    }

    @Test
    fun `get cycles in component with three nodes`() {
        val cycles = detectSimpleCycles(stronglyConnectedComponentOf(
            Edge(1, 2),
            Edge(2, 3),
            Edge(3, 1)
        ))

        assertHasSimpleCycle(cycles, 1, 2, 3, 1)
        assertEquals(1, cycles.size)
    }

    @Test
    fun `get cycles in component with five nodes`() {
        val cycles = detectSimpleCycles(stronglyConnectedComponentOf(
            Edge(1, 2), Edge(1, 3),
            Edge(2, 3), Edge(2, 3),
            Edge(3, 4), Edge(3, 5),
            Edge(4, 5), Edge(4, 1),
            Edge(5, 1), Edge(5, 2)
        ))

        assertHasSimpleCycle(cycles, 1, 2, 3, 4, 5, 1)
        assertHasSimpleCycle(cycles, 1, 2, 3, 4, 1)
        assertHasSimpleCycle(cycles, 1, 2, 3, 5, 1)
        assertHasSimpleCycle(cycles, 1, 3, 4, 5, 1)
        assertHasSimpleCycle(cycles, 1, 3, 5, 1)
        assertHasSimpleCycle(cycles, 1, 3, 4, 1)
        assertHasSimpleCycle(cycles, 2, 3, 5, 2)
        assertHasSimpleCycle(cycles, 2, 3, 4, 5, 2)
        assertEquals(8, cycles.size)
    }

    @Test
    fun `get cycles in component with six nodes`() {
        val cycles = detectSimpleCycles(stronglyConnectedComponentOf(
            Edge(1, 2), Edge(1, 5),
            Edge(2, 3),
            Edge(3, 1), Edge(3, 2), Edge(3, 4), Edge(3, 6),
            Edge(4, 5),
            Edge(5, 2),
            Edge(6, 4)
        ))

        assertHasSimpleCycle(cycles, 1, 2, 3, 1)
        assertHasSimpleCycle(cycles, 1, 5, 2, 3, 1)
        assertHasSimpleCycle(cycles, 2, 3, 2)
        assertHasSimpleCycle(cycles, 2, 3, 4, 5, 2)
        assertHasSimpleCycle(cycles, 2, 3, 6, 4, 5, 2)
        assertEquals(5, cycles.size)
    }

    private fun stronglyConnectedComponentOf(vararg edges: Edge<Int>): StronglyConnectedComponent<Int> {
        return detectStronglyConnectedComponents(DirectedGraph(edges.asList())).single()
    }

    private fun assertHasSimpleCycle(cycles: Set<Cycle<Int>>, vararg nodes: Int) {
        if (cycles.count { areIdenticalCycles(it, nodes.asList()) } == 0) {
            val expectedCycle = nodes.joinToString(", ")
            throw AssertionError("Simple cycle {$expectedCycle} not found")
        }
    }

    private fun <T> areIdenticalCycles(a: Cycle<T>, b: Cycle<T>): Boolean {
        if (a.size != b.size || a.toSet() != b.toSet()) {
            return false
        }

        val size = a.size - 1
        val delta = b.indexOf(a.first())

        for (i in 0..size) {
            val j = (i + delta) % size
            if (a[i] != b[j]) {
                return false
            }
        }

        return true
    }
}
