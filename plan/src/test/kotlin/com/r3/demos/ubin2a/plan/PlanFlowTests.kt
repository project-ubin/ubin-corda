package com.r3.demos.ubin2a.plan

import com.r3.demos.ubin2a.base.SGD
import com.r3.demos.ubin2a.plan.generator.countObligations
import net.corda.core.contracts.Amount
import net.corda.core.identity.AbstractParty
import net.corda.core.utilities.getOrThrow
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

class PlanFlowTests : NettingTests() {

    @Test
    fun `All obligations net to zero`() {
        val (A, B, C) = getNodes(3)

        // Create some obligations.
        val obligationA = obligation(A, B, 100)
        val obligationB = obligation(B, C, 100)
        val obligationC = obligation(C, A, 100)

        // The obligation graph.
        val obligations = setOfObligations(obligationA, obligationB, obligationC)

        // The cash each node has pledged for the netting round. Again, using 'Party' instead of 'AnonymousParty'
        val limits: Map<AbstractParty, Amount<Currency>> = mapOf(
            A to 1000.SGD,
            B to 1500.SGD,
            C to 2000.SGD
        )

        val flow = NewPlanFlow(obligations, limits, NettingMode.LargestSum)
        val (paymentsToMake, resultantObligations) = bank1.services.startFlow(flow).resultFuture.getOrThrow()
        assertEquals(0, paymentsToMake.size)
        assertEquals(3, countObligations(resultantObligations))
    }

    @Test
    fun `Got a payment`() {
        val (A, B, C) = getNodes(3)

        // Create some obligations.
        val obligationA = obligation(A, B, 100)
        val obligationB = obligation(B, C, 200)
        val obligationC = obligation(C, A, 100)

        // The obligation graph.
        val obligations = setOfObligations(obligationA, obligationB, obligationC)

        // The cash each node has pledged for the netting round. Again, using 'Party' instead of 'AnonymousParty'
        val limits: Map<AbstractParty, Amount<Currency>> = mapOf(
            A to 1000.SGD,
            B to 1500.SGD,
            C to 2000.SGD
        )

        val flow = NewPlanFlow(obligations, limits, NettingMode.LargestSum)
        val (paymentsToMake, resultantObligations) = bank1.services.startFlow(flow).resultFuture.getOrThrow()
        assertEquals(1, paymentsToMake.size)
        assertEquals(100.SGD, paymentsToMake.first().amount)
        assertEquals(B, paymentsToMake.first().from)
        assertEquals(C, paymentsToMake.first().to)
        assertEquals(3, countObligations(resultantObligations))
    }

    @Test
    fun `Settlement from Functional Test F6_3 using largest obligation sum`() {
        val (A, B, C, D, E) = getNodes(5)

        // obligations
        val obligationT1 = obligation(A, B, 5)
        val obligationT2 = obligation(B, C, 6)
        val obligationT3 = obligation(B, C, 30)
        val obligationT4 = obligation(C, D, 8)
        val obligationT5 = obligation(C, E, 80)
        val obligationT6 = obligation(D, E, 7)
        val obligationT7 = obligation(A, C, 6)
        val obligationT8 = obligation(E, A, 8)
        val obligationT9 = obligation(E, B, 100)
        val obligationT10 = obligation(D, A, 5)

        val obligations = setOfObligations(
            obligationT1, obligationT2, obligationT3, obligationT4,
            obligationT5, obligationT6, obligationT7, obligationT8,
            obligationT9, obligationT10
        )

        val limits: Map<AbstractParty, Amount<Currency>> = mapOf(
            A to 3.SGD,
            B to 4.SGD,
            C to 5.SGD,
            D to 4.SGD,
            E to 3.SGD)
        val flow = NewPlanFlow(obligations, limits, NettingMode.LargestSum)

        val (paymentsToMake, resultantObligations) = bank1.services.startFlow(flow).resultFuture.getOrThrow()
        assertEquals(5, countObligations(resultantObligations))
        assertEquals(3, paymentsToMake.size)

        val newLimits = applyPaymentsToLimits(limits, paymentsToMake)
        assertEquals(6.SGD, newLimits[A])
        assertEquals(3.SGD, newLimits[B])
        assertEquals(3.SGD, newLimits[C])
        assertEquals(5.SGD, newLimits[D])
        assertEquals(2.SGD, newLimits[E])
    }

    @Test
    fun `Settlement from Functional Test F1_1`() {
        val (A, B) = getNodes(2)

        // obligations
        val obligationT1 = obligation(A, B, 2)
        val obligationT2 = obligation(B, A, 2)

        val obligations = setOfObligations(
            obligationT1, obligationT2
        )

        val limits: Map<AbstractParty, Amount<Currency>> = mapOf(
            A to 0.SGD,
            B to 0.SGD)
        val flow = NewPlanFlow(obligations, limits, NettingMode.BestEffort)

        val (paymentsToMake, resultantObligations) = bank1.services.startFlow(flow).resultFuture.getOrThrow()

        assertEquals(2, countObligations(resultantObligations))
        assertEquals(0, paymentsToMake.size)

        val newLimits = applyPaymentsToLimits(limits, paymentsToMake)
        assertEquals(0.SGD, newLimits[A])
        assertEquals(0.SGD, newLimits[B])
    }

