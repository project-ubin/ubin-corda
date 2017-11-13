package com.r3.demos.ubin2a.plan.solver

import com.r3.demos.ubin2a.plan.Cycle
import com.r3.demos.ubin2a.plan.Edge
import com.r3.demos.ubin2a.plan.NettingPayment
import com.r3.demos.ubin2a.plan.Obligations
import com.r3.demos.ubin2a.plan.generator.ObligationEdge
import net.corda.core.contracts.Amount
import net.corda.core.identity.AbstractParty
import org.slf4j.Logger
import java.util.*

interface NettingOptimiser {
    fun optimise(): Pair<List<NettingPayment>, List<ObligationEdge>>
}

class ComponentNettingOptimiser(
    val cycles: Set<Cycle>,
    val obligations: Map<Edge, Obligations>,
    val limits: Map<AbstractParty, Amount<Currency>>,
    val logger: Logger,
    val cycleSolver: ComponentCycleSolver = LargestSumCycleOnlySolverImpl(),
    val cashflowSolver: CashflowSolver = CashflowSolverImpl()
) : NettingOptimiser {

    override fun optimise(): Pair<List<NettingPayment>, List<ObligationEdge>> {
        if (cycles.isEmpty()) {
            return Pair(emptyList(), emptyList())
        }
        try {
            val cycleResult = cycleSolver.solveCycles(cycles.toList(), obligations, limits)
            if (cycleResult.settledObligations.isNotEmpty()) {
                val netResult = cashflowSolver.solveForNettingCashflows(cycleResult, obligations, limits)
                return Pair(netResult.payments, netResult.obligations)
            }
        } catch (e: NettingFailure) {
            logger.error("Netting failed: {0}".format(e))
        }
        return Pair(emptyList(), emptyList())
    }
}

class NettingOptimiserImpl(val cyclesPerComponent: Set<Set<Cycle>>,
                           val obligations: Map<Edge, Obligations>,
                           val limits: Map<AbstractParty, Amount<Currency>>,
                           val logger: Logger,
                           val factoryMethod: (Set<Cycle>, Map<Edge, Obligations>, Map<AbstractParty, Amount<Currency>>, Logger) -> NettingOptimiser
                           = { c, o, l, lg -> ComponentNettingOptimiser(c, o, l, lg) })
    : NettingOptimiser {
    override fun optimise(): Pair<List<NettingPayment>, List<ObligationEdge>> {
        val result = Pair(mutableListOf<NettingPayment>(), mutableListOf<ObligationEdge>())
        cyclesPerComponent.forEach {
            val res = factoryMethod(it, obligations, limits, logger).optimise()
            result.first.addAll(res.first)
            result.second.addAll(res.second)
        }
        return result
    }
}
