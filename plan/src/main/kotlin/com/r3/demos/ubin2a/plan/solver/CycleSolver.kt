package com.r3.demos.ubin2a.plan.solver

import com.r3.demos.ubin2a.plan.Cycle
import com.r3.demos.ubin2a.plan.Edge
import com.r3.demos.ubin2a.plan.Obligations
import net.corda.core.contracts.Amount
import net.corda.core.identity.AbstractParty
import java.util.*

data class CycleResult(val settledObligations: Obligations,
                       val newLimits: Map<AbstractParty, Amount<Currency>>)

class NettingFailure(message: String?) : Throwable(message)

interface ComponentCycleSolver {
    fun solveCycles(cycles: List<Cycle>, obligations: Map<Edge, Obligations>, limits: Map<AbstractParty, Amount<Currency>>)
        : CycleResult
}

interface CycleSolver {
    fun solveCycle(cycle: Cycle, obligations: Map<Edge, Obligations>, limits: Map<AbstractParty, Amount<Currency>>)
        : CycleResult
}