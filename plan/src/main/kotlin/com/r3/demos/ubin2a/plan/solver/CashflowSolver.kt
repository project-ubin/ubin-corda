package com.r3.demos.ubin2a.plan.solver

import com.r3.demos.ubin2a.plan.Edge
import com.r3.demos.ubin2a.plan.Obligations
import com.r3.demos.ubin2a.plan.generator.NettingResult
import net.corda.core.contracts.Amount
import net.corda.core.identity.AbstractParty
import java.util.*

interface CashflowSolver {
    fun solveForNettingCashflows(cycleResult: CycleResult, obligations: Map<Edge, Obligations>, limits: Map<AbstractParty, Amount<Currency>>)
        : NettingResult
}