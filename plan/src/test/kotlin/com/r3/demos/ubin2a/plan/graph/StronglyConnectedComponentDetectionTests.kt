package com.r3.demos.ubin2a.plan.graph

import org.junit.Test
import kotlin.test.assertEquals

class StronglyConnectedComponentDetectionTests {

    @Test
    fun `get strongly connected components from graph with two nodes`() {
        val components = stronglyConnectedComponentsOf(
            Edge(1, 2),
            Edge(2, 1)
        )

        assertEquals(1, components.size)
        assertHasStronglyConnectedComponent(components, 1, 2)
    }

    @Test
    fun `get strongly connected components from graph with eight nodes`() {
        val components = stronglyConnectedComponentsOf(
            Edge(0, 1),
            Edge(2, 0),
            Edge(5, 2), Edge(5, 6),
            Edge(6, 5),
            Edge(1, 2),
            Edge(3, 1), Edge(3, 2), Edge(3, 4),
            Edge(4, 5), Edge(4, 3),
            Edge(7, 4), Edge(7, 7), Edge(7, 6)
        )

        assertEquals(4, components.size)
        assertHasStronglyConnectedComponent(components, 2, 1, 0)
        assertHasStronglyConnectedComponent(components, 6, 5)
        assertHasStronglyConnectedComponent(components, 4, 3)
        assertHasStronglyConnectedComponent(components, 7)
    }

    @Test
    fun `get strongly connected components from graph with sixteen nodes`() {
        val components = stronglyConnectedComponentsOf(
            Edge(1, 3),
            Edge(2, 1), Edge(2, 6),
            Edge(3, 2), Edge(3, 5),
            Edge(4, 3), Edge(4, 13),
            Edge(5, 4), Edge(5, 6), Edge(5, 14),
            Edge(6, 7), Edge(6, 9), Edge(6, 11),
            Edge(7, 8),
            Edge(8, 9), Edge(8, 10),
            Edge(9, 7), Edge(9, 10),
            Edge(11, 10), Edge(11, 12),
            Edge(12, 11),
            Edge(13, 14),
            Edge(14, 15), Edge(14, 16),
            Edge(15, 13),
            Edge(16, 15), Edge(16, 11)
        )

        assertEquals(5, components.size)
        assertHasStronglyConnectedComponent(components, 1, 2, 3, 4, 5)
        assertHasStronglyConnectedComponent(components, 6)
        assertHasStronglyConnectedComponent(components, 7, 8, 9)
        assertHasStronglyConnectedComponent(components, 11, 12)
        assertHasStronglyConnectedComponent(components, 13, 14, 15, 16)
    }

    @Test
    fun `test merging two strongly connected components`() {
        val components = stronglyConnectedComponentsOf(
            Edge(0, 1), Edge(1, 0),
            Edge(2, 3), Edge(3, 2)
        )

        assertEquals(2, components.size)

        val merged = components.first().merge(components.last())
        assertEquals(4, merged.nodes.size)
        assertEquals(listOf(0, 1, 2, 3), merged.nodes.sorted())
    }

    private fun assertHasStronglyConnectedComponent(components: List<StronglyConnectedComponent<Int>>, vararg nodes: Int) {
        if (components.count { it.nodes.count() == nodes.count() && it.nodes.containsAll(nodes.asList()) } == 0) {
            throw AssertionError("Strongly connected component [${nodes.joinToString(", ")}] not found")
        }
    }

    private fun stronglyConnectedComponentsOf(vararg edges: Edge<Int>): List<StronglyConnectedComponent<Int>> {
        return detectStronglyConnectedComponents(DirectedGraph(edges.asList()))
    }
}
