package com.r3.demos.ubin2a.plan.generator

import com.r3.demos.ubin2a.plan.Obligations
import com.r3.demos.ubin2a.plan.ObligationsMap
import com.r3.demos.ubin2a.plan.allEntries
import com.r3.demos.ubin2a.plan.graph.NodeSequence
import com.r3.demos.ubin2a.plan.graph.getSequenceFromEdges
import net.corda.core.identity.AbstractParty

fun nodeSequenceFrom(obligations: Obligations, bidirectional: Boolean = false): NodeSequence<AbstractParty> =
    getSequenceFromObligations(obligations, bidirectional)

fun nodeSequenceFrom(obligations: ObligationsMap, bidirectional: Boolean = false): NodeSequence<AbstractParty> =
    getSequenceFromObligations(obligations.allEntries(), bidirectional)

private fun getSequenceFromObligations(obligations: Obligations, bidirectional: Boolean): NodeSequence<AbstractParty> {
    val edges = obligations
        .groupBy { it.lender }
        .mapValues { it.value.map { it.borrower } }
    return getSequenceFromEdges(edges, bidirectional)
}
