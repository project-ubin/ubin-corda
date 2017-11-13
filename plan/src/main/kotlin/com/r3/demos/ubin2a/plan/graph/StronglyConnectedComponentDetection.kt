package com.r3.demos.ubin2a.plan.graph

import java.util.*

/**
 * Use Tarjan's strongly connected components algorithm to find all strongly connected components in a graph.
 */
fun <T> detectStronglyConnectedComponents(graph: DirectedGraph<T>): List<StronglyConnectedComponent<T>> {
    return StronglyConnectedComponentDetection(graph).components
}

private class StronglyConnectedComponentDetection<T>(val graph: DirectedGraph<T>) {
    private val componentsList = mutableListOf<StronglyConnectedComponent<T>>()
    private val exploredEdges = mutableSetOf<Pair<GraphNode<T>, GraphNode<T>>>()
    private val stack = Stack<GraphNode<T>>()
    private var index = 0

    /**
     * For each node in the directed graph, find strongly connected components.
     */
    val components: List<StronglyConnectedComponent<T>> by lazy {
        graph.undefinedNodes().forEach { connect(it) }
        componentsList
    }

    /**
     * Recursively find strongly connected component.
     */
    private fun connect(node: GraphNode<T>) {
        node.index = index
        node.lowLink = index
        index += 1
        stack.push(node)

        var processedEdges = false
        for (connectedNode in graph.edgesForNode(node.id)) {
            // Instead of relying on traversing the nodes in a specific order,
            // keep track of what edges have been visited
            exploreEdge(node, connectedNode) {
                processedEdges = true
                if (connectedNode.hasUndefinedIndex()) {
                    connect(connectedNode)
                    node.lowLink = minOf(node.lowLink, connectedNode.lowLink)
                } else if (stack.contains(connectedNode)) {
                    // On the stack -> in the current strongly connected component
                    node.lowLink = minOf(node.lowLink, connectedNode.index)
                }
            }
        }

        if (node.lowLink == node.index) {
            // At the root node, so add strongly connected component to result
            val component = StronglyConnectedComponent(graph, stackNodes(node))
            if (component.hasNodes && processedEdges) {
                componentsList.add(component)
            }
        }
    }

    /**
     * Get nodes on stack belonging to the current strongly connected component.
     */
    private fun stackNodes(node: GraphNode<T>): List<GraphNode<T>> {
        val nodes = mutableListOf<GraphNode<T>>()
        do {
            val stackNode = stack.pop()
            nodes.add(stackNode)
        } while (stackNode != node)
        return nodes
    }

    /**
     * Execute action if and only if the provided edge has not yet been explored.
     */
    private fun exploreEdge(from: GraphNode<T>, to: GraphNode<T>, action: () -> Unit) {
        val edge = Pair(from, to)
        if (!exploredEdges.contains(edge)) {
            exploredEdges.add(edge)
            action()
        }
    }
}
