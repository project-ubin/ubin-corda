package com.r3.demos.ubin2a.plan

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class GraphTypeTests : NetworkTests() {

    @Test
    fun `get directed graph from no obligations`() {
        assertFails { directedGraphOf(listOf()) }
    }

    @Test
    fun `get directed graph from one obligation`() {
        val (A, B) = getNodes(2)
        val graph = directedGraphOf(listOf(
            obligation(A, B, 100).state
        ))

        assertEquals(2, graph.nodes.size)
        assertEquals(1, graph.edgesForNode(A).size)
        assertEquals(0, graph.edgesForNode(B).size)
        assertEquals(B, graph.edgesForNode(A).first().id)
    }

    @Test
    fun `get directed graph from two obligations`() {
        val (A, B, C) = getNodes(3)

        val graph = directedGraphOf(listOf(
            obligation(A, B, 100).state,
            obligation(B, C, 200).state
        ))

        assertEquals(3, graph.nodes.size)
        assertEquals(1, graph.edgesForNode(A).size)
        assertEquals(1, graph.edgesForNode(B).size)
        assertEquals(0, graph.edgesForNode(C).size)
        assertEquals(B, graph.edgesForNode(A).first().id)
        assertEquals(C, graph.edgesForNode(B).first().id)
    }

    @Test
    fun `get directed graph from three obligations`() {
        val (A, B, C) = getNodes(3)

        val graph = directedGraphOf(listOf(
            obligation(A, B, 100).state,
            obligation(B, C, 200).state,
            obligation(C, A, 300).state
        ))

        assertEquals(3, graph.nodes.size)
        assertEquals(1, graph.edgesForNode(A).size)
        assertEquals(1, graph.edgesForNode(B).size)
        assertEquals(1, graph.edgesForNode(C).size)
        assertEquals(B, graph.edgesForNode(A).first().id)
        assertEquals(C, graph.edgesForNode(B).first().id)
        assertEquals(A, graph.edgesForNode(C).first().id)
    }

}
