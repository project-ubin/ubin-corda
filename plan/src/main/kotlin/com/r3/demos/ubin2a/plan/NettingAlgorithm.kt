package com.r3.demos.ubin2a.plan

import net.corda.core.contracts.UniqueIdentifier
import unsigned.Uint
import unsigned.Ulong
import unsigned.toUint
import java.util.*
import java.util.logging.Logger

// TODO: Convert this into a library called from a simple planning flow.

val log = Logger.getAnonymousLogger()!!

class NettingException(msg: String) : RuntimeException(msg)

/**
 * Pseudo random number generation.
 *
 * What do we want?  Randomness!  How do we want it?  Repeatably, so the tests run
 * the same way every time on all platforms.
 */
class PRNG(var x: Uint = Uint(123456789), var y: Uint = Uint(987654321), var z: Uint = Uint(43219876), var c: Uint = Uint(6543217)) {
    fun prng(): Uint {
        this.x = Uint(314527869) * this.x + 1234567
        this.y = this.y.xor(this.y.shl(5))
        this.y = this.y.xor(this.y.shr(7))
        this.y = this.y.xor(this.y.shl(22))
        val t: Ulong = Ulong("4294584393") * Ulong(this.z + c) // kotlin's long is already 64 bits, no need for longlong
        c = t.shr(32).toUint()
        z = t.toUint()
        return x + y + z
    }
}

// Three functions with different signatures for keeping tests similar to the c++ originals.

fun <T : Comparable<T>> add_obligation(
    nodes: HashMap<NodeId<T>, Node<T>>,
    obligor: NodeId<T>,
    obligee: NodeId<T>,
    amount: Long,
    linearId: UniqueIdentifier = UniqueIdentifier()) {
    add_obligation(nodes, obligor, obligee, CashValue(amount, linearId))
}

fun <T : Comparable<T>> add_obligation(
    nodes: HashMap<NodeId<T>, Node<T>>,
    obligor: NodeId<T>,
    obligee: NodeId<T>,
    amount: CashValue) {
    nodes[obligor]!!.add_obligation_to(obligee, amount)
}

fun add_obligation(
    nodes: HashMap<NodeId<Int>, Node<Int>>,
    obligor: Int,
    obligee: Int,
    amount: Long,
    linearId: UniqueIdentifier = UniqueIdentifier()) {
    add_obligation(nodes, NodeId(obligor), NodeId(obligee), CashValue(amount, linearId))
}

fun <T : Comparable<T>> exists_obligation(nodes: HashMap<NodeId<T>, Node<T>>, obligor: NodeId<T>, obligee: NodeId<T>) =
    nodes[obligor]!!.exists_obligation_to(obligee)


fun delim() = println("---------------------")
fun dump_delim() = delim()

val Int.cash: CashValue get() = CashValue(this.toLong())

