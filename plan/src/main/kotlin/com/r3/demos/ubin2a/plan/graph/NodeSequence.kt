package com.r3.demos.ubin2a.plan.graph

/**
 * Representation of a cyclic sequence of graph nodes.
 */
class NodeSequence<out T>(nodeList: List<T>) : Iterable<T> {
    private val elements: List<T> =
        when {
            nodeList.isEmpty() -> emptyList()
            nodeList.last() == nodeList.first() -> nodeList
            else -> throw IllegalArgumentException(
                "Last element expected to be ${nodeList.first()}, found ${nodeList.last()}")
        }

    val size = elements.size - 1

    operator fun get(index: Int): T = elements[index % elements.size]

    override fun iterator(): Iterator<T> {
        return elements.dropLast(1).iterator()
    }

    fun <U> edges(action: (T, T) -> U): Iterable<U> {
        return elements.zip(elements.drop(1), action)
    }
}

/**
 * Find a sequence through the graph formed by given edges that touch all nodes at least once.
 */
fun <T> getNodeSequenceFromEdges(edges: Map<T, List<T>>): NodeSequence<T> {
    val graph = DirectedGraph(edges.flatMap { entry -> entry.value.flatMap {
        listOf(Edge(entry.key, it), Edge(it, entry.key))
    } })
    val components = detectStronglyConnectedComponents(graph)
    val cycles = components.flatMap { detectSimpleCycles(it) }

    if (cycles.isEmpty()) { return NodeSequence(emptyList()) }
    assert(components.size <= 1) { "Expected edges forming a maximum of 1 strongly connected component" }

    val cyclesWithNodes = cycles.map { cycle -> Pair(cycle, cycle.toSet()) }
    val cyclesOrdered = cyclesWithNodes.sortedByDescending { it.second.count() }
    var (sequence, processedNodes) = cyclesOrdered.first()

    var i = 0
    val cyclesToProcess = cyclesOrdered.drop(1).reversed().toMutableList()
    val originalUpperBound = cyclesToProcess.size
    var upperBound = 0

    while (i < cyclesToProcess.size) {
        if (i > originalUpperBound && upperBound == 0) {
            // Allow exploring a few possible rearrangements, but don't keep going forever.
            // We assume that we should be able to find a viable solution within three times the
            // current overflow.
            upperBound = originalUpperBound * (cyclesToProcess.size - originalUpperBound) * 3
        }
        val (cycle, cycleNodes) = cyclesToProcess[i]
        if (!processedNodes.containsAll(cycleNodes)) {
            val (newSequence, wasProcessed) = mergeCycles(sequence, cycle)
            if (!wasProcessed) {
                // No overlaps were found, so we defer processing this cycle till later
                if (i > upperBound) {
                    throw IllegalArgumentException("Unable to find bridges between cycles")
                }
                cyclesToProcess.add(Pair(cycle, cycleNodes))
            } else {
                processedNodes = processedNodes.union(cycleNodes)
            }
            sequence = newSequence
        }
        i += 1
    }

    return NodeSequence(sequence)
}

/**
 * Find a sequence through the graph formed by given edges that touch all edge groups at least once.
 */
fun <T> getSequenceFromEdges(edges: Map<T, List<T>>, bidirectional: Boolean = false): NodeSequence<T> {
    val graph = if (bidirectional) {
        DirectedGraph(edges.flatMap { entry ->
            entry.value.flatMap { listOf(Edge(entry.key, it), Edge(it, entry.key)) }
        })
    } else {
        DirectedGraph(edges.flatMap { entry -> entry.value.map { Edge(entry.key, it) } })
    }

    val components = detectStronglyConnectedComponents(graph)
    val cycles = components.flatMap { detectSimpleCycles(it) }

    if (cycles.isEmpty()) { return NodeSequence(emptyList()) }
    assert(components.size <= 1) { "Expected edges forming a maximum of 1 strongly connected component" }

    val cyclesWithEdges = cycles.map { cycle -> Pair(cycle, cycle.edges { a, b -> Pair(a, b) }.toSet()) }
    val cyclesOrdered = cyclesWithEdges.sortedByDescending { it.second.count() }
    var (sequence, processedEdges) = cyclesOrdered.first()

    var i = 0
    val cyclesToProcess = cyclesOrdered.drop(1).reversed().toMutableList()
    val originalUpperBound = cyclesToProcess.size
    var upperBound = 0

    while (i < cyclesToProcess.size) {
        if (i >= originalUpperBound && upperBound == 0) {
            // Allow exploring a few possible rearrangements, but don't keep going forever.
            // We assume that we should be able to find a viable solution within three times the
            // current overflow.
            upperBound = originalUpperBound + (cyclesToProcess.size - originalUpperBound) * 3
        }
        val (cycle, cycleEdges) = cyclesToProcess[i]
        if (!processedEdges.containsAll(cycleEdges)) {
            val (newSequence, wasProcessed) = mergeCycles(sequence, cycle)
            if (!wasProcessed) {
                // No overlaps were found, so we defer processing this cycle till later
                if (i > originalUpperBound && i > upperBound) {
                    throw IllegalArgumentException("Unable to find bridges between cycles")
                }
                cyclesToProcess.add(Pair(cycle, cycleEdges))
            } else {
                processedEdges = processedEdges.union(cycleEdges)
            }
            sequence = newSequence
        }
        i += 1
    }

    return NodeSequence(sequence)
}

private fun <T> findCommonNode(cycle1: Cycle<T>, cycle2: Cycle<T>): Pair<Int, Int>? {
    val commonNodes = cycle1.intersect(cycle2)
    val commonNode = if (commonNodes.isNotEmpty()) {
        commonNodes.first()
    } else {
        return null
    }
    return Pair(cycle1.indexOf(commonNode), cycle2.indexOf(commonNode))
}

private fun <T> insertCycleAt(
    cycleToInsert: Cycle<T>, destinationCycle: Cycle<T>, startIndex: Int, length: Int
): Cycle<T> {
    return destinationCycle.subList(0, startIndex) +
        cycleToInsert +
        destinationCycle.subList(startIndex + length, destinationCycle.size)
}

private fun <T> mergeCycles(cycle1: Cycle<T>, cycle2: Cycle<T>): Pair<Cycle<T>, Boolean> {
    val commonNode = findCommonNode(cycle1, cycle2) ?: return Pair(cycle1, false)
    val (indexInCycle1, indexInCycle2) = commonNode
    return Pair(insertCycleAt(cycle2.rotateLeft(indexInCycle2), cycle1, indexInCycle1, 1), true)
}

private fun <T> Cycle<T>.rotateLeft(numberOfPlaces: Int): Cycle<T> {
    val list = this.dropLast(1).let {
        it.subList(numberOfPlaces, it.size) + it.subList(0, numberOfPlaces)
    }
    return list + list.first()
}

private fun <T, U> Cycle<T>.edges(action: (T, T) -> U): Iterable<U> {
    return this.zip(this.drop(1), action)
}
