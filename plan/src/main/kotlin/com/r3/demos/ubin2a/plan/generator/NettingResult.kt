package com.r3.demos.ubin2a.plan.generator

import com.r3.demos.ubin2a.plan.NettingPayment

data class NettingResult(
    val payments: List<NettingPayment>,
    val obligations: List<ObligationEdge>
)