fun <T : Comparable<T>> find_netting(nodes: HashMap<NodeId<T>, Node<T>>): Pair<MutableList<Triple<NodeId<T>, NodeId<T>, CashValue>>, Set<UniqueIdentifier>> {
    var result: Pair<MutableList<Triple<NodeId<T>, NodeId<T>, CashValue>>, Set<UniqueIdentifier>>? = null
    val cycles: MutableList<ObligationCycle<T>> = mutableListOf()
    var max_recursion = 3

    /*
     * We don't actually know how this algorithm might run so we have to be careful
     * to avoid it exploding, should we have an insanely connected graph, rather than
     * a sparse one.  To do this we start with a very tight limit on recursions, but
     * then incrementally allow more until we either converge, or until we're clearly
     * on a path to computing things until the sun burns out.  A timely, but imperfect
     * answer is far better than none :-)
     */
    main_recurse_loop@ while (true) {
        var ops = 0
        log.finest("ops is $ops")
        nodes.map { k ->
            log.finest("looking at ${k.key}")
            val scan_stack = LinkedList<NodeId<T>>()
            val blocked_set = HashSet<NodeId<T>>()
            ops += recurse_netting(cycles, scan_stack, blocked_set, nodes, k.key, k.key, CashValue(Long.MAX_VALUE), 0.cash, max_recursion)
            log.finest("Scan contains ${scan_stack.size} blocked contains ${blocked_set.size}")
        }

        /*
         * Did we converge?
         */
        if (max_recursion >= nodes.size) {
            println("recursion finished at: $max_recursion")
            break@main_recurse_loop
        }

        /*
         * Did we exceed our arbitrary computation limit?
         */
        if (ops > 50000) {
            println("recursion iterations clamped at: $max_recursion, next op count would be: $ops")
            break@main_recurse_loop
        }

        /*
         * Still good - try recursing a little more.
         */
        max_recursion++
        cycles.clear()
    }

    val sortedCycles = cycles.sortedWith(compareByDescending({ it.total_val }))

    println("Cycles found: ${sortedCycles.size}")

    sortedCycles.map {
        println("min: ${it.min_val}, total: ${it.total_val}, num nodes: ${it._nodes.size}, nodes: ${it._nodes}")
    }
    var viableHappenedBefore = false
    for (cy in sortedCycles) {
        val try_nodes = cy._nodes.size
        var viable = true

        loop@ for (k in 0..try_nodes - 1) {
            // println("k is $k")
            val prev_k = (k + try_nodes - 1) % try_nodes
            val next_k = (k + 1) % try_nodes
            val this_node = cy._nodes[k]
            val prev_node = cy._nodes[prev_k]
            val next_node = cy._nodes[next_k]

            /*
             * Is the edge we've just looked at one that we've already used?
             */
            if (!nodes[prev_node]!!.exists_obligation_to(this_node) || !nodes[this_node]!!.exists_obligation_to(next_node)) {
                viable = false
                break@loop
            }

            val incoming = nodes[prev_node]!!.obligations_to[this_node] ?: CashValue(0)
            val outgoing = nodes[this_node]!!.obligations_to[next_node] ?: CashValue(0)
            if ((nodes[this_node]!!.balance + incoming) < outgoing) {
                viable = false
                break@loop
            }
        }

        val toRemove = mutableSetOf<UniqueIdentifier>()

        if (viable) {
            val payments = generate_netting(cy, nodes)

            val cy_nodes = cy._nodes.size

            for (k in 0..cy_nodes - 1) {
                val prev_k = (k + cy_nodes - 1) % cy_nodes
                val this_node = cy._nodes[k]
                val prev_node = cy._nodes[prev_k]
                val incoming = nodes[prev_node]!!.obligations_to[this_node]!!


                val obligationToSettle = nodes[prev_node]!!.obligations_to.remove(this_node)
                println("Settling ${obligationToSettle!!.linearId}")
                toRemove.add(obligationToSettle.linearId)
                nodes[prev_node]!!.balance -= incoming
                nodes[this_node]!!.balance += incoming
            }

            // TODO: Remove this hack.
            // Currently the netting algorithm returns zero payments which potentially confuses the execute stage.
            // We only add payments if they are for > 0.
            val filteredPayments = payments.filter { it.third.amount > 0 }.toMutableList()
            if(!viableHappenedBefore) {
                viableHappenedBefore = true
                result = Pair(filteredPayments, toRemove.toSet())
            }
        }
    }

    /**
     * Once we've found all of the netting scenarios we may have freed up some
     * unilateral payments.  Make these now.
     *
     * TODO: Run this after the LSM transaction has been committed.
     */

    val nodeKeys = nodes.keys

    nodeKeys.map { k ->
        val obligationsToRemove: MutableList<Pair<NodeId<T>, NodeId<T>>> = mutableListOf()
        nodes[k]!!.obligations_to.map { j ->
            if (nodes[k]!!.balance > j.value) {
                println("  unilateral pay from: $k, to: ${j.key}, amount: ${j.value}")
                nodes[k]!!.balance -= j.value
                nodes[j.key]!!.balance += j.value
                //   nodes[k]!!.obligations_to.remove(j.key)
                obligationsToRemove.add(Pair(k, j.key))
            }
        }

        obligationsToRemove.map { (first, second) ->
            nodes[first]!!.obligations_to.remove(second)
        }
    }


    return result ?: throw NettingException("Netting not possible")
}


