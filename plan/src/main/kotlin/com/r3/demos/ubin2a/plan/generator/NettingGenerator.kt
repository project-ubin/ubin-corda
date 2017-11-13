package com.r3.demos.ubin2a.plan.generator

import com.r3.demos.ubin2a.plan.NettingPayment
import com.r3.demos.ubin2a.plan.Obligations
import com.r3.demos.ubin2a.plan.ObligationsMap
import com.r3.demos.ubin2a.plan.allEntries
import net.corda.core.contracts.Amount
import net.corda.core.identity.AbstractParty
import java.util.*

interface NettingGenerator {
    fun generate(obligations: ObligationsMap): NettingResult
    fun generate(obligations: Obligations): NettingResult
}

class NettingGeneratorImpl : NettingGenerator {

    private val bidirectional: Boolean = true

    override fun generate(obligations: ObligationsMap): NettingResult {
        return generate(obligations.allEntries())
    }

    override fun generate(obligations: Obligations): NettingResult {
        // Find a unique sequence formed by provided obligations
        val sequence = nodeSequenceFrom(obligations, bidirectional)
        val nodes = obligations.flatMap { listOf(it.lender, it.borrower) }.toSet()

        // Note: We're assuming that all obligations are of the same currency
        val currency = obligations.first().amount.token

        // Derive balance per node in the cycle and sort in ascending order
        val balances = nodes
            .map { node ->
                val incoming = obligations.filter { it.borrower == node }.map { it.amount.quantity }.sum()
                val outgoing = obligations.filter { it.lender == node }.map { it.amount.quantity }.sum()
                Balance(node, incoming - outgoing)
            }
            .sortedWith(compareBy { it.value() })

        // Generate netting payments to even out the balances
        val payments = mutableListOf<NettingPayment>()
        var (from, to) = Pair(0, balances.size - 1)

        while (from < to) {
            val payFrom = 0 - balances[from].value()
            val payTo = balances[to].value()

            when {
                payFrom < payTo -> {
                    payments.createPayment(balances, from, to, payFrom, currency)
                    balances[to].subtract(payFrom)
                    from++
                }
                payFrom == payTo -> {
                    payments.createPayment(balances, from, to, payFrom, currency)
                    from++; to--
                }
                else -> {
                    payments.createPayment(balances, from, to, payTo, currency)
                    balances[from].add(payTo)
                    to--
                }
            }
        }

        val obligationEdges : List<ObligationEdge> = sequence
            .edges { a, b -> obligationEdgeOf(obligations.filter {
                (it.lender == a && it.borrower == b) ||
                (bidirectional && it.lender == b && it.borrower == a)
            }) }
            .filter { it !is ObligationGroup || !it.linearIds.isEmpty() }
        return NettingResult(payments, obligationEdges)
    }

    private data class Balance(val node: AbstractParty, private var currentValue: Long) {
        fun value(): Long = currentValue
        fun add(amount: Long) {
            this.currentValue += amount
        }

        fun subtract(amount: Long) {
            this.currentValue -= amount
        }
    }

    private fun MutableList<NettingPayment>.createPayment(
        balances: List<Balance>,
        from: Int,
        to: Int,
        amount: Long,
        currency: Currency
    ) {
        val fromNode = balances[from].node
        val toNode = balances[to].node
        if (fromNode != toNode && amount != 0L) {
            this.add(NettingPayment(fromNode, toNode, Amount(amount, currency)))
        }
    }

}