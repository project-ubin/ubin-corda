package com.r3.demos.ubin2a.plan.solver

import com.r3.demos.ubin2a.base.SGD
import com.r3.demos.ubin2a.plan.NettingPayment
import com.r3.demos.ubin2a.plan.NetworkTests
import com.r3.demos.ubin2a.plan.generator.ObligationEdge
import com.r3.demos.ubin2a.plan.getObligationsMap
import net.corda.core.contracts.Amount
import net.corda.core.identity.AbstractParty
import org.junit.Test
import org.slf4j.helpers.NOPLogger
import java.util.*
import kotlin.test.assertEquals

class NettingOptimisationTests : NetworkTests() {
    class MockComponentOptimiser(private val result: Pair<List<NettingPayment>, List<ObligationEdge>>) : NettingOptimiser {
        override fun optimise(): Pair<List<NettingPayment>, List<ObligationEdge>> {
            return result
        }
    }

    @Test
    fun testPerComponentLogic() {
        val (A, B, C) = getNodes(3)

        // Create some obligations.
        val obligationA = obligation(A, B, 100)
        val obligationB = obligation(B, C, 100)
        val obligationC = obligation(C, A, 100)

        // The cash each node has pledged for the netting round. Again, using 'Party' instead of 'AnonymousParty'
        val limits: Map<AbstractParty, Amount<Currency>> = mapOf(
            A to 1000.SGD,
            B to 1500.SGD,
            C to 2000.SGD)
        // this doesn't make sense from a graph point of view - but it doesn't have to as
        // all it's supposed to test is the looping/aggregation in the optimiser class
        val cyclesPerComponent = setOf(
            setOf(listOf(A, B), listOf(A, C)),
            setOf(listOf(B, C)))
        val obligationsMap = getObligationsMap(listOf(
            obligationA.state,
            obligationB.state,
            obligationC.state
        ))

        val testLogger = NOPLogger.NOP_LOGGER
        val optimiser = NettingOptimiserImpl(cyclesPerComponent, obligationsMap, limits, testLogger,
            { cycles, _, _, _ ->
                MockComponentOptimiser(Pair(
                    listOf(NettingPayment(cycles.first().first(), cycles.first().last(), 100.SGD)),
                    emptyList()
                ))
            })

        val result = optimiser.optimise()
        assertEquals(NettingPayment(A, B, 100.SGD), result.first.first())
        assertEquals(NettingPayment(B, C, 100.SGD), result.first.last())
    }

}
