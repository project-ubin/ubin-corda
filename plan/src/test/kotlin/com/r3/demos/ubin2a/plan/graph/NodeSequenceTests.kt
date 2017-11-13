package com.r3.demos.ubin2a.plan.graph

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NodeSequenceTests {

    @Test
    fun `generate undirected sequence from simple cycle`() {
        val edges = mapOf(
            'A' to listOf('B'),
            'B' to listOf('A')
        )
        val sequence = getNodeSequenceFromEdges(edges)
        assertValidSequence(edges, sequence)
        assertEquals(2, sequence.size)
    }

    @Test
    fun `generate undirected sequence from two overlapping cycles`() {
        val edges = mapOf(
            'A' to listOf('B'),
            'B' to listOf('A', 'C'),
            'C' to listOf('B')
        )
        val sequence = getNodeSequenceFromEdges(edges)
        assertValidSequence(edges, sequence)
        assertEquals(4, sequence.size)
    }

    @Test
    fun `generate undirected sequence from two overlapping cycles with common edges`() {
        val edges = mapOf(
            'A' to listOf('B', 'C'),
            'B' to listOf('C'),
            'C' to listOf('D'),
            'D' to listOf('E', 'A'),
            'E' to listOf('A')
        )
        val expectedEdges = mapOf(
            'A' to listOf('B'),
            'B' to listOf('C'),
            'C' to listOf('D'),
            'D' to listOf('E'),
            'E' to listOf('A')
        )
        val sequence = getNodeSequenceFromEdges(edges)
        assertValidSequence(expectedEdges, sequence)
        assertEquals(5, sequence.size)
    }

    @Test
    fun `generate undirected sequence from three overlapping cycles with common edges`() {
        val edges = mapOf(
            'A' to listOf('B'),
            'B' to listOf('A', 'C', 'D'),
            'C' to listOf('B'),
            'D' to listOf('C')
        )
        val expectedEdges = mapOf(
            'A' to listOf('B'),
            'B' to listOf('C'),
            'C' to listOf('D'),
            'B' to listOf('A')
        )
        val sequence = getNodeSequenceFromEdges(edges)
        assertValidSequence(expectedEdges, sequence)
        assertEquals(5, sequence.size)
    }

    @Test
    fun `generate undirected sequence from overlapping cycles with common unidirectional edges`() {
        val edges = mapOf(
            'A' to listOf('B'),
            'B' to listOf('A', 'C'),
            'C' to listOf('D'),
            'D' to listOf('C')
        )
        val expectedEdges = mapOf(
            'A' to listOf('B'),
            'B' to listOf('C'),
            'C' to listOf('D')
        )
        val sequence = getNodeSequenceFromEdges(edges)
        assertValidSequence(expectedEdges, sequence)
        assertEquals(6, sequence.size)
    }

    @Test
    fun `generate directed sequence from simple cycle`() {
        val edges = mapOf(
            'A' to listOf('B'),
            'B' to listOf('A')
        )
        val sequence = getSequenceFromEdges(edges)
        assertValidSequence(edges, sequence)
        assertEquals(2, sequence.size)
    }

    @Test
    fun `generate directed sequence from two overlapping cycles`() {
        val edges = mapOf(
            'A' to listOf('B'),
            'B' to listOf('A', 'C'),
            'C' to listOf('B')
        )
        val sequence = getSequenceFromEdges(edges)
        assertValidSequence(edges, sequence)
        assertEquals(4, sequence.size)
    }

    @Test
    fun `generate directed sequence from two overlapping cycles with common edges`() {
        val edges = mapOf(
            'A' to listOf('B', 'C'),
            'B' to listOf('C'),
            'C' to listOf('D'),
            'D' to listOf('E', 'A'),
            'E' to listOf('A')
        )
        val sequence = getSequenceFromEdges(edges)
        assertValidSequence(edges, sequence)
        assertEquals(8, sequence.size)
    }

    @Test
    fun `generate directed sequence from three overlapping cycles with common edges`() {
        val edges = mapOf(
            'A' to listOf('B'),
            'B' to listOf('A', 'C', 'D'),
            'C' to listOf('B'),
            'D' to listOf('C')
        )
        val sequence = getSequenceFromEdges(edges)
        assertValidSequence(edges, sequence)
        assertEquals(7, sequence.size)
    }

    private fun assertValidSequence(edges: Map<Char, List<Char>>, actual: NodeSequence<Char>) {
        val list = actual.toList().plus(listOf(actual.first()))
        for ((node, connectedNodes) in edges) {
            for (connectedNode in connectedNodes) {
                assertTrue(actual.contains(node))
                assertTrue(actual.contains(connectedNode))
                assertTrue(
                    findSubList(list, listOf(node, connectedNode)) ||
                    findSubList(list, listOf(connectedNode, node))
                )
            }
        }
    }

    private fun <T> findSubList(list: List<T>, subList: List<T>): Boolean {
        outer@ for (i in 0..(list.size - subList.size)) {
            for (j in 0 until subList.size) {
                if (list[i + j] != subList[j]) { continue@outer }
            }
            return true
        }
        return false
    }

}
