package com.r3.demos.ubin2a.plan

import com.r3.demos.ubin2a.obligation.Obligation
import net.corda.core.identity.AbstractParty

typealias Obligations = List<Obligation.State>
typealias ObligationsMap = Map<Edge, Obligations>

fun getObligationsMap(obligations: Obligations): ObligationsMap {
    val edges = obligations.map { Pair(Edge(it.lender, it.borrower), it) }
    return edges.groupBy { it.first }.mapValues { it.value.map { it.second } }
}

fun ObligationsMap.obligationsForEdge(lender: AbstractParty, borrower: AbstractParty): Obligations {
    return this[Edge(lender, borrower)].orEmpty()
}

fun ObligationsMap.allEntries(): List<Obligation.State> {
    return this.values.flatMap { it }
}

data class ObligationsGraph(val graph: Graph, val obligations: ObligationsMap, val cycles: Set<Set<Cycle>>)

fun obligationsGraphFrom(listOfObligations: Obligations, useExtendedComponents: Boolean = false): ObligationsGraph {
    val graph = directedGraphOf(listOfObligations)
    val obligations = getObligationsMap(listOfObligations)
    val cycles = if (useExtendedComponents)  findExtendedComponentCycles(graph) else findCyclesInGraph(graph)
    return ObligationsGraph(graph, obligations, cycles)
}
