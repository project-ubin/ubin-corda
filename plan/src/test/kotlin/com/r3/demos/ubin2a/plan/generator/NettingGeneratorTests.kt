package com.r3.demos.ubin2a.plan.generator

import com.r3.demos.ubin2a.plan.NettingTests
import org.junit.Test
import kotlin.test.assertEquals

class NettingGeneratorTests : NettingTests() {

    private val generator = NettingGeneratorImpl()

    @Test
    fun `get sequence from simple graph formed by two nodes`() {
        val (nodes, obligations) = graph(2)
            .withLimits(0, 0)
            .withObligation(A, B, 1000)
            .withObligation(B, A, 1500)
            .generate()

        val (A, B) = nodes
        val sequence = nodeSequenceFrom(obligations)
        assertSequence(sequence, A, B, A)
    }

    @Test
    fun `get sequence from simple graph formed by three nodes`() {
        val (nodes, obligations) = graph(3)
            .withLimits(0, 0, 0)
            .withObligation(A, B, 1000)
            .withObligation(B, C, 1500)
            .withObligation(C, A, 2000)
            .generate()

        val (A, B, C) = nodes
        val sequence = nodeSequenceFrom(obligations)
        assertSequence(sequence, A, B, C, A)
    }

    @Test
    fun `get sequence from simple graph formed by four nodes`() {
        val (nodes, obligations) = graph(4)
            .withLimits(0, 0, 0, 0)
            .withObligation(A, B, 1000)
            .withObligation(B, C, 1500)
            .withObligation(C, D, 2000)
            .withObligation(D, A, 2500)
            .generate()

        val (A, B, C, D) = nodes
        val sequence = nodeSequenceFrom(obligations)
        assertSequence(sequence, A, B, C, D, A)
    }

    @Test
    fun `get sequence from simple graph formed by five nodes`() {
        val (nodes, obligations) = graph(5)
            .withLimits(0, 0, 0, 0, 0)
            .withObligation(A, B, 1000)
            .withObligation(B, C, 1500)
            .withObligation(C, D, 2000)
            .withObligation(D, E, 2500)
            .withObligation(E, A, 3000)
            .generate()

        val (A, B, C, D, E) = nodes
        val sequence = nodeSequenceFrom(obligations)
        assertSequence(sequence, A, B, C, D, E, A)
    }

    @Test
    fun `get sequence from overlapping cycles formed by three nodes`() {
        val (nodes, obligations) = graph(3)
            .withLimits(0, 0, 0)
            .withObligation(A, B, 1000)
            .withObligation(B, C, 2000)
            .withObligation(C, B, 2500)
            .withObligation(B, A, 1500)
            .generate()

        val (A, B, C) = nodes
        val sequence = nodeSequenceFrom(obligations)
        assertSequence(sequence, A, B, C, B, A)
    }

    @Test
    fun `get sequence from two overlapping cycles formed by three nodes (out of order)`() {
        val (nodes, obligations) = graph(3)
            .withLimits(0, 0, 0)
            .withObligation(A, B, 1000)
            .withObligation(B, A, 1500)
            .withObligation(B, C, 2000)
            .withObligation(C, B, 2500)
            .generate()

        val (A, B, C) = nodes
        val sequence = nodeSequenceFrom(obligations)
        assertSequence(sequence, A, B, C, B, A)
    }

    @Test
    fun `get sequence from two overlapping cycles formed by five nodes`() {
        val (nodes, obligations) = graph(5)
            .withLimits(0, 0, 0, 0, 0)
            .withObligation(A, B, 1000)
            .withObligation(B, C, 1500)
            .withObligation(C, A, 2000)
            .withObligation(A, E, 2500)
            .withObligation(E, D, 3500)
            .withObligation(D, A, 4000)
            .generate()

        val (A, B, C, D, E) = nodes
        val sequence = nodeSequenceFrom(obligations)
        assertSequence(sequence, A, E, D, A, B, C, A)
    }

    @Test
    fun `get sequence from two overlapping cycles formed by seven nodes`() {
        val (nodes, obligations) = graph(7)
            .withLimits(0, 0, 0)
            .withObligation(A, B, 1000)
            .withObligation(B, C, 2000)
            .withObligation(C, E, 1500)
            .withObligation(C, D, 2500)
            .withObligation(D, A, 1500)
            .withObligation(E, F, 1500)
            .withObligation(F, G, 1500)
            .withObligation(G, C, 1500)
            .generate()

        val (A, B, C, D, E, F, G) = nodes
        val sequence = nodeSequenceFrom(obligations)
        assertSequence(sequence, A, B, C, E, F, G, C, D, A)
    }

