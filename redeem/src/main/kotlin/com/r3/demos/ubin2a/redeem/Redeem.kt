package com.r3.demos.ubin2a.redeem

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.transactions.LedgerTransaction
import java.time.Instant
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Lob
import javax.persistence.Table

class Redeem : Contract {
    companion object {
        @JvmStatic
        val REDEEM_CONTRACT_ID = "com.r3.demos.ubin2a.redeem.Redeem"
    }

    override fun verify(tx: LedgerTransaction){
        val command = tx.commands.requireSingleCommand<TypeOnlyCommandData>()
        when (command.value) {
            is Redeem.Issue -> requireThat {
                val redeem = tx.outputsOfType<Redeem.State>().first()
                "A newly issued Redeem must have a positive amount." using (redeem.amount > Amount(0, redeem.amount.token))
                "Cash inputs should be consumed when issuing a redeem request." using (tx.inputs.isNotEmpty())
                "The signers should only be the participants for the redeem" using
                        (command.signers.toSet() == redeem.participants.map { it.owningKey }.toSet())
            }
            is Redeem.Settle -> requireThat {
            }
        }
    }

    // Commands.
    interface Commands : CommandData

    class Issue : TypeOnlyCommandData()
    class Settle : TypeOnlyCommandData()

    data class State(val amount: Amount<Currency>,
                     val requester: AbstractParty,
                     val approver: AbstractParty,
                     val paid: Amount<Currency> = Amount(0, amount.token),
                     val issueDate: Instant = Instant.now(),
                     override val linearId: UniqueIdentifier = UniqueIdentifier()) : LinearState, QueryableState {
        override val participants: List<AbstractParty> get() = listOf(requester, approver)

        // Object relational mapper.
        override fun supportedSchemas() = listOf(RedeemSchemaV1)

        override fun generateMappedObject(schema: MappedSchema) = RedeemSchemaV1.RedeemEntity(this)

        object RedeemSchemaV1 : MappedSchema(State::class.java, 1, listOf(RedeemEntity::class.java)) {
            @Entity
            @Table(name = "redeems")
            class RedeemEntity(redeem: State) : PersistentState() {
                @Column var currency: String = redeem.amount.token.toString()
                @Column var amount: String = redeem.amount.quantity.toString()
                @Column @Lob var requester: ByteArray = redeem.requester.owningKey.encoded
                @Column @Lob var approver: ByteArray = redeem.approver.owningKey.encoded
                @Column var issueDate: String = redeem.issueDate.toString()
                @Column var linearId: String = redeem.linearId.toString()
            }
        }
    }
}