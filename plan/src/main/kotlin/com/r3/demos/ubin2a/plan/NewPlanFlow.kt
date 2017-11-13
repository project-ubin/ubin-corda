package com.r3.demos.ubin2a.plan

import co.paralleluniverse.fibers.Suspendable
import com.r3.demos.ubin2a.obligation.Obligation
import com.r3.demos.ubin2a.plan.generator.ObligationEdge
import com.r3.demos.ubin2a.plan.solver.ComponentCycleSolver
import com.r3.demos.ubin2a.plan.solver.ComponentNettingOptimiser
import com.r3.demos.ubin2a.plan.solver.NettingOptimiserImpl
import net.corda.core.contracts.Amount
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AbstractParty
import java.util.*

data class NettingPayment(val from: AbstractParty, val to: AbstractParty, val amount: Amount<Currency>) {

    override fun toString(): String {
        return "NettingPayment(" +
            "from=${from.nameOrNull()!!.organisation}, " +
            "to=${to.nameOrNull()!!.organisation}, " +
            "amount=$amount)"
    }

}

@InitiatingFlow
@StartableByRPC
class NewPlanFlow(val obligations: Set<Obligation.State>,
                  val limits: Map<AbstractParty, Amount<Currency>>, // Would usually just take 'AnonymousParty'.
                  private val nettingMode: ComponentCycleSolver,
                  private val extendComponents: Boolean = false
) : FlowLogic<Pair<List<NettingPayment>, List<ObligationEdge>>>() {
    @Suspendable
    override fun call(): Pair<List<NettingPayment>, List<ObligationEdge>> {
        val (_, obligationsMap, cycles) = obligationsGraphFrom(obligations.toList(), extendComponents)
        val optimiser = NettingOptimiserImpl(cycles, obligationsMap, limits, logger, { c, o, l, lg ->
            ComponentNettingOptimiser(c, o, l, lg, nettingMode)
        })
        return optimiser.optimise()
    }
}
