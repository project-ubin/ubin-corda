package com.r3.demos.ubin2a.plan.graph

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestExtendComponents {
    @Test
    fun `simply connect two components`() {
        // 0 <=> 1 -> 2 <=> 3 should be transformed to  0 <=> 1 <=> 2 <=> 3
        val components = stronglyConnectedComponentsOf(
            Edge(0, 1), Edge(1, 0),
            Edge(1, 2),
            Edge(2, 3), Edge(3, 2)
        )

        val result = extendComponents(components)
        assertEquals(1, result.size)
        assertEquals(listOf(0, 1, 2, 3), result.first().nodes.sorted())
        assertEquals(listOf(0, 2), result.first().graph.edgesForNode(1).map { it.id }.sorted())
        assertEquals(listOf(1), result.first().graph.edgesForNode(0).map { it.id }.sorted())
        assertEquals(listOf(1, 3), result.first().graph.edgesForNode(2).map { it.id }.sorted())
        assertEquals(listOf(2), result.first().graph.edgesForNode(3).map { it.id }.sorted())
    }

    @Test
    fun `link 3 components, then later add a fourth`() {
        val components = stronglyConnectedComponentsOf(
            Edge(0, 1), Edge(1, 0),
            Edge(1, 2),
            Edge(2, 3), Edge(3, 2),
            Edge(3, 4),
            Edge(4, 5), Edge(5, 4),
            Edge(6, 4),
            Edge(7, 8)
        )

        assertEquals(components.size, 5)

        val result = extendComponents(components)
        assertEquals(2, result.size)
        assertTrue(listOf(0, 1, 2, 3, 4, 5, 6) in result.map { it.nodes.sorted() })
        assertTrue(listOf(7, 8) in result.map { it.nodes.sorted() })

        assertEquals(listOf(0, 2), result.first().graph.edgesForNode(1).map { it.id }.sorted())
        assertEquals(listOf(1), result.first().graph.edgesForNode(0).map { it.id }.sorted())
        assertEquals(listOf(1, 3), result.first().graph.edgesForNode(2).map { it.id }.sorted())
        assertEquals(listOf(2, 4), result.first().graph.edgesForNode(3).map { it.id }.sorted())
        assertEquals(listOf(3, 5, 6), result.first().graph.edgesForNode(4).map { it.id }.sorted())
        assertEquals(listOf(4), result.first().graph.edgesForNode(5).map { it.id }.sorted())
        assertEquals(listOf(4), result.first().graph.edgesForNode(6).map { it.id }.sorted())
        assertEquals(listOf(8), result.first().graph.edgesForNode(7).map { it.id }.sorted())
        assertEquals(listOf(7), result.first().graph.edgesForNode(8).map { it.id }.sorted())
    }

    @Test
    fun `add two components, leave an unconnected one alone`() {
        val components = stronglyConnectedComponentsOf(
            Edge(0, 1), Edge(1, 0),
            Edge(1, 2),
            Edge(2, 3), Edge(3, 2),
            Edge(4, 5), Edge(5, 4)
        )

        val result = extendComponents(components)
        assertEquals(2, result.size)
        assertTrue(listOf(0, 1, 2, 3) in result.map { it.nodes.sorted() })
        assertTrue(listOf(4, 5) in result.map { it.nodes.sorted() })
        assertEquals(listOf(0, 2), result.first().graph.edgesForNode(1).map { it.id }.sorted())
        assertEquals(listOf(1), result.first().graph.edgesForNode(0).map { it.id }.sorted())
        assertEquals(listOf(1, 3), result.first().graph.edgesForNode(2).map { it.id }.sorted())
        assertEquals(listOf(2), result.first().graph.edgesForNode(3).map { it.id }.sorted())
        assertEquals(listOf(5), result.first().graph.edgesForNode(4).map { it.id }.sorted())
        assertEquals(listOf(4), result.first().graph.edgesForNode(5).map { it.id }.sorted())

    }

    private fun stronglyConnectedComponentsOf(vararg edges: Edge<Int>): List<StronglyConnectedComponent<Int>> {
        return detectStronglyConnectedComponents(DirectedGraph(edges.asList()))
    }

}
