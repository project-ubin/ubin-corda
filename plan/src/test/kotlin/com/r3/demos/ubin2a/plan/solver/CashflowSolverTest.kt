package com.r3.demos.ubin2a.plan.solver

import com.r3.demos.ubin2a.base.SGD
import com.r3.demos.ubin2a.plan.*
import com.r3.demos.ubin2a.plan.generator.NettingGenerator
import com.r3.demos.ubin2a.plan.generator.NettingResult
import com.r3.demos.ubin2a.plan.generator.obligationEdgeOf
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith


class CashflowSolverTest : NettingTests() {

    class MockGenerator(private val result: NettingResult) : NettingGenerator {
        override fun generate(obligations: Obligations): NettingResult {
            return result;
        }

        override fun generate(obligations: ObligationsMap): NettingResult {
            return result;
        }
    }


    @Test
    fun `Valid Generation`() {
        val (nodes, obligationsMap, _, limits) =
            graph(2)
                .withLimits(3, 5)
                .withObligation(A, B, 5)
                .withObligation(A, B, 30)
                .withObligation(A, B, 3)
                .withObligation(B, A, 5)
                .generate()
        val (A, B) = nodes

        val obligations = obligationsMap.allEntries().filter { it.amount < 10.SGD }

        val solver = CashflowSolverImpl(MockGenerator(NettingResult(listOf(NettingPayment(A, B, 3.SGD)), obligations.map { obligationEdgeOf(it) })))
        val result = solver.solveForNettingCashflows(CycleResult(obligations, limits), obligationsMap, limits)
        assertEquals(result.payments.first(), NettingPayment(A, B, 3.SGD))
    }

    @Test
    fun `Invalid Generation - cash flow too big`() {
        val (nodes, obligationsMap, _, limits) =
            graph(2)
                .withLimits(3, 5)
                .withObligation(A, B, 5)
                .withObligation(A, B, 30)
                .withObligation(A, B, 3)
                .withObligation(B, A, 5)
                .generate()
        val (A, B) = nodes

        val obligations = obligationsMap.allEntries().filter { it.amount < 10.SGD }

        val solver = CashflowSolverImpl(MockGenerator(NettingResult(listOf(NettingPayment(A, B, 5.SGD)), obligations.map { obligationEdgeOf(it) })))
        assertFailsWith(NettingFailure::class, { solver.solveForNettingCashflows(CycleResult(obligations, limits), obligationsMap, limits) })
    }


}
