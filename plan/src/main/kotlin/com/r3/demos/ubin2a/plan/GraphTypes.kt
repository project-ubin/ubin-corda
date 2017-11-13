package com.r3.demos.ubin2a.plan

import com.r3.demos.ubin2a.plan.graph.DirectedGraph
import com.r3.demos.ubin2a.plan.graph.detectSimpleCycles
import com.r3.demos.ubin2a.plan.graph.detectStronglyConnectedComponents
import com.r3.demos.ubin2a.plan.graph.extendComponents
import net.corda.core.identity.AbstractParty

typealias Cycle = com.r3.demos.ubin2a.plan.graph.Cycle<AbstractParty>
typealias Edge = com.r3.demos.ubin2a.plan.graph.Edge<AbstractParty>
typealias Graph = DirectedGraph<AbstractParty>

fun directedGraphOf(obligations: Obligations): Graph {
    if (obligations.isEmpty()) {
        throw IllegalArgumentException("Empty list of obligations")
    }
    return DirectedGraph(obligations.map { Edge(it.lender, it.borrower) })
}

fun findCyclesInGraph(graph: Graph): Set<Set<Cycle>> {
    val stronglyConnectedComponents = detectStronglyConnectedComponents(graph)
    return stronglyConnectedComponents
        .filter { it.nodes.count() > 1 }
        .map { detectSimpleCycles(it) }.toSet()
}

fun findExtendedComponentCycles(graph:Graph):Set<Set<Cycle>>{
    val stronglyConnectedComponents = extendComponents(detectStronglyConnectedComponents(graph))
    return stronglyConnectedComponents
            .filter { it.nodes.count() > 1 }
            .map { detectSimpleCycles(it) }.toSet()
}
