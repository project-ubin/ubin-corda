package com.r3.demos.ubin2a.plan

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ObligationTests : NetworkTests() {

    @Test
    fun `get empty obligations map`() {
        val map = getObligationsMap(emptyList())
        assertEquals(0, map.size)
    }

    @Test
    fun `get obligations map with one obligation`() {
        val (A, B) = getNodes(2)
        val map = getObligationsMap(listOf(
            obligation(A, B, 100).state
        ))

        assertEquals(1, map.size)
        assertNotNull(map[Edge(A, B)])
        assertNull(map[Edge(B, A)])
        assertEquals(map[Edge(A, B)]!!.size, 1)
        assertEquals(map.obligationsForEdge(A, B).size, 1)
        assertEquals(map.obligationsForEdge(B, A).size, 0)
    }

    @Test
    fun `get obligations map with two different obligations`() {
        val (A, B) = getNodes(2)
        val map = getObligationsMap(listOf(
            obligation(A, B, 100).state,
            obligation(B, A, 200).state
        ))

        assertEquals(2, map.size)
        assertNotNull(map[Edge(A, B)])
        assertNotNull(map[Edge(B, A)])
        assertEquals(map[Edge(A, B)]!!.size, 1)
        assertEquals(map[Edge(B, A)]!!.size, 1)
        assertEquals(map.obligationsForEdge(A, B).size, 1)
        assertEquals(map.obligationsForEdge(B, A).size, 1)
    }

    @Test
    fun `get obligations map with two obligations from A to B`() {
        val (A, B) = getNodes(2)
        val map = getObligationsMap(listOf(
            obligation(A, B, 100).state,
            obligation(A, B, 200).state
        ))

        assertEquals(1, map.size)
        assertNotNull(map[Edge(A, B)])
        assertNull(map[Edge(B, A)])
        assertEquals(map[Edge(A, B)]!!.size, 2)
        assertEquals(map.obligationsForEdge(A, B).size, 2)
        assertEquals(map.obligationsForEdge(B, A).size, 0)
    }

}