    @Test
    fun `Settlement from Functional Test F1_2`() {
        val (A, B) = getNodes(2)

        // obligations
        val obligationT1 = obligation(A, B, 5)
        val obligationT2 = obligation(B, A, 6)

        val obligations = setOfObligations(
            obligationT1, obligationT2
        )

        val limits: Map<AbstractParty, Amount<Currency>> = mapOf(
            A to 3.SGD,
            B to 4.SGD)
        val flow = NewPlanFlow(obligations, limits, NettingMode.BestEffort)

        val (paymentsToMake, resultantObligations) = bank1.services.startFlow(flow).resultFuture.getOrThrow()

        assertEquals(2, countObligations(resultantObligations))
        assertEquals(1, paymentsToMake.size)

        val newLimits = applyPaymentsToLimits(limits, paymentsToMake)
        assertEquals(4.SGD, newLimits[A])
        assertEquals(3.SGD, newLimits[B])
    }

    @Test
    fun `Settlement from Functional Test F1_3`() {
        val (A, B) = getNodes(2)

        // obligations
        val obligationT1 = obligation(A, B, 8)
        val obligationT2 = obligation(B, A, 10)
        val obligationT3 = obligation(B, A, 20)
        val obligationT4 = obligation(A, B, 17)

        val obligations = setOfObligations(
            obligationT1, obligationT2, obligationT3, obligationT4
        )

        val limits: Map<AbstractParty, Amount<Currency>> = mapOf(
            A to 5.SGD,
            B to 6.SGD)
        val flow = NewPlanFlow(obligations, limits, NettingMode.BestEffort)

        val (paymentsToMake, resultantObligations) = bank1.services.startFlow(flow).resultFuture.getOrThrow()
        assertEquals(4, countObligations(resultantObligations))
        assertEquals(1, paymentsToMake.size)

        val newLimits = applyPaymentsToLimits(limits, paymentsToMake)
        assertEquals(10.SGD, newLimits[A])
        assertEquals(1.SGD, newLimits[B])
    }

    @Test
    fun `Settlement from Functional Test F2_1`() {
        val (A, B) = getNodes(2)

        // obligations
        val obligationT1 = obligation(B, A, 2)
        val obligationT2 = obligation(B, A, 3)
        val obligationT3 = obligation(A, B, 5)
        val obligationT4 = obligation(A, B, 2)

        val obligations = setOfObligations(
                obligationT1, obligationT2, obligationT3, obligationT4
        )

        val limits: Map<AbstractParty, Amount<Currency>> = mapOf(
                A to 0.SGD,
                B to 0.SGD)
        val flow = NewPlanFlow(obligations, limits, NettingMode.BestEffort)

        val (paymentsToMake, resultantObligations) = bank1.services.startFlow(flow).resultFuture.getOrThrow()

        assertEquals(3, countObligations(resultantObligations))
        assertEquals(0, paymentsToMake.size)

        val newLimits = applyPaymentsToLimits(limits, paymentsToMake)
        assertEquals(0.SGD, newLimits[A])
        assertEquals(0.SGD, newLimits[B])
    }

    @Test
    fun `Settlement from Functional Test F2_2`() {
        val (A, B) = getNodes(2)

        // obligations
        val obligationT1 = obligation(A, B, 20)
        val obligationT2 = obligation(B, A, 15)
        val obligationT3 = obligation(A, B, 30)
        val obligationT4 = obligation(A, B, 15)
        val obligationT5 = obligation(B, A, 80)

        val obligations = setOfObligations(
                obligationT1, obligationT2, obligationT3, obligationT4, obligationT5
        )

        val limits: Map<AbstractParty, Amount<Currency>> = mapOf(
                A to 10.SGD,
                B to 8.SGD)
        val flow = NewPlanFlow(obligations, limits, NettingMode.BestEffort)

        val (paymentsToMake, resultantObligations) = bank1.services.startFlow(flow).resultFuture.getOrThrow()

        assertEquals(2, countObligations(resultantObligations))
        assertEquals(1, paymentsToMake.size)

        val newLimits = applyPaymentsToLimits(limits, paymentsToMake)
        assertEquals(5.SGD, newLimits[A])
        assertEquals(13.SGD, newLimits[B])
    }

    @Test
    fun `Settlement from Functional Test F2_3`() {
        val (A, B) = getNodes(2)

        // obligations
        val obligationT1 = obligation(A, B, 20)
        val obligationT2 = obligation(B, A, 15)
        val obligationT3 = obligation(A, B, 30)
        val obligationT4 = obligation(B, A, 12)
        val obligationT5 = obligation(A, B, 15)
        val obligationT6 = obligation(B, A, 80)

        val obligations = setOfObligations(
            obligationT1, obligationT2, obligationT3, obligationT4, obligationT5, obligationT6
        )

        val limits: Map<AbstractParty, Amount<Currency>> = mapOf(
            A to 10.SGD,
            B to 8.SGD)
        val flow = NewPlanFlow(obligations, limits, NettingMode.BestEffort)

        val (paymentsToMake, resultantObligations) = bank1.services.startFlow(flow).resultFuture.getOrThrow()
        assertEquals(4, countObligations(resultantObligations))
        assertEquals(1, paymentsToMake.size)

        val newLimits = applyPaymentsToLimits(limits, paymentsToMake)
        assertEquals(2.SGD, newLimits[A])
        assertEquals(16.SGD, newLimits[B])
    }

