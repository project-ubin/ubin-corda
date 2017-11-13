package com.r3.demos.ubin2a.plan.graph

// Extend strongly connected components by adding the minimum number of reciprocal links for
// unilateral links so that anything that is connected at all becomes one strongly
// connected component

fun <T> extendComponents(components: List<StronglyConnectedComponent<T>>): List<StronglyConnectedComponent<T>> {
    val unprocessedComponents = HashSet(components)
    val result = mutableSetOf<StronglyConnectedComponent<T>>()
    while (unprocessedComponents.isNotEmpty()) {
        val toProcess = mutableSetOf(unprocessedComponents.first())
        unprocessedComponents.remove(toProcess.single())
        val nodesInComponent = mutableSetOf<T>()
        var targetComponent = toProcess.single()

        while (toProcess.isNotEmpty()) {
            val component = toProcess.first()
            toProcess.remove(component)
            nodesInComponent.addAll(component.nodes)
            for (node in component.nodes) {
                for (otherNode in component.graph.edgesForNode(node)) {
                    if (otherNode.id !in nodesInComponent) {
                        // link outside the current strongly connected component - we need to add a backlink
                        var otherComponent = getComponentForNode(otherNode.id, unprocessedComponents)
                        if (otherComponent != null) {
                            // we're going to link a component we haven't looked at - move it from unprocessed to
                            // the set of things to process for this component
                            toProcess.add(otherComponent)
                            unprocessedComponents.remove(otherComponent)
                        } else {
                            // linking to a component that is already processed
                            otherComponent = getComponentForNode(otherNode.id, result)
                        }
                        if (otherComponent != null) {
                            // found a linked component - merge
                            nodesInComponent.addAll(otherComponent.nodes)
                            targetComponent = targetComponent.merge(otherComponent)
                        } else {
                            // single node without outgoing connections is not a component - special case for adding
                            nodesInComponent.add(otherNode.id)
                            targetComponent = targetComponent.merge(StronglyConnectedComponent(component.graph, listOf(otherNode)))
                        }
                        targetComponent.graph.insertEdge(otherNode.id, node)
                    }
                }
            }
        }
        result.add(targetComponent)
    }
    return result.toList()
}

fun <T> getComponentForNode(node: T, nodesToLookAt: MutableSet<StronglyConnectedComponent<T>>): StronglyConnectedComponent<T>? {
    val component = nodesToLookAt.flatMap { c -> c.nodes.map { n -> Pair(n, c) } }.toMap()[node]
    if (component != null) {
        nodesToLookAt.remove(component)
    }
    return component
}

