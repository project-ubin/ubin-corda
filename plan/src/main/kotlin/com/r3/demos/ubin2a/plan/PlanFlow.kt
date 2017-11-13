package com.r3.demos.ubin2a.plan

import co.paralleluniverse.fibers.Suspendable
import com.r3.demos.ubin2a.obligation.Obligation
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AbstractParty
import net.corda.core.utilities.ProgressTracker
import java.util.*

@InitiatingFlow
@StartableByRPC
class PlanFlow(val obligations: Set<Obligation.State>,
               val limits: Map<AbstractParty, Long>,
               val currency: Currency) :
    FlowLogic<Pair<List<Triple<AbstractParty, AbstractParty, Amount<Currency>>>, Set<UniqueIdentifier>>>() {

    override val progressTracker: ProgressTracker = tracker()

    companion object {
        object PREPARATION : ProgressTracker.Step("Building graph and calculating minimum spanning tree")
        object NETTING : ProgressTracker.Step("Calculating optimal netting solution")
        object RUNNING : ProgressTracker.Step("Kicking off collect states flow")

        fun tracker() = ProgressTracker(PREPARATION, NETTING, RUNNING)
    }

    /**
     * This is a bit of ETL for transforming the output of detect so it's compatible with the netting algorithm.
     */
    private fun transformInput(obligations: Set<Obligation.State>, limits: Map<AbstractParty, Long>): HashMap<NodeId<ComparableParty>, Node<ComparableParty>> {
        val nodes = HashMap<NodeId<ComparableParty>, Node<ComparableParty>>()
        val map: HashMap<String, NodeId<ComparableParty>> = HashMap()

        limits.forEach {
            map.put(it.key.owningKey.toString(), NodeId(it.key.toComparable()))
        }

        limits.forEach {
            val nodeId = map[it.key.owningKey.toString()]!!
            nodes.put(nodeId, Node<ComparableParty>(it.value))
        }

        obligations.forEach {
            val obligor = map[it.borrower.owningKey.toString()]!!
            val obligee = map[it.lender.owningKey.toString()]!!
            add_obligation(nodes, obligor, obligee, it.amount.quantity, it.linearId)
        }

        return nodes
    }

    /**
     * Transforms the output of the netting algorithm so it's compatible with the execution stage.
     */
    private fun transformPayments(netting: List<Triple<NodeId<ComparableParty>, NodeId<ComparableParty>, CashValue>>): List<Triple<AbstractParty, AbstractParty, Amount<Currency>>> {
        return netting.map {
            Triple(it.first.id.party, it.second.id.party, Amount(it.third.amount, currency))
        }
    }

    /**
     * Pipeline: Detect flow output -> transform -> run netting -> transform -> return
     */
    @Suspendable
    override fun call(): Pair<List<Triple<AbstractParty, AbstractParty, Amount<Currency>>>, Set<UniqueIdentifier>> {
        val input: HashMap<NodeId<ComparableParty>, Node<ComparableParty>> = transformInput(obligations, limits)
        val (payments, resultantObligations) = find_netting(input)
        return Pair(transformPayments(payments), resultantObligations)
    }
}