    @Test
    fun `Settlement from Functional Test F3_1`() {
        val (A, B, C) = getNodes(3)

        // obligations
        val obligationT1 = obligation(A, C, 2)
        val obligationT2 = obligation(B, C, 3)
        val obligationT3 = obligation(C, B, 5)
        val obligationT4 = obligation(B, A, 2)

        val obligations = setOfObligations(
                obligationT1, obligationT2, obligationT3, obligationT4
        )

        val limits: Map<AbstractParty, Amount<Currency>> = mapOf(
                A to 0.SGD,
                B to 0.SGD,
                C to 0.SGD)
        val flow = NewPlanFlow(obligations, limits, NettingMode.BestEffort)

        val (paymentsToMake, resultantObligations) = bank1.services.startFlow(flow).resultFuture.getOrThrow()

        assertEquals(0, countObligations(resultantObligations))
        assertEquals(0, paymentsToMake.size)

        val newLimits = applyPaymentsToLimits(limits, paymentsToMake)
        assertEquals(0.SGD, newLimits[A])
        assertEquals(0.SGD, newLimits[B])
        assertEquals(0.SGD, newLimits[C])
    }

    @Test
    fun `Settlement from Functional Test F3_2`() {
        val (A, B, C) = getNodes(3)

        // obligations
        val obligationT1 = obligation(A, B, 5)
        val obligationT2 = obligation(A, C, 6)
        val obligationT3 = obligation(A, B, 10)
        val obligationT4 = obligation(B, A, 25)
        val obligationT5 = obligation(C, B, 7)

        val obligations = setOfObligations(
                obligationT1, obligationT2, obligationT3, obligationT4, obligationT5
        )

        val limits: Map<AbstractParty, Amount<Currency>> = mapOf(
                A to 3.SGD,
                B to 4.SGD,
                C to 5.SGD)
        val flow = NewPlanFlow(obligations, limits, NettingMode.BestEffort)

        val (paymentsToMake, resultantObligations) = bank1.services.startFlow(flow).resultFuture.getOrThrow()

        assertEquals(0, countObligations(resultantObligations))
        assertEquals(0, paymentsToMake.size)

        val newLimits = applyPaymentsToLimits(limits, paymentsToMake)
        assertEquals(3.SGD, newLimits[A])
        assertEquals(4.SGD, newLimits[B])
        assertEquals(5.SGD, newLimits[C])
    }

    @Test
    fun `Settlement from Functional Test F3_3`() {
        val (A, B, C) = getNodes(3)

        // obligations
        val obligationT1 = obligation(A, B, 5)
        val obligationT2 = obligation(B, A, 7)
        val obligationT3 = obligation(B, C, 8)
        val obligationT4 = obligation(C, B, 7)

        val obligations = setOfObligations(
            obligationT1, obligationT2, obligationT3, obligationT4
        )

        val limits: Map<AbstractParty, Amount<Currency>> = mapOf(
            A to 3.SGD,
            B to 4.SGD,
            C to 5.SGD)
        val flow = NewPlanFlow(obligations, limits, NettingMode.BestEffort)

        val (paymentsToMake, resultantObligations) = bank1.services.startFlow(flow).resultFuture.getOrThrow()
        assertEquals(4, countObligations(resultantObligations))
        assertEquals(2, paymentsToMake.size)

        val newLimits = applyPaymentsToLimits(limits, paymentsToMake)
        assertEquals(5.SGD, newLimits[A])
        assertEquals(1.SGD, newLimits[B])
        assertEquals(6.SGD, newLimits[C])
    }

    @Test
    fun `Settlement from Functional Test F4_1`() {
        val (A, B, C) = getNodes(3)

        // obligations
        val obligationT1 = obligation(A, B, 8)
        val obligationT2 = obligation(B, C, 7)
        val obligationT3 = obligation(C, A, 9)
        val obligationT4 = obligation(A, B, 20)

        val obligations = setOfObligations(
                obligationT1, obligationT2, obligationT3, obligationT4
        )

        val limits: Map<AbstractParty, Amount<Currency>> = mapOf(
                A to 5.SGD,
                B to 6.SGD,
                C to 7.SGD)
        val flow = NewPlanFlow(obligations, limits, NettingMode.BestEffort)

        val (paymentsToMake, resultantObligations) = bank1.services.startFlow(flow).resultFuture.getOrThrow()

        assertEquals(3, countObligations(resultantObligations))
        assertEquals(2, paymentsToMake.size)

        val newLimits = applyPaymentsToLimits(limits, paymentsToMake)
        assertEquals(6.SGD, newLimits[A])
        assertEquals(7.SGD, newLimits[B])
        assertEquals(5.SGD, newLimits[C])
    }

    // TODO: This one could be more optimal.
    @Test
    fun `Settlement from Functional Test F4_2`() {
        val (A, B, C) = getNodes(3)

        // obligations
        val obligationT1 = obligation(A, B, 8)      // Netted.
        val obligationT2 = obligation(B, C, 7)      // Netted.
        val obligationT3 = obligation(C, A, 9)      // Netted.
        val obligationT4 = obligation(A, B, 20)
        val obligationT5 = obligation(B, A, 7)      // Can also net this one but we don't
        val obligationT6 = obligation(A, B, 40)
        val obligationT7 = obligation(B, C, 80)
        val obligationT8 = obligation(C, B, 10)

        val obligations = setOfObligations(
                obligationT1, obligationT2, obligationT3, obligationT4,
                obligationT5, obligationT6, obligationT7, obligationT8
        )

        val limits: Map<AbstractParty, Amount<Currency>> = mapOf(
                A to 5.SGD,
                B to 6.SGD,
                C to 7.SGD)
        val flow = NewPlanFlow(obligations, limits, NettingMode.BestEffort)

        val (paymentsToMake, resultantObligations) = bank1.services.startFlow(flow).resultFuture.getOrThrow()

        assertEquals(3, countObligations(resultantObligations))
        assertEquals(2, paymentsToMake.size)

        val newLimits = applyPaymentsToLimits(limits, paymentsToMake)
        assertEquals(6.SGD, newLimits[A])
        assertEquals(7.SGD, newLimits[B])
        assertEquals(5.SGD, newLimits[C])
    }