    @Test
    fun `get sequence from two overlapping cycles formed by seven nodes (out of order)`() {
        val (nodes, obligations) = graph(7)
            .withLimits(0, 0, 0)
            .withObligation(A, B, 1000)
            .withObligation(B, C, 2000)
            .withObligation(C, D, 2500)
            .withObligation(D, A, 1500)
            .withObligation(C, E, 1500)
            .withObligation(E, F, 1500)
            .withObligation(F, G, 1500)
            .withObligation(G, C, 1500)
            .generate()

        val (A, B, C, D, E, F, G) = nodes
        val sequence = nodeSequenceFrom(obligations)
        assertSequence(sequence, A, B, C, E, F, G, C, D, A)
    }

    @Test
    fun `get sequence from three overlapping cycles formed by nine nodes`() {
        val (nodes, obligations) = graph(9)
            .withLimits(0, 0, 0)
            .withObligation(A, B, 1000)
            .withObligation(B, C, 2000)
            .withObligation(C, H, 1500)
            .withObligation(C, E, 1500)
            .withObligation(C, D, 2500)
            .withObligation(D, A, 1500)
            .withObligation(E, F, 1500)
            .withObligation(F, G, 1500)
            .withObligation(G, C, 1500)
            .withObligation(H, I, 1500)
            .withObligation(I, C, 1500)
            .generate()

        val (A, B, C, D, E, F, G, H, I) = nodes
        val sequence = nodeSequenceFrom(obligations)
        assertSequence(sequence, A, B, C, E, F, G, C, H, I, C, D, A)
    }

    @Test
    fun `get sequence from three overlapping cycles formed by nine nodes (out of order)`() {
        val (nodes, obligations) = graph(9)
            .withLimits(0, 0, 0)
            .withObligation(A, B, 1000)
            .withObligation(B, C, 2000)
            .withObligation(C, D, 2500)
            .withObligation(D, A, 1500)
            .withObligation(C, E, 1500)
            .withObligation(E, F, 1500)
            .withObligation(F, G, 1500)
            .withObligation(G, C, 1500)
            .withObligation(A, B, 1000)
            .withObligation(B, C, 2000)
            .withObligation(C, E, 1500)
            .withObligation(C, D, 2500)
            .withObligation(D, A, 1500)
            .withObligation(E, F, 1500)
            .withObligation(F, G, 1500)
            .withObligation(G, C, 1500)
            .withObligation(C, H, 1500)
            .withObligation(H, I, 1500)
            .withObligation(I, C, 1500)
            .generate()

        val (A, B, C, D, E, F, G, H, I) = nodes
        val sequence = nodeSequenceFrom(obligations)
        assertSequence(sequence, A, B, C, H, I, C, E, F, G, C, D, A)
    }

    @Test
    fun `generate payments for settleable cycle formed by four obligations`() {
        val (nodes, obligations) = graph(4)
            .withLimits(500, 1200, 1700, 1900)
            .withObligation(A, B, 1000)
            .withObligation(B, C, 1500)
            .withObligation(C, D, 2000)
            .withObligation(D, A, 2500)
            .generate()

        val (A, B, C, D) = nodes
        val result = generator.generate(obligations)

        assertEquals(3, result.payments.size)
        result.assertHasPayment(B, A, 500)
        result.assertHasPayment(C, A, 500)
        result.assertHasPayment(D, A, 500)
    }

    @Test
    fun `generate payments for settleable cycle formed by four obligations but with too low limits`() {
        val (nodes, obligations) = graph(4)
            .withLimits(500, 1200, 700, 1900)
            .withObligation(A, B, 1000)
            .withObligation(B, C, 1500)
            .withObligation(C, D, 2000)
            .withObligation(D, A, 2500)
            .generate()

        val (A, B, C, D) = nodes
        val result = generator.generate(obligations)

        assertEquals(3, result.payments.size)
        result.assertHasPayment(B, A, 500)
        result.assertHasPayment(C, A, 500)
        result.assertHasPayment(D, A, 500)
    }

    @Test
    fun `generate payments for settleable cycle formed by five obligations`() {
        val (nodes, obligations) = graph(5)
            .withLimits(3, 4, 5, 4, 3)
            .withObligation(A, B, 5)
            .withObligation(B, C, 6)
            .withObligation(C, D, 8)
            .withObligation(D, E, 7)
            .withObligation(E, A, 8)
            .generate()

        val (A, B, C, D, E) = nodes
        val result = generator.generate(obligations)

        assertEquals(3, result.payments.size)
        result.assertHasPayment(B, A, 1)
        result.assertHasPayment(C, A, 2)
        result.assertHasPayment(E, D, 1)
    }

    @Test
    fun `generate payments for interconnected cycles formed by four obligations`() {
        val (nodes, obligations) = graph(3)
            .withLimits(1000, 1000, 2000)
            .withObligation(A, B, 300)
            .withObligation(B, C, 100)
            .withObligation(C, B, 1500)
            .withObligation(B, A, 2000)
            .generate()

        val (A, B, C) = nodes
        val result = generator.generate(obligations)

        assertEquals(2, result.payments.size)
        result.assertHasPayment(C, A, 1400)
        result.assertHasPayment(B, A, 300)
    }

}
