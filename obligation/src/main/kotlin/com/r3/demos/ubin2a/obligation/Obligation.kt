package com.r3.demos.ubin2a.obligation

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

class Obligation : Contract {
    companion object {
        @JvmStatic
        val OBLIGATION_CONTRACT_ID = "com.r3.demos.ubin2a.obligation.Obligation"
    }

    override fun verify(tx: LedgerTransaction){
        val command = tx.commands.requireSingleCommand<Commands>()
        when (command.value) {
            is Obligation.Issue -> requireThat {
                val obligation = tx.outputs.single().data as Obligation.State
                "A newly issued Obligation must have a positive amount." using (obligation.amount > Amount(0, obligation.amount.token))
                "No inputs should be consumed when issuing an Obligation." using (tx.inputs.isEmpty())
                "Only one output state should be created when issuing an Obligation." using (tx.outputs.size == 1)
                "The beneficiary and obligor cannot be the same identity." using (obligation.lender != obligation.borrower)
                "The signers should only be the participants for the obligation" using
                        (command.signers.toSet() == obligation.participants.map { it.owningKey }.toSet())
            }
            is Obligation.Settle -> requireThat {
                // TODO: Add some contract code here.
                // This contract code has to be slightly more complicated than just being able to settle one obligation
                // at a time as when using the LSM we'll be settling multiple obligations.
            }
        }
    }

    // Commands.
    interface Commands : CommandData

    class Issue : Commands, TypeOnlyCommandData()
    class Settle : Commands, TypeOnlyCommandData()
    class Exit : Commands, TypeOnlyCommandData()

    data class State(val amount: Amount<Currency>,
                     val lender: AbstractParty,
                     val borrower: AbstractParty,
                     val issueDate: Instant = Instant.now(),
                     override val linearId: UniqueIdentifier = UniqueIdentifier()) : LinearState, QueryableState {
        override val participants: List<AbstractParty> get() = listOf(lender, borrower)

        // Object relational mapper.
        override fun supportedSchemas() = listOf(ObligationSchemaV1)
        override fun generateMappedObject(schema: MappedSchema) = ObligationSchemaV1.ObligationEntity(this)

        object ObligationSchemaV1 : MappedSchema(State::class.java, 1, listOf(ObligationEntity::class.java)) {
            @Entity @Table(name = "obligations")
            class ObligationEntity(obligation: State) : PersistentState() {
                @Column var currency: String = obligation.amount.token.toString()
                @Column var amount: Long = obligation.amount.quantity
                @Column @Lob var lender: ByteArray = obligation.lender.owningKey.encoded
                @Column @Lob var borrower: ByteArray = obligation.borrower.owningKey.encoded
                @Column var issueDate: Instant = obligation.issueDate               // TODO: Check whether we need this.
                @Column var linearId: String = obligation.linearId.id.toString()
            }
        }
    }
}