    @Test
    fun `Settlement from Functional Test F4_3`() {
        val (A, B, C) = getNodes(3)

        // obligations
        val obligationT1 = obligation(A, B, 8)
        val obligationT2 = obligation(B, C, 10)
        val obligationT3 = obligation(B, A, 7)
        val obligationT4 = obligation(B, A, 10)
        val obligationT5 = obligation(C, B, 12)
        val obligationT6 = obligation(A, B, 9)
        val obligationT7 = obligation(B, A, 30)
        val obligationT8 = obligation(C, B, 50)
        val obligationT9 = obligation(B, A, 10)
        val obligationT10 = obligation(A, B, 80)

        val obligations = setOfObligations(
            obligationT1, obligationT2, obligationT3, obligationT4,
            obligationT5, obligationT6, obligationT7, obligationT8,
            obligationT9, obligationT10
        )

        val limits: Map<AbstractParty, Amount<Currency>> = mapOf(
            A to 5.SGD,
            B to 6.SGD,
            C to 7.SGD)
        val flow = NewPlanFlow(obligations, limits, NettingMode.BestEffort)

        val (paymentsToMake, resultantObligations) = bank1.services.startFlow(flow).resultFuture.getOrThrow()
        assertEquals(6, countObligations(resultantObligations))
        assertEquals(1, paymentsToMake.size)

        val newLimits = applyPaymentsToLimits(limits, paymentsToMake)
        assertEquals(5.SGD, newLimits[A])
        assertEquals(8.SGD, newLimits[B])
        assertEquals(5.SGD, newLimits[C])
    }

    @Test
    fun `Settlement from Functional Test F4_4`() {
        val (A, B, C) = getNodes(3)

        // obligations
        val obligationT1 = obligation(C, B, 9)
        val obligationT2 = obligation(B, C, 8)
        val obligationT3 = obligation(A, B, 7)
        val obligationT4 = obligation(B, A, 20)

        val obligations = setOfObligations(
                obligationT1, obligationT2, obligationT3, obligationT4
        )

        val limits: Map<AbstractParty, Amount<Currency>> = mapOf(
                A to 5.SGD,
                B to 6.SGD,
                C to 7.SGD)
        val flow = NewPlanFlow(obligations, limits, NettingMode.BestEffort)

        val (paymentsToMake, resultantObligations) = bank1.services.startFlow(flow).resultFuture.getOrThrow()

        assertEquals(2, countObligations(resultantObligations))
        assertEquals(1, paymentsToMake.size)

        val newLimits = applyPaymentsToLimits(limits, paymentsToMake)
        assertEquals(5.SGD, newLimits[A])
        assertEquals(7.SGD, newLimits[B])
        assertEquals(6.SGD, newLimits[C])
    }

    @Test
    fun `Settlement from Functional Test F5_1`() {
        val (A, B, C, D, E) = getNodes(5)

        // obligations
        val obligationT1 = obligation(A, B, 5)
        val obligationT2 = obligation(B, C, 6)
        val obligationT3 = obligation(C, D, 7)
        val obligationT4 = obligation(D, E, 8)
        val obligationT5 = obligation(E, A, 7)

        val obligations = setOfObligations(
                obligationT1, obligationT2, obligationT3, obligationT4, obligationT5
        )

        val limits: Map<AbstractParty, Amount<Currency>> = mapOf(
                A to 3.SGD,
                B to 4.SGD,
                C to 5.SGD,
                D to 4.SGD,
                E to 3.SGD)
        val flow = NewPlanFlow(obligations, limits, NettingMode.BestEffort)

        val (paymentsToMake, resultantObligations) = bank1.services.startFlow(flow).resultFuture.getOrThrow()

        assertEquals(5, countObligations(resultantObligations))
        assertEquals(3, paymentsToMake.size)

        val newLimits = applyPaymentsToLimits(limits, paymentsToMake)
        assertEquals(5.SGD, newLimits[A])
        assertEquals(3.SGD, newLimits[B])
        assertEquals(4.SGD, newLimits[C])
        assertEquals(3.SGD, newLimits[D])
        assertEquals(4.SGD, newLimits[E])
    }

