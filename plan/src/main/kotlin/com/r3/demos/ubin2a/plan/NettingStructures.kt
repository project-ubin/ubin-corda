package com.r3.demos.ubin2a.plan

import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import java.util.*

class ComparableParty(val party: AbstractParty) : Comparable<ComparableParty> {
    override fun toString() = party.toString()
    override fun compareTo(other: ComparableParty): Int {
        val thisString = this.party.owningKey.toString()
        val otherString = other.party.owningKey.toString()
        return thisString.compareTo(otherString)
    }
}

fun AbstractParty.toComparable() = ComparableParty(this)

/**
 * Representation of cash values.
 */
// TODO: the linearId in here is a hack. All these structures need refactoring.
data class CashValue(val amount: Long, val linearId: UniqueIdentifier = UniqueIdentifier()) : Comparable<CashValue> {
    operator fun plus(other: CashValue) = CashValue(this.amount + other.amount, UniqueIdentifier())
    operator fun minus(other: CashValue) = CashValue(this.amount - other.amount, UniqueIdentifier())
    override fun toString() = "$$amount"
    override fun compareTo(other: CashValue) = this.amount.compareTo(other.amount)
}

/**
 * Representation of a node identity.
 */
data class NodeId<T : Comparable<T>>(val id: T) {
    override fun toString() = "N:$id"
    operator fun compareTo(other: NodeId<T>) = id.compareTo(other.id)
}

/**
 * Representation of each node in the payment graph.
 */
data class Node<T : Comparable<T>>(var balance: CashValue,
                                   var obligations_to: HashMap<NodeId<T>, CashValue> = hashMapOf()) {
    constructor(balance_l: Long) : this(CashValue(balance_l))

    fun add_obligation_to(obligee: NodeId<T>, amount: CashValue) {
        this.obligations_to[obligee] = amount
    }

    fun exists_obligation_to(obligee: NodeId<T>) = obligations_to.containsKey(obligee)
}

/**
 * Payment planning class
 */
data class Payment<T : Comparable<T>>(var id: NodeId<T>, var balance: CashValue) {
    operator fun compareTo(other: Payment<T>) = balance.compareTo(other.balance)
}

/**
 * Representation of obligation cycles.
 */
data class ObligationCycle<T : Comparable<T>>(var min_val: CashValue, var total_val: CashValue, var _nodes: MutableList<NodeId<T>> = mutableListOf<NodeId<T>>()) {
    constructor(nodes: LinkedList<NodeId<T>>, min_val: CashValue, total_val: CashValue) : this(min_val, total_val) {
        nodes.map { k: NodeId<T> ->
            _nodes.add(k)
        }
    }
}