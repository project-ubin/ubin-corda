package com.r3.demos.ubin2a.plan.generator

import com.r3.demos.ubin2a.obligation.Obligation
import net.corda.core.contracts.UniqueIdentifier

open class ObligationEdge(val linearIds: List<UniqueIdentifier>)

class ObligationGroup(linearIds: List<UniqueIdentifier>) : ObligationEdge(linearIds)

class ObligationId(val linearId: UniqueIdentifier) : ObligationEdge(listOf(linearId))

fun obligationEdgeOf(vararg obligations: Obligation.State): ObligationEdge {
    return obligationEdgeOf(obligations.asList())
}

fun obligationEdgeOf(obligations: List<Obligation.State>): ObligationEdge {
    return if (obligations.size == 1) {
        ObligationId(obligations.single().linearId)
    } else {
        ObligationGroup(obligations.map { it.linearId })
    }
}

fun countObligations(obligations: List<ObligationEdge>): Int {
    return obligations.flatMap { it.linearIds }.toSet().count()
}