    @Test
    fun `Settlement from Functional Test F5_2`() {
        val (A, B, C, D, E) = getNodes(5)

        // obligations
        val obligationT1 = obligation(A, B, 2)
        val obligationT2 = obligation(B, C, 3)
        val obligationT3 = obligation(C, D, 5)
        val obligationT4 = obligation(D, E, 6)
        val obligationT5 = obligation(E, A, 2)
        val obligationT6 = obligation(E, B, 1)
        val obligationT7 = obligation(E, C, 2)
        val obligationT8 = obligation(E, D, 1)

        val obligations = setOfObligations(
                obligationT1, obligationT2, obligationT3, obligationT4,
                obligationT5, obligationT6, obligationT7, obligationT8
        )

        val limits: Map<AbstractParty, Amount<Currency>> = mapOf(
                A to 0.SGD,
                B to 0.SGD,
                C to 0.SGD,
                D to 0.SGD,
                E to 0.SGD)
        val flow = NewPlanFlow(obligations, limits, NettingMode.BestEffort)

        val (paymentsToMake, resultantObligations) = bank1.services.startFlow(flow).resultFuture.getOrThrow()

        assertEquals(0, countObligations(resultantObligations))
        assertEquals(0, paymentsToMake.size)

        val newLimits = applyPaymentsToLimits(limits, paymentsToMake)
        assertEquals(0.SGD, newLimits[A])
        assertEquals(0.SGD, newLimits[B])
        assertEquals(0.SGD, newLimits[C])
        assertEquals(0.SGD, newLimits[D])
        assertEquals(0.SGD, newLimits[E])
    }

    @Test
    fun `Settlement from Functional Test F5_3`() {
        val (A, B, C, D, E) = getNodes(5)

        // obligations
        val obligationT1 = obligation(A, B, 5)
        val obligationT2 = obligation(B, C, 6)
        val obligationT3 = obligation(C, A, 7)
        val obligationT4 = obligation(A, E, 8)
        val obligationT5 = obligation(E, D, 9)
        val obligationT6 = obligation(D, A, 10)

        val obligations = setOfObligations(
                obligationT1, obligationT2, obligationT3,
                obligationT4, obligationT5, obligationT6
        )

        val limits: Map<AbstractParty, Amount<Currency>> = mapOf(
                A to 3.SGD,
                B to 4.SGD,
                C to 5.SGD,
                D to 4.SGD,
                E to 3.SGD)
        val flow = NewPlanFlow(obligations, limits, NettingMode.BestEffort)

        val (paymentsToMake, resultantObligations) = bank1.services.startFlow(flow).resultFuture.getOrThrow()

        assertEquals(6, countObligations(resultantObligations))
        assertEquals(4, paymentsToMake.size)

        val newLimits = applyPaymentsToLimits(limits, paymentsToMake)
        assertEquals(7.SGD, newLimits[A])
        assertEquals(3.SGD, newLimits[B])
        assertEquals(4.SGD, newLimits[C])
        assertEquals(3.SGD, newLimits[D])
        assertEquals(2.SGD, newLimits[E])
    }

    @Test
    fun `Settlement from Functional Test F5_4`() {
        val (A, B, C, D, E) = getNodes(5)

        // obligations
        val obligationT1 = obligation(E, A, 8)
        val obligationT2 = obligation(E, D, 9)
        val obligationT3 = obligation(D, E, 15)
        val obligationT4 = obligation(A, B, 14)
        val obligationT5 = obligation(B, C, 15)
        val obligationT6 = obligation(C, A, 10)

        val obligations = setOfObligations(
                obligationT1, obligationT2, obligationT3,
                obligationT4, obligationT5, obligationT6
        )

        val limits: Map<AbstractParty, Amount<Currency>> = mapOf(
                A to 3.SGD,
                B to 4.SGD,
                C to 5.SGD,
                D to 8.SGD,
                E to 3.SGD)
        val flow = NewPlanFlow(obligations, limits, NettingMode.BestEffort)

        val (paymentsToMake, resultantObligations) = bank1.services.startFlow(flow).resultFuture.getOrThrow()

        assertEquals(2, countObligations(resultantObligations))
        assertEquals(1, paymentsToMake.size)

        val newLimits = applyPaymentsToLimits(limits, paymentsToMake)
        assertEquals(3.SGD, newLimits[A])
        assertEquals(4.SGD, newLimits[B])
        assertEquals(5.SGD, newLimits[C])
        assertEquals(2.SGD, newLimits[D])
        assertEquals(9.SGD, newLimits[E])
    }

    @Test
    fun `Settlement from Functional Test F6_1`() {
        val (A, B, C, D, E) = getNodes(5)

        // obligations
        val obligationT1 = obligation(C, B, 30)
        val obligationT2 = obligation(D, C, 20)
        val obligationT3 = obligation(A, B, 5)
        val obligationT4 = obligation(B, C, 6)
        val obligationT5 = obligation(C, D, 8)
        val obligationT6 = obligation(D, E, 7)
        val obligationT7 = obligation(E, A, 5)

        val obligations = setOfObligations(
                obligationT1, obligationT2, obligationT3,
                obligationT4, obligationT5, obligationT6, obligationT7
        )

        val limits: Map<AbstractParty, Amount<Currency>> = mapOf(
                A to 3.SGD,
                B to 4.SGD,
                C to 5.SGD,
                D to 4.SGD,
                E to 3.SGD)
        val flow = NewPlanFlow(obligations, limits, NettingMode.BestEffort)

        val (paymentsToMake, resultantObligations) = bank1.services.startFlow(flow).resultFuture.getOrThrow()

        assertEquals(5, countObligations(resultantObligations))
        assertEquals(2, paymentsToMake.size)

        val newLimits = applyPaymentsToLimits(limits, paymentsToMake)
        assertEquals(3.SGD, newLimits[A])
        assertEquals(3.SGD, newLimits[B])
        assertEquals(3.SGD, newLimits[C])
        assertEquals(5.SGD, newLimits[D])
        assertEquals(5.SGD, newLimits[E])
    }

