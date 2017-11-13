package com.r3.demos.ubin2a.plan.solver

import com.r3.demos.ubin2a.plan.Edge
import com.r3.demos.ubin2a.plan.Obligations
import com.r3.demos.ubin2a.plan.generator.NettingGenerator
import com.r3.demos.ubin2a.plan.generator.NettingGeneratorImpl
import com.r3.demos.ubin2a.plan.generator.NettingResult
import net.corda.core.contracts.Amount
import net.corda.core.identity.AbstractParty
import java.util.*

class CashflowSolverImpl(private val generator: NettingGenerator = NettingGeneratorImpl()) : CashflowSolver {
    override fun solveForNettingCashflows(
        cycleResult: CycleResult,
        obligations: Map<Edge, Obligations>,
        limits: Map<AbstractParty, Amount<Currency>>
    ): NettingResult {
        val netResult = generator.generate(cycleResult.settledObligations)
        val outCash = netResult.payments.groupBy { it.from }.map { Pair(it.key, Amount(it.value.map { it.amount.quantity }.sum(), it.value.first().amount.token)) }.toMap()
        for (cash in outCash) {
            if (cash.value > limits[cash.key]!!) {
                throw NettingFailure("Trying to settle larger sum than is available in limits - this should never happen!")
            }
        }
        return netResult
    }
}