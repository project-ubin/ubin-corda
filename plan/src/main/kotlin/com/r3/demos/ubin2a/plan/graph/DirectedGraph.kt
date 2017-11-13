package com.r3.demos.ubin2a.plan.graph

data class DirectedGraph<T>(private val edges: List<Edge<T>>) {
    val nodes = edges.flatMap { listOf(it.from, it.to) }.toSet().map { GraphNode(it) }
    private val nodeMap = nodes.map { Pair(it.id, it) }.toMap()
    private val edgeMap = edges.groupBy { it.from }.toMutableMap()

    fun insertEdge(from: T, to: T) {
        if (from !in nodeMap || to !in nodeMap) {
            throw IllegalArgumentException("Trying to insert edge for unknown vertex")
        }

        val fromList = edgeMap[from]
        if (fromList == null) {
            edgeMap[from] = listOf(Edge(from, to))
        } else {
            edgeMap[from] = fromList.union(listOf(Edge(from, to))).toList()
        }
    }

    fun undefinedNodes(): Iterable<GraphNode<T>> {
        return nodes.filter { it.hasUndefinedIndex() }
    }

    fun edgesForNode(node: T): List<GraphNode<T>> {
        return edgeMap.getOrDefault(node, listOf()).map { nodeMap[it.to]!! }
    }
}