    @Test
    fun `Settlement from Functional Test F6_2`() {
        val (A, B, C, D, E) = getNodes(5)

        // obligations
        val obligationT1 = obligation(A, E, 8)
        val obligationT2 = obligation(E, D, 9)
        val obligationT3 = obligation(D, A, 10)
        val obligationT4 = obligation(A, B, 15)
        val obligationT5 = obligation(B, C, 6)
        val obligationT6 = obligation(C, A, 7)

        val obligations = setOfObligations(
                obligationT1, obligationT2, obligationT3,
                obligationT4, obligationT5, obligationT6
        )

        val limits: Map<AbstractParty, Amount<Currency>> = mapOf(
                A to 3.SGD,
                B to 4.SGD,
                C to 5.SGD,
                D to 4.SGD,
                E to 3.SGD)
        val flow = NewPlanFlow(obligations, limits, NettingMode.BestEffort)

        val (paymentsToMake, resultantObligations) = bank1.services.startFlow(flow).resultFuture.getOrThrow()

        assertEquals(3, countObligations(resultantObligations))
        assertEquals(2, paymentsToMake.size)

        val newLimits = applyPaymentsToLimits(limits, paymentsToMake)
        assertEquals(5.SGD, newLimits[A])
        assertEquals(4.SGD, newLimits[B])
        assertEquals(5.SGD, newLimits[C])
        assertEquals(3.SGD, newLimits[D])
        assertEquals(2.SGD, newLimits[E])
    }

    @Test
    fun `Settlement from Functional Test F6_3`() {
        val (A, B, C, D, E) = getNodes(5)

        // obligations
        val obligationT1 = obligation(A, B, 5)
        val obligationT2 = obligation(B, C, 6)
        val obligationT3 = obligation(B, C, 30)
        val obligationT4 = obligation(C, D, 8)
        val obligationT5 = obligation(C, E, 80)
        val obligationT6 = obligation(D, E, 7)
        val obligationT7 = obligation(A, C, 6)
        val obligationT8 = obligation(E, A, 8)
        val obligationT9 = obligation(E, B, 100)
        val obligationT10 = obligation(D, A, 5)

        val obligations = setOfObligations(
                obligationT1, obligationT2, obligationT3, obligationT4,
                obligationT5, obligationT6, obligationT7, obligationT8,
                obligationT9, obligationT10
        )

        val limits: Map<AbstractParty, Amount<Currency>> = mapOf(
                A to 3.SGD,
                B to 4.SGD,
                C to 5.SGD,
                D to 4.SGD,
                E to 3.SGD)
        val flow = NewPlanFlow(obligations, limits, NettingMode.BestEffort)

        val (paymentsToMake, resultantObligations) = bank1.services.startFlow(flow).resultFuture.getOrThrow()
        assertEquals(7, countObligations(resultantObligations))
        assertEquals(3, paymentsToMake.size)

        val newLimits = applyPaymentsToLimits(limits, paymentsToMake)
        assertEquals(5.SGD, newLimits[A])
        assertEquals(3.SGD, newLimits[B])
        assertEquals(9.SGD, newLimits[C])
        assertEquals(0.SGD, newLimits[D])
        assertEquals(2.SGD, newLimits[E])
    }

    @Test
    fun `Settlement from Functional Test F7_1`() {
        val (A, B, C, D, E) = getNodes(5)

        // obligations
        val obligationT1 = obligation(A, B, 8)
        val obligationT2 = obligation(B, C, 10)
        val obligationT3 = obligation(B, C, 20)
        val obligationT4 = obligation(C, D, 30)
        val obligationT5 = obligation(C, E, 40)

        val obligations = setOfObligations(
                obligationT1, obligationT2, obligationT3,
                obligationT4, obligationT5
        )

        val limits: Map<AbstractParty, Amount<Currency>> = mapOf(
                A to 3.SGD,
                B to 4.SGD,
                C to 5.SGD,
                D to 4.SGD,
                E to 3.SGD)
        val flow = NewPlanFlow(obligations, limits, NettingMode.BestEffort)

        val (paymentsToMake, resultantObligations) = bank1.services.startFlow(flow).resultFuture.getOrThrow()
        assertEquals(0, countObligations(resultantObligations))
        assertEquals(0, paymentsToMake.size)

        val newLimits = applyPaymentsToLimits(limits, paymentsToMake)
        assertEquals(3.SGD, newLimits[A])
        assertEquals(4.SGD, newLimits[B])
        assertEquals(5.SGD, newLimits[C])
        assertEquals(4.SGD, newLimits[D])
        assertEquals(3.SGD, newLimits[E])
    }

    @Test
    fun `Settlement from Functional Test F7_2`() {
        val (A, B, C, D, E) = getNodes(5)

        // obligations
        val obligationT1 = obligation(E, A, 8)
        val obligationT2 = obligation(E, D, 9)
        val obligationT3 = obligation(D, E, 15)
        val obligationT4 = obligation(A, B, 14)
        val obligationT5 = obligation(B, C, 15)
        val obligationT6 = obligation(C, A, 10)

        val obligations = setOfObligations(
                obligationT1, obligationT2, obligationT3,
                obligationT4, obligationT5, obligationT6
        )

        val limits: Map<AbstractParty, Amount<Currency>> = mapOf(
                A to 3.SGD,
                B to 4.SGD,
                C to 5.SGD,
                D to 4.SGD,
                E to 3.SGD)
        val flow = NewPlanFlow(obligations, limits, NettingMode.BestEffort)

        val (paymentsToMake, resultantObligations) = bank1.services.startFlow(flow).resultFuture.getOrThrow()
        assertEquals(0, countObligations(resultantObligations))
        assertEquals(0, paymentsToMake.size)

        val newLimits = applyPaymentsToLimits(limits, paymentsToMake)
        assertEquals(3.SGD, newLimits[A])
        assertEquals(4.SGD, newLimits[B])
        assertEquals(5.SGD, newLimits[C])
        assertEquals(4.SGD, newLimits[D])
        assertEquals(3.SGD, newLimits[E])
    }

