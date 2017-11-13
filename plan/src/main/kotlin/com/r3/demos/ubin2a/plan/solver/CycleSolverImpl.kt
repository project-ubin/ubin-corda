package com.r3.demos.ubin2a.plan.solver

import com.r3.demos.ubin2a.obligation.Obligation
import com.r3.demos.ubin2a.plan.*
import net.corda.core.contracts.Amount
import net.corda.core.identity.AbstractParty
import java.util.*
import kotlin.collections.HashMap

data class EdgeResult(val modified: Boolean, val failed: Boolean)

fun populateEdge(lastParty: AbstractParty, party: AbstractParty,
                 obligations: ObligationsMap,
                 mutableLimits: MutableMap<AbstractParty, Long>,
                 settledObligations: MutableSet<Obligation.State>): EdgeResult {
    val obs = obligations.obligationsForEdge(lastParty, party)
    if (obs.isEmpty()) {
        return EdgeResult(false, failed = false)
    }

    settledObligations.addAll(obs)

    var obSum = obs.map { it.amount.quantity }.sum()
    mutableLimits[lastParty] = mutableLimits[lastParty]!!.minus(obSum)
    mutableLimits[party] = mutableLimits[party]!!.plus(obSum)
    return EdgeResult(true, false)
}

fun checkEdge(lastParty: AbstractParty, party: AbstractParty,
              obligations: ObligationsMap,
              mutableLimits: MutableMap<AbstractParty, Long>,
              settledObligations: MutableSet<Obligation.State>): EdgeResult {
    var amountP = mutableLimits[lastParty]!!
    var amountN = mutableLimits[party]!!
    if (amountP >= 0) {
        return EdgeResult(false, false)
    }
    val obs = obligations.obligationsForEdge(lastParty, party).filter { it in settledObligations }

    // remove obligations from the back until we are >=0
    var removeUpTo = obs.size
    while (removeUpTo > 0 && amountP < 0) {
        --removeUpTo
        amountP += obs[removeUpTo].amount.quantity
        amountN -= obs[removeUpTo].amount.quantity
    }
    val toRemove = mutableSetOf(obs[removeUpTo])

    // check if we can re-add any later obligations
    for (i in removeUpTo + 1 until obs.size) {
        if (amountP - obs[i].amount.quantity >= 0) {
            amountP -= obs[i].amount.quantity
            amountN += obs[i].amount.quantity
        } else {
            toRemove.add(obs[i])
        }
    }

    if (toRemove.size == obs.size) {
        return EdgeResult(false, true)
    }

    //mutate new limits and settled obligations accordingly
    settledObligations.removeAll(toRemove)
    mutableLimits[lastParty] = amountP
    mutableLimits[party] = amountN
    return EdgeResult(true, false)
}

fun <T> loopCycle(cycle: Collection<T>, function: (currentParty: T, lastParty: T) -> EdgeResult): EdgeResult {
    var lastParty: T? = null
    var modified = false
    for (party in cycle) {
        if (lastParty != null) {
            val res = function(lastParty, party)
            if (res.failed) {
                return EdgeResult(modified, true)
            }
            if (res.modified) {
                modified = true
            }
        }
        lastParty = party
    }
    return EdgeResult(modified, false)
}


class CycleSolverImpl : CycleSolver {
    override fun solveCycle(cycle: Cycle, obligations: Map<Edge, Obligations>, limits: Map<AbstractParty, Amount<Currency>>): CycleResult {
        val token = limits.values.first().token
        val newLimits = HashMap(limits.map {
            if (it.value.token != token) {
                throw IllegalArgumentException("Can't mix currencies within one netting run")
            }
            Pair(it.key, it.value.quantity)
        }.toMap())

        val settledObligations: MutableSet<Obligation.State> = HashSet<Obligation.State>()
        // try to settle all obligations - we'll check for limit breaches in the next step
        if (loopCycle(cycle, { l, c -> populateEdge(l, c, obligations, newLimits, settledObligations) }).failed) {
            return CycleResult(emptyList(), limits)
        }

        for (i in 0..100) {
            val res = loopCycle(cycle, { l, c -> checkEdge(l, c, obligations, newLimits, settledObligations) })
            if (res.failed) {
                return CycleResult(emptyList(), limits)
            }
            if (!res.modified) {
                return CycleResult(settledObligations.toList(),
                    newLimits.map { Pair(it.key, Amount(it.value, token)) }.toMap())
            }
        }

        throw NettingFailure("Exceeded number of iterations to solve cycle")
    }
}
