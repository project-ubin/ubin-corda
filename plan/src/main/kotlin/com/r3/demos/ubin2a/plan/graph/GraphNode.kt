package com.r3.demos.ubin2a.plan.graph

private const val UNDEFINED = -1

class GraphNode<out T>(val id: T) {
    var index = UNDEFINED
    var lowLink = UNDEFINED

    fun hasUndefinedIndex(): Boolean {
        return index == UNDEFINED
    }

    override fun toString(): String {
        return "Node($id)"
    }
}