    @Test
    fun `Settlement from Functional Test F7_3`() {
        val (A, B, C, D, E) = getNodes(5)

        // obligations
        val obligationT1 = obligation(A, B, 15)
        val obligationT2 = obligation(B, C, 6)
        val obligationT3 = obligation(C, A, 7)
        val obligationT4 = obligation(A, E, 8)
        val obligationT5 = obligation(E, D, 9)
        val obligationT6 = obligation(D, A, 20)

        val obligations = setOfObligations(
                obligationT1, obligationT2, obligationT3,
                obligationT4, obligationT5, obligationT6
        )

        val limits: Map<AbstractParty, Amount<Currency>> = mapOf(
                A to 3.SGD,
                B to 4.SGD,
                C to 5.SGD,
                D to 4.SGD,
                E to 3.SGD)
        val flow = NewPlanFlow(obligations, limits, NettingMode.BestEffort)

        val (paymentsToMake, resultantObligations) = bank1.services.startFlow(flow).resultFuture.getOrThrow()
        assertEquals(0, countObligations(resultantObligations))
        assertEquals(0, paymentsToMake.size)

        val newLimits = applyPaymentsToLimits(limits, paymentsToMake)
        assertEquals(3.SGD, newLimits[A])
        assertEquals(4.SGD, newLimits[B])
        assertEquals(5.SGD, newLimits[C])
        assertEquals(4.SGD, newLimits[D])
        assertEquals(3.SGD, newLimits[E])
    }

    @Test
    fun `Settlement from Functional Test F7_4`() {
        val (A, B, C, D, E) = getNodes(5)

        // obligations
        val obligationT1 = obligation(A, B, 30)
        val obligationT2 = obligation(B, C, 5)
        val obligationT3 = obligation(C, A, 6)
        val obligationT4 = obligation(A, E, 7)
        val obligationT5 = obligation(E, D, 4)
        val obligationT6 = obligation(D, A, 5)

        val obligations = setOfObligations(
                obligationT1, obligationT2, obligationT3,
                obligationT4, obligationT5, obligationT6
        )

        val limits: Map<AbstractParty, Amount<Currency>> = mapOf(
                A to 3.SGD,
                B to 4.SGD,
                C to 5.SGD,
                D to 4.SGD,
                E to 3.SGD)
        val flow = NewPlanFlow(obligations, limits, NettingMode.BestEffort)

        val (paymentsToMake, resultantObligations) = bank1.services.startFlow(flow).resultFuture.getOrThrow()
        assertEquals(3, countObligations(resultantObligations))
        assertEquals(2, paymentsToMake.size)

        val newLimits = applyPaymentsToLimits(limits, paymentsToMake)
        assertEquals(1.SGD, newLimits[A])
        assertEquals(4.SGD, newLimits[B])
        assertEquals(5.SGD, newLimits[C])
        assertEquals(3.SGD, newLimits[D])
        assertEquals(6.SGD, newLimits[E])
    }

    @Test
    fun `The 'exceptional' test`() {
        val nodes = getNodes(11)
        val A = nodes[0]
        val B = nodes[1]
        val C = nodes[2]
        val D = nodes[3]
        val E = nodes[4]
        val F = nodes[5]
        val G = nodes[6]
        val H = nodes[7]
        val I = nodes[8]
        val J = nodes[9]
        val K = nodes[10]

        // obligations
        val obligationT1 = obligation(A, B, 500)
        val obligationT2 = obligation(B, C, 10)
        val obligationT3 = obligation(B, D, 20)
        val obligationT4 = obligation(B, E, 30)
        val obligationT5 = obligation(B, F, 40)
        val obligationT6 = obligation(B, G, 50)
        val obligationT7 = obligation(B, H, 60)
        val obligationT8 = obligation(B, I, 70)
        val obligationT9 = obligation(B, J, 80)
        val obligationT10 = obligation(B, K, 140)
        val obligationT11 = obligation(C, A, 60)
        val obligationT12 = obligation(D, A, 70)
        val obligationT13 = obligation(E, A, 80)
        val obligationT14 = obligation(F, A, 90)
        val obligationT15 = obligation(G, A, 200)
        val obligationT16 = obligation(H, C, 50)
        val obligationT17 = obligation(I, D, 50)
        val obligationT18 = obligation(J, E, 50)
        val obligationT19 = obligation(K, F, 50)
        val obligationT20 = obligation(J, G, 10)
        val obligationT21 = obligation(K, G, 140)
        val obligationT22 = obligation(H, K, 10)
        val obligationT23 = obligation(I, K, 20)
        val obligationT24 = obligation(J, K, 30)
        val obligationT25 = obligation(K, J, 10)

        val obligations = setOfObligations(
            obligationT1, obligationT2, obligationT3, obligationT4, obligationT5,
            obligationT6, obligationT7, obligationT8, obligationT9, obligationT10,
            obligationT11, obligationT12, obligationT13, obligationT14, obligationT15,
            obligationT16, obligationT17, obligationT18, obligationT19, obligationT20,
            obligationT21, obligationT22, obligationT23, obligationT24, obligationT25
        )

        val limits: Map<AbstractParty, Amount<Currency>> = mapOf(
            A to 2000.SGD,
            B to 2000.SGD,
            C to 2000.SGD,
            D to 2000.SGD,
            E to 2000.SGD,
            F to 2000.SGD,
            G to 2000.SGD,
            H to 2000.SGD,
            I to 2000.SGD,
            J to 2000.SGD,
            K to 2000.SGD)
        val flow = NewPlanFlow(obligations, limits, NettingMode.BestEffort)

        val (paymentsToMake, resultantObligations) = bank1.services.startFlow(flow).resultFuture.getOrThrow()

        assertEquals(25, countObligations(resultantObligations))
        assertEquals(0, paymentsToMake.size)

        println(paymentsToMake)
        println(resultantObligations)

        val newLimits = applyPaymentsToLimits(limits, paymentsToMake)
        newLimits.forEach { assertEquals(it.value, 2000.SGD) }

        println(newLimits)
    }

    @Test
    fun `Multiple cycles detected`() {
        val nodes = getNodes(6)
        val A = nodes[0]
        val B = nodes[1]
        val C = nodes[2]
        val D = nodes[3]
        val E = nodes[4]
        val F = nodes[5]

        // obligations
        val obligationT1 = obligation(A, B, 10)
        val obligationT2 = obligation(B, C, 20)
        val obligationT3 = obligation(C, A, 30)
        val obligationT4 = obligation(B, D, 1)
        val obligationT5 = obligation(D, E, 20)
        val obligationT6 = obligation(E, F, 40)
        val obligationT7 = obligation(F, D, 50)

        val obligations = setOfObligations(
            obligationT1, obligationT2, obligationT3, obligationT4,
            obligationT5, obligationT6, obligationT7
        )

        val limits: Map<AbstractParty, Amount<Currency>> = mapOf(
            A to 30.SGD,
            B to 30.SGD,
            C to 30.SGD,
            D to 30.SGD,
            E to 30.SGD,
            F to 30.SGD)
        val flow = NewPlanFlow(obligations, limits, NettingMode.BestEffort)

        val (paymentsToMake, resultantObligations) = bank1.services.startFlow(flow).resultFuture.getOrThrow()
//        assertEquals(6, countObligations(resultantObligations))
        assertEquals(4, paymentsToMake.size)

        val newLimits = applyPaymentsToLimits(limits, paymentsToMake)
        assertEquals(50.SGD, newLimits[A])
        assertEquals(20.SGD, newLimits[B])
        assertEquals(20.SGD, newLimits[C])
        assertEquals(60.SGD, newLimits[D])
        assertEquals(10.SGD, newLimits[E])
        assertEquals(20.SGD, newLimits[F])
    }

    @Test
    fun `Insert arbitrary zero-edges`() {
        val (A, B, C, D, E, F, G, H, I, J, K) = getNodes(11)

        val obligationT1 = obligation(A, B, 6)
        val obligationT2 = obligation(B, C, 4)
        val obligationT3 = obligation(C, A, 2)
        val obligationT4 = obligation(C, D, 2)
        val obligationT5 = obligation(D, E, 5)
        val obligationT6 = obligation(E, F, 2)
        val obligationT7 = obligation(F, D, 2)
        val obligationT8 = obligation(I, E, 1)
        val obligationT9 = obligation(G, H, 10)
        val obligationT10 = obligation(H, G, 8)
        val obligationT11 = obligation(J, K, 2)

        val obligations = setOfObligations(
            obligationT1, obligationT2, obligationT3, obligationT4,
            obligationT5, obligationT6, obligationT7, obligationT8,
            obligationT9, obligationT10, obligationT11
        )

        val limits: Map<AbstractParty, Amount<Currency>> = mapOf(
            A to 5.SGD,
            B to 3.SGD,
            C to 1.SGD,
            D to 2.SGD,
            E to 4.SGD,
            F to 2.SGD,
            G to 4.SGD,
            H to 5.SGD,
            I to 2.SGD,
            J to 4.SGD,
            K to 2.SGD)
        val flow = NewPlanFlow(obligations, limits, NettingMode.BestEffort, extendComponents = true)

        val (paymentsToMake, resultantObligations) = bank1.services.startFlow(flow).resultFuture.getOrThrow()
        assertEquals(11, countObligations(resultantObligations))
        assertEquals(5, paymentsToMake.size)

        val newLimits = applyPaymentsToLimits(limits, paymentsToMake)
        assertEquals(1.SGD, newLimits[A])
        assertEquals(5.SGD, newLimits[B])
        assertEquals(1.SGD, newLimits[C])
        assertEquals(1.SGD, newLimits[D])
        assertEquals(8.SGD, newLimits[E])
        assertEquals(2.SGD, newLimits[F])
        assertEquals(2.SGD, newLimits[G])
        assertEquals(7.SGD, newLimits[H])
        assertEquals(1.SGD, newLimits[I])
        assertEquals(2.SGD, newLimits[J])
        assertEquals(4.SGD, newLimits[K])
    }
}