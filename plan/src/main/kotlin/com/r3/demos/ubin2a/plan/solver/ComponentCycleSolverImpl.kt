package com.r3.demos.ubin2a.plan.solver

import com.r3.demos.ubin2a.obligation.Obligation
import com.r3.demos.ubin2a.plan.Cycle
import com.r3.demos.ubin2a.plan.Edge
import com.r3.demos.ubin2a.plan.Obligations
import net.corda.core.contracts.Amount
import net.corda.core.identity.AbstractParty
import java.util.*

class ComponentCycleSolverImpl(private val cycleSolver: CycleSolver = CycleSolverImpl()) : ComponentCycleSolver {
    override fun solveCycles(cycles: List<Cycle>, obligations: Map<Edge, Obligations>, limits: Map<AbstractParty, Amount<Currency>>): CycleResult {
        val settledObligations = mutableListOf<Obligation.State>()
        var newLimits = limits.toMap()
        var localObligations = obligations.toMap()

        var haveChanges = true

        // we need to loop over the cycles until nothing can be settled anymore,
        // as cycles that were initially unsolvable might become solvable after some other cycles
        // have been solved
        while (haveChanges) {
            haveChanges = false
            for (cycle in cycles) {
                val res = cycleSolver.solveCycle(cycle, localObligations, newLimits)
                if (res.settledObligations.isNotEmpty()) {
                    settledObligations.addAll(res.settledObligations)
                    localObligations = localObligations.map({
                        Pair(it.key, it.value.filter { it !in res.settledObligations })
                    })
                        .filter { it.second.isNotEmpty() }.toMap()
                    haveChanges = true
                    newLimits = res.newLimits
                }
            }
        }
        return CycleResult(settledObligations, newLimits)
    }
}

class LargestSumCycleOnlySolverImpl(private val cycleSolver: CycleSolver = CycleSolverImpl()) : ComponentCycleSolver {
    override fun solveCycles(cycles: List<Cycle>, obligations: Map<Edge, Obligations>, limits: Map<AbstractParty, Amount<Currency>>): CycleResult {
        val cycleResults = cycles.map { cycleSolver.solveCycle(it, obligations, limits) }
        return cycleResults.maxBy { it.settledObligations.map { it.amount.quantity }.sum() } ?: CycleResult(emptyList(), emptyMap())
    }
}