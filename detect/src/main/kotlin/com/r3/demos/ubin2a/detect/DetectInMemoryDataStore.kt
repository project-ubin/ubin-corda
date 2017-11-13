package com.r3.demos.ubin2a.detect

import com.r3.demos.ubin2a.obligation.Obligation
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import java.util.concurrent.ConcurrentHashMap

/**
 * This is some mutable state for each node to keep track of the current search state.
 * When unit testing this set of flows using the MockNetwork infrastructure, each node will share the same static class
 * this object definition defines. Therefore, we must delineate between each node's use of the map by keying it with
 * [Party] objects representing the parties involved.
 *
 * When testing on actual nodes, each node runs in a separate JVM process therefore the [Party] delineation becomes
 * superfluous.
 *
 * Interestingly, these data structure will be persisted to disk when flows are suspended and serialised to disk as they
 * are all accessed via @Suspendable methods. So, in theory, this state SHOULD survive node restarts. In practise, who
 * knows, as I haven't tested it.
 *
 * TODO: Replace this with an actual database.
 */
object DataStore {
    /**
     * Each node only stores one [ScanRequest], the one with the lowest hash out of all they have seen so far.
     */
    val scanRequest: MutableMap<Party, ScanRequest> = ConcurrentHashMap(mutableMapOf())

    /**
     * This tracks the state of all open ScanRequests from sending to receipt of a response.
     */
    val neighbours: MutableMap<Party, MutableMap<Party, Status>> = ConcurrentHashMap(mutableMapOf())

    /**
     * Holds the ScanResponse object, it gathers additional edges at it moves from the leaves of the search tree to the
     * source of the winning [ScanRequest].
     */
    val obligations: MutableMap<Party, MutableSet<Obligation.State>> = ConcurrentHashMap(mutableMapOf())

    /**
     * How much cash each party is willing to commit for the netting arrangement.
     */
    val limits: MutableMap<Party, MutableMap<AbstractParty, Long>> = ConcurrentHashMap(mutableMapOf())

    /**
     * Sets up the state for a new [ScanRequest].
     */
    fun initState(from: Party, to: Party) {
        val ourRequests = neighbours[from]
        if (ourRequests == null) neighbours.put(from, mutableMapOf(to to Status.SENT))
        else ourRequests.put(to, Status.SENT)
    }

    /**
     * Method used to update the state for a specific [ScanRequest] for a specified [Party].
     * This update method forces some invariants which SHOULD never be broken.
     */
    fun updateState(from: Party, to: Party, status: Status) {
        val request = neighbours[from]?.get(to)
        when (status) {
            Status.SENT -> throw IllegalArgumentException("Can't update to SENT as it's the default status.")
            Status.ACKNOWLEDGED -> if (request != Status.SENT)
                throw IllegalArgumentException("Can only update SENT neighbours to ACKNOWLEDGED.")
            Status.RECEIVED -> if (request != Status.ACKNOWLEDGED)
                throw IllegalArgumentException("WARNING: Skipped from ACKNOWLEDGED neighbours to RECEIVED.")
        }
        neighbours[from]?.put(to, status)
    }

    /**
     * Helpers for clearing out the current [ScanRequest] state and getting the current state.
     */
    fun purgeState(party: Party) = DataStore.neighbours.put(party, mutableMapOf())
    fun getState(party: Party) = DataStore.neighbours[party]

    /**
     * Helpers for updating and getting the current [ScanRequest].
     */
    fun updateScanRequest(party: Party, req: ScanRequest) {
        DataStore.scanRequest.put(party, req)
    }
    fun getScanRequest(party: Party) = DataStore.scanRequest[party]

    /**
     * Helper for updating the list of collected obligations.
     */
    fun updateObligations(party: Party, newResponses: Set<Obligation.State>) {
        val obligations = DataStore.obligations[party]
        if (obligations == null) DataStore.obligations.put(party, newResponses.toMutableSet())
        else DataStore.obligations[party]?.addAll(newResponses)
    }

    /**
     * Helper for getting the current list of collected obligations for a particular node.
     */
    fun getObligations(party: Party) = DataStore.obligations[party]

    /**
     * Helpers for getting and updating cash limits.
     */
    fun updateLimits(party: Party, newLimits: LinkedHashMap<AbstractParty, Long>) {
        val limits = DataStore.limits[party]
        if (limits == null) DataStore.limits.put(party, newLimits)
        else DataStore.limits[party]?.putAll(newLimits)
    }

    fun getLimits(party: Party) = DataStore.limits[party]

    fun purge(party: Party) {
        scanRequest.remove(party)
        neighbours.remove(party)
        obligations.remove(party)
        limits.remove(party)
    }
}