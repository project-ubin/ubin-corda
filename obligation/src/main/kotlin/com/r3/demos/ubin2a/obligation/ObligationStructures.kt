package com.r3.demos.ubin2a.obligation

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class InternalObligation(val state: Obligation.State, val priority: Int = 0) {
    val linearId get() = state.linearId
    val lender get() = state.lender
    val borrower get() = state.borrower
    val amount get() = state.amount
    val issueDate get() = state.issueDate
    fun toExternal() = state
}
