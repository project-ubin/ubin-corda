package com.r3.demos.ubin2a

import com.r3.demos.ubin2a.obligation.IssueObligation
import com.r3.demos.ubin2a.obligation.Obligation
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.crypto.SecureHash
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.finance.contracts.asset.Cash
import net.corda.node.internal.StartedNode
import net.corda.testing.chooseIdentity
import net.corda.testing.node.MockNetwork
import java.util.*

object ubin2aTestHelpers {
    fun printObligations(node: StartedNode<MockNetwork.MockNode>) {
        val me = node.services.myInfo.chooseIdentity()
        node.database.transaction {
            val queryCriteria = net.corda.core.node.services.vault.QueryCriteria.LinearStateQueryCriteria()
            val obligations = node.services.vaultService.queryBy<Obligation.State>(queryCriteria).states.map {
                it.state.data
            }
            obligations.forEach { println("$me(${it.linearId}): ${it.borrower} owes ${it.lender} ${it.amount}") }
        }
    }

    fun sumObligations(obligations: List<Obligation.State>): Long {
        var sum = 0L
        obligations.forEach { sum += it.amount.quantity }
        return sum
    }

    fun allObligations(node: StartedNode<MockNetwork.MockNode>): List<Obligation.State> =
            node.database.transaction {
                val queryCriteria = net.corda.core.node.services.vault.QueryCriteria.LinearStateQueryCriteria()
                val obligations = node.services.vaultService.queryBy<Obligation.State>(queryCriteria).states.map {
                    it.state.data
                }
                obligations
            }

    fun printSortedObligations(node: StartedNode<MockNetwork.MockNode>) {
        node.database.transaction {
            val queryCriteria = net.corda.core.node.services.vault.QueryCriteria.LinearStateQueryCriteria()
            val obligations = node.services.vaultService.queryBy<Obligation.State>(queryCriteria).states.map {
                it.state.data
            }
            obligations.sortedBy { it.issueDate }.forEach {
                val borrower = node.services.identityService.wellKnownPartyFromAnonymous(it.borrower)
                val lender = node.services.identityService.wellKnownPartyFromAnonymous(it.lender)
                println("$borrower owes $lender ${it.amount}")
            }
        }
    }

    fun getVerifiedTransaction(node: StartedNode<MockNetwork.MockNode>, tx: SecureHash): SignedTransaction {
        val maybeTx = node.services.validatedTransactions.getTransaction(tx)!!
        return maybeTx
    }

    fun getCashStates(node: StartedNode<MockNetwork.MockNode>, currency: Currency): List<StateAndRef<Cash.State>> =
        node.database.transaction {
            val cashStates = node.services.vaultService.queryBy<Cash.State>().states.filter { it.state.data.amount.token.product == currency }
            cashStates
        }

    fun createObligation(lender: StartedNode<MockNetwork.MockNode>,
                         borrower: StartedNode<MockNetwork.MockNode>,
                         amount: Amount<Currency>,
                         priority: Int,
                         anonymous: Boolean = true): CordaFuture<SignedTransaction> {
        val flow = IssueObligation.Initiator(amount, lender.info.chooseIdentity(), priority, anonymous)
        return borrower.services.startFlow(flow).resultFuture
    }




}