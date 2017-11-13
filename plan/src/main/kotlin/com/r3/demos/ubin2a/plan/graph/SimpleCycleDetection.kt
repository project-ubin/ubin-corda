package com.r3.demos.ubin2a.plan.graph

import java.util.*

/**
 * Find all simple cycles in a strongly connected component using Johnson's algorithm.
 */
fun <T> detectSimpleCycles(stronglyConnectedComponent: StronglyConnectedComponent<T>): Set<Cycle<T>> {
    return SimpleCycleDetection(stronglyConnectedComponent).cycles
}

private class SimpleCycleDetection<T>(val stronglyConnectedComponent: StronglyConnectedComponent<T>) {
    private val stack = Stack<T>()
    private val blockedSet = mutableSetOf<T>()
    private val blockedMap = mutableMapOf<T, MutableSet<T>>()
    private val removedNodes = mutableSetOf<T>()
    private val detectedCycles = mutableSetOf<Cycle<T>>()

    init {
        for (node in stronglyConnectedComponent.nodes) {
            blockedMap.put(node, mutableSetOf())
        }
    }

    /**
     * Find simple cycles in strongly connected component.
     */
    val cycles: Set<Cycle<T>> by lazy {
        for (startNode in stronglyConnectedComponent.nodes) {
            findCycles(startNode)
            blockedMap.values.forEach { it.clear() }
            removedNodes.add(startNode)
        }
        detectedCycles
    }

    /**
     * Recurse through strongly connected component to find simple cycles.
     */
    private fun findCycles(startNode: T, node: T? = null): Boolean {
        var foundCycle = false
        val currentNode = node ?: startNode
        pushAndBlock(currentNode)

        for (connectedNode in findNeighboursOf(currentNode)) {
            if (connectedNode == startNode) {
                // This concludes a simple cycle, so let's record it and move on
                recordCycle()
                foundCycle = true
            } else if (!blockedSet.contains(connectedNode)) {
                // If already in the blocked set then there's no point in going down this path.
                // The cycle will no longer be a simple cycle if the node gets repeated.
                if (findCycles(startNode, connectedNode)) {
                    foundCycle = true
                }
            }
        }

        if (foundCycle) {
            // Unblock current node and all dependencies (through `blockedMap`)
            unblock(currentNode)
        } else {
            // Update map of blocked nodes so that we can unblock the current node if any of the
            // neighbours get unblocked.
            for (connectedNode in findNeighboursOf(currentNode)) {
                blockedMap[connectedNode]!!.add(currentNode)
            }
        }

        assert(stack.pop() === currentNode)
        return foundCycle
    }

    /**
     * Block node and push onto stack.
     */
    private fun pushAndBlock(node: T) {
        stack.push(node)
        blockedSet.add(node)
    }

    /**
     * Recursively unblock node and dependencies.
     */
    private fun unblock(node: T) {
        blockedSet.remove(node)
        blockedMap[node]!!.forEach {
            if (blockedSet.contains(it)) {
                unblock(it)
            }
        }
        blockedMap[node]!!.clear()
    }

    /**
     * Record the current content of the stack as a cycle.
     */
    private fun recordCycle() {
        val cycleList = stack.toMutableList()
        cycleList.add(cycleList.first())
        detectedCycles.add(cycleList)
    }

    /**
     * Find connected neighbours of node (minus those already processed).
     */
    private fun findNeighboursOf(node: T): Iterable<T> {
        return stronglyConnectedComponent.graph.edgesForNode(node)
            .map { it.id }
            .filter { stronglyConnectedComponent.nodes.contains(it) }
            .filter { !removedNodes.contains(it) }
    }
}
