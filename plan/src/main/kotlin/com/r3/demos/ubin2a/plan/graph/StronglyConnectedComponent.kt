package com.r3.demos.ubin2a.plan.graph

import java.io.InvalidObjectException

data class StronglyConnectedComponent<T>(val graph: DirectedGraph<T>, private val graphNodes: List<GraphNode<T>>) {
    val nodes = graphNodes.map { it.id }
    val hasNodes = nodes.isNotEmpty()

    override fun toString(): String {
        return "Component(${nodes.joinToString(", ")})"
    }

    fun merge(otherComponent: StronglyConnectedComponent<T>): StronglyConnectedComponent<T> {
        if (graph !== otherComponent.graph) {
            throw InvalidObjectException("Can only merge strongly connected components on the same graph")
        }
        return StronglyConnectedComponent(graph, graphNodes.union(otherComponent.graphNodes).toList())
    }
}