fun <T : Comparable<T>> recurse_netting(cycles: MutableList<ObligationCycle<T>>, scan_stack: LinkedList<NodeId<T>>, blocked_set: MutableSet<NodeId<T>>, nodes: HashMap<NodeId<T>, Node<T>>, i: NodeId<T>, start_i: NodeId<T>, min_val: CashValue, total_val: CashValue, max_depth: Int): Int {
    var ops = 0

    log.finest("In recurse netting with $i and $start_i")

    scan_stack.addLast(i)
    blocked_set.add(i)

    log.finest("i is $i")

    val node = nodes[i]!!

    for ((obligee, value) in node.obligations_to) {
        if (obligee.id < start_i.id) {
            continue
        }
        var new_min_val = min_val
        if (new_min_val >= value) {
            new_min_val = value
        }

        val new_total_val = total_val + value

        log.finest("comparing $obligee with $start_i")

        if (obligee.id == start_i.id) {
            log.finest("Both are $obligee - adding to cycles")
            cycles.add(ObligationCycle(scan_stack, new_min_val, new_total_val))
            ops += 1
            continue
        }

        if (blocked_set.contains(obligee)) {
            ops += 1
            continue
        }

        log.finest("Adding to recurse netting")
        if (scan_stack.size < max_depth) {
            ops += recurse_netting(cycles, scan_stack, blocked_set, nodes, obligee, start_i, new_min_val, new_total_val, max_depth)

        }
    }

    blocked_set.remove(i)
    scan_stack.removeLast()
    return ops
}

// TODO: Move to execute phase.
fun <T : Comparable<T>> generate_netting(cy: ObligationCycle<T>, nodes: HashMap<NodeId<T>, Node<T>>): MutableList<Triple<NodeId<T>, NodeId<T>, CashValue>> {
    val cy_nodes = cy._nodes.size

    println("  solving cycle: total value: ${cy.total_val} (min contribution: ${cy.min_val}), num nodes: $cy_nodes, nodes: ${cy._nodes}")

    val payments: MutableList<Payment<T>> = mutableListOf()
    loop@ for (k in 0..cy_nodes - 1) {
        val prev_k = (k + cy_nodes - 1) % cy_nodes
        val next_k = (k + cy_nodes + 1) % cy_nodes
        val this_node = cy._nodes[k]
        val prev_node = cy._nodes[prev_k]
        val next_node = cy._nodes[next_k]
        val incoming = nodes[prev_node]!!.obligations_to[this_node]!!
        val outgoing = nodes[this_node]!!.obligations_to[next_node]!!
        payments.add(Payment(this_node, incoming - outgoing))
    }

    /*
     * Sort from highest value payment to lowest.
     */
    val sortedPayments = payments.sortedWith(compareBy({ it.balance }))

    /*
     * Start working inwards from both ends.
     */
    var p = 0
    var q = cy_nodes - 1

    val paymentList: MutableList<Triple<NodeId<T>, NodeId<T>, CashValue>> = mutableListOf()

    while (p < q) {
        val pay_from = CashValue(0) - sortedPayments[p].balance
        val pay_to = sortedPayments[q].balance

        if (pay_from < pay_to) {
            println("    pay from: ${sortedPayments[p].id}, to: ${sortedPayments[q].id}, of: $pay_from")
            paymentList.add(Triple(sortedPayments[p].id, sortedPayments[q].id, pay_from))

            sortedPayments[q].balance -= pay_from
            p++
        } else if (pay_from == pay_to) {
            println("    pay from: ${sortedPayments[p].id}, to: ${sortedPayments[q].id}, of: $pay_from")
            paymentList.add(Triple(sortedPayments[p].id, sortedPayments[q].id, pay_from))

            p++
            q--
        } else {
            println("    pay from: ${sortedPayments[p].id}, to: ${sortedPayments[q].id}, of: $pay_to")
            paymentList.add(Triple(sortedPayments[p].id, sortedPayments[q].id, pay_to))

            sortedPayments[p].balance += pay_to
            q--
        }
    }

    return paymentList
}

fun <T : Comparable<T>> dump_balances(nodes: HashMap<NodeId<T>, Node<T>>) {
    var total_obligations = 0.cash
    nodes.map { entry ->
        val obligations = CashValue(entry.value.obligations_to.map { it -> it.value.amount }.sum())
        println("${entry.key} - ${entry.value} - Total obligations: $obligations")
        total_obligations += obligations
    }
    println("Total obligations: $total_obligations")
}
