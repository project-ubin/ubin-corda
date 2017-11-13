package com.r3.demos.ubin2a.plan.graph

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DirectedGraphTests {
    @Test
    fun testAddingEdges() {
        val graph = DirectedGraph(listOf(Edge(0, 1), Edge(1, 0), Edge(0, 2)))

        assertEquals(graph.edgesForNode(2), emptyList())
        assertFailsWith(IllegalArgumentException::class, { graph.insertEdge(3, 5) })

        graph.insertEdge(2, 1)
        assertEquals(1, graph.edgesForNode(2).size)
        assertEquals(1, graph.edgesForNode(2).first().id)

        graph.insertEdge(0, 1)
        assertEquals(2, graph.edgesForNode(0).size)
        assertEquals(listOf(1, 2), graph.edgesForNode(0).map { it.id })

        graph.insertEdge(2, 0)
        assertEquals(2, graph.edgesForNode(2).size)
        assertEquals(listOf(1, 0), graph.edgesForNode(2).map { it.id })
    }
}