package com.r3.demos.ubin2a.pledge

import com.r3.demos.ubin2a.base.CENTRAL_PARTY_X500
import net.corda.core.contracts.*
import net.corda.core.crypto.keys
import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey
import java.time.Instant
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Lob
import javax.persistence.Table

class Pledge : Contract {
    companion object {
        @JvmStatic
        val PLEDGE_CONTRACT_ID = "com.r3.demos.ubin2a.pledge.Pledge"
    }

    override fun verify(tx: LedgerTransaction){
        val command = tx.commands.requireSingleCommand<TypeOnlyCommandData>()
        when (command.value) {
            is Pledge.Issue -> requireThat {
                val pledge = tx.outputsOfType<Pledge.State>().first()
                "A newly issued Pledge must have a positive amount." using (pledge.amount > Amount(0, pledge.amount.token))
                "Cash inputs should be consumed when issuing a pledge request." using (tx.inputs.isNotEmpty())
                "The signers should only be the participants for the pledge" using
                        (command.signers.toSet() == pledge.participants.map { it.owningKey }.toSet())
            }
            is Pledge.Settle -> requireThat {
            }
        }
    }

    // Commands.
    interface Commands : CommandData

    class Issue : TypeOnlyCommandData()
    class Settle : TypeOnlyCommandData()
    class Exit : TypeOnlyCommandData()

    data class State(val amount: Amount<Currency>,
                     val requester: AbstractParty,
                     val approver: AbstractParty,
                     val paid: Amount<Currency> = Amount(0, amount.token),
                     val issueDate: Instant = Instant.now(),
                     override val linearId: UniqueIdentifier = UniqueIdentifier()) : LinearState, QueryableState {
        override val participants: List<AbstractParty> get() = listOf(requester, approver)

        // Object relational mapper.
        override fun supportedSchemas() = listOf(PledgeSchemaV1)

        override fun generateMappedObject(schema: MappedSchema) = PledgeSchemaV1.PledgeEntity(this)

        object PledgeSchemaV1 : MappedSchema(State::class.java, 1, listOf(PledgeEntity::class.java)) {
            @Entity
            @Table(name = "pledges")
            class PledgeEntity(pledge: State) : PersistentState() {
                @Column var currency: String = pledge.amount.token.toString()
                @Column var amount: String = pledge.amount.quantity.toString()
                @Column @Lob var requester: ByteArray = pledge.requester.owningKey.encoded
                @Column @Lob var approver: ByteArray = pledge.approver.owningKey.encoded
                @Column var issueDate: String = pledge.issueDate.toString()
                @Column var linearId: String = pledge.linearId.toString()
            }
        }
    }
}