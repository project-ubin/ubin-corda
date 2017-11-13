package com.r3.demos.ubin2a.execute

import co.paralleluniverse.fibers.Suspendable
import com.r3.demos.ubin2a.base.OBLIGATION_STATUS
import com.r3.demos.ubin2a.base.TemporaryKeyManager
import com.r3.demos.ubin2a.obligation.Obligation
import com.r3.demos.ubin2a.obligation.PersistentObligationQueue
import net.corda.core.contracts.*
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.seconds
import net.corda.core.utilities.toBase58String
import net.corda.core.utilities.unwrap
import net.corda.finance.contracts.asset.CASH_PROGRAM_ID
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.contracts.asset.PartyAndAmount
import java.util.*

// TODO: The collect states flows erroneously uses the obligation graph and not the toSettle graph, to navigate the network. Change this.

// TODO: We need some kind of local in memory storage for the states storage.
object ExecuteDataStore {
    val obligations: MutableSet<StateAndRef<Obligation.State>> = mutableSetOf()
    val payments: MutableSet<Payment> = mutableSetOf()
    val toSettle: MutableSet<UniqueIdentifier> = mutableSetOf()

    fun purge() {
        obligations.clear()
        payments.clear()
        toSettle.clear()
    }
}

@CordaSerializable
data class Payment(val inputs: List<StateAndRef<Cash.State>> = listOf(),
                   val outputs: List<Cash.State> = listOf(),
                   val commands: List<Command<Cash.Commands.Move>> = listOf())

@CordaSerializable
data class SigningStructure(val ptx: SignedTransaction,
                            val source: AbstractParty,
                            val nettingCycle: Set<UniqueIdentifier>,
                            val stateAndRefs: List<StateAndRef<*>>)

/**
 * Data structures.
 */
// TODO: Simplify this by calculating the path to cycle in the detect phase.
@CordaSerializable
data class NettingData(val queue: List<Party>,
                       val visited: Set<Party>,
                       val graph: Set<UniqueIdentifier>,
                       val payments: List<Triple<AbstractParty, AbstractParty, Amount<Currency>>>,
                       val toSettle: Set<UniqueIdentifier>,
                       val source: Party) {
    fun propagate(toVisit: List<Party>, updatedVisited: Set<Party>) = copy(queue = toVisit, visited = updatedVisited)
}

@CordaSerializable
data class CollectStatesRequest(val graph: Set<UniqueIdentifier>,
                                val toSettle: MutableSet<UniqueIdentifier>,
                                val numPayments: Map<AbstractParty, Int>,
                                val toPay: MutableSet<Triple<AbstractParty, AbstractParty, Amount<Currency>>>,
                                val source: AbstractParty,
                                val gatheredObligations: MutableSet<StateAndRef<Obligation.State>> = mutableSetOf(),
                                val recipientPartyAndAmounts: MutableSet<Triple<AbstractParty, AbstractParty, PartyAndAmount<Currency>>> = mutableSetOf(),
                                val payments: MutableSet<Payment> = mutableSetOf())

class ExecuteFlow(val graph: Set<Obligation.State>,
                  val toSettle: Set<UniqueIdentifier>, // Could just use linearIDs.
                  val toPay: List<Triple<AbstractParty, AbstractParty, Amount<Currency>>>) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        logger.info("${ourIdentity.name.organisation}: Starting ExecuteFlow.")
        val obligationLinearIDs = graph.map { it.linearId }.toSet()
        val nettingData = NettingData(listOf(ourIdentity), emptySet(), obligationLinearIDs, toPay, toSettle, ourIdentity)
        subFlow(FindNettingCycleFlow(nettingData))
    }
}

/**
 * Flows for finding the netting cycle.
 */
class FindNettingCycleFlow(val nettingData: NettingData) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        logger.info("${ourIdentity.name.organisation}: Finding netting cycle...")
        val query = QueryCriteria.LinearStateQueryCriteria(uuid = nettingData.toSettle.map { it.id })
        val myObligations = serviceHub.vaultService.queryBy<Obligation.State>(query).states
        if (myObligations.isNotEmpty()) {
            logger.info("${ourIdentity.name.organisation}: I'm part of the netting cycle.")
            val secretKey = serviceHub.cordaService(TemporaryKeyManager::class.java).key()
            val numPayments = nettingData.payments.groupBy { it.first }.map { Pair(it.key, it.value.size) }.toMap()
            println(numPayments)
            val obligationRequest = CollectStatesRequest(
                    graph = nettingData.toSettle,
                    toSettle = nettingData.toSettle.toMutableSet(),
                    toPay = nettingData.payments.toMutableSet(),
                    numPayments = numPayments,
                    source = secretKey
            )
            // TODO: Remove this hack.
            ExecuteDataStore.toSettle.addAll(nettingData.toSettle)

            subFlow(GatherStatesFlow(ourIdentity, obligationRequest))
            return
        } else {
            logger.info("${ourIdentity.name.organisation}: I'm not part of the netting cycle.")
            val neighbours = subFlow(DetermineNeighboursFlow(nettingData.graph)) - nettingData.visited
            val newNettingData = nettingData.propagate(
                    toVisit = nettingData.queue + neighbours - ourIdentity,
                    updatedVisited = nettingData.visited + ourIdentity
            )
            logger.info("${ourIdentity.name.organisation}: New queue ${newNettingData.queue.map { it.name.organisation }}")
            logger.info("${ourIdentity.name.organisation}: New visited ${newNettingData.visited.map { it.name.organisation }}")
            subFlow(SendNettingData(newNettingData.queue.first(), newNettingData))
        }
    }
}

@InitiatingFlow
class SendNettingData(val target: Party, val nettingData: NettingData) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        logger.info("${ourIdentity.name.organisation}: Sending netting data to ${target.name.organisation}")
        val session = initiateFlow(target)
        session.send(nettingData)
    }
}

@InitiatedBy(SendNettingData::class)
class ReceiveNettingData(val otherFlow: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val nettingData = otherFlow.receive<NettingData>().unwrap { it }
        val otherParty = otherFlow.counterparty
        logger.info("${ourIdentity.name.organisation}: Receiving netting data from ${otherParty.name.organisation}")
        subFlow(FindNettingCycleFlow(nettingData))
    }
}

/**
 * Flows for gathering states.
 */
class GatherStatesFlow(val from: Party, val request: CollectStatesRequest) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        // 1. Setup.
        logger.info("${ourIdentity.name.organisation}: Gathering obligations...")
        // 2. Pick out my obligations.
        val query = QueryCriteria.LinearStateQueryCriteria(uuid = request.toSettle.map { it.id })
        val myObligations = serviceHub.vaultService.queryBy<Obligation.State>(query).states
        logger.info("${ourIdentity.name.organisation}: Adding: ${myObligations.map { it.state.data.linearId }}")
        val remainingObligationsToSettle = request.toSettle - myObligations.map { it.state.data.linearId }
        logger.info("${ourIdentity.name.organisation}: Still to settle: $remainingObligationsToSettle")
        require(myObligations.size == (request.toSettle.size - remainingObligationsToSettle.size))
        val neighbours = subFlow(DetermineNeighboursFlow(request.graph))
        // 3. Determine who we send the request to next.
        // There is a special case for the node that first starts collecting obligations. It will have two
        // counter-parties as 'from' will just be it's own identity. So to fix this we just take the first element
        // in the neighbours list to always ensure we don't start two collect obligation rounds in parallel.
        val nextNode = if (neighbours.size == 1) neighbours.single() else (neighbours - from).first()
        logger.info("${ourIdentity.name.organisation}: Next node is $nextNode")
        // 3. Add obligations to settle and update the 'toSettle' list.
        request.gatheredObligations += myObligations.toSet()
        request.toSettle -= myObligations.map { it.state.data.linearId }
        // 4. determine if we are a payment recipient.
        val secretKey = serviceHub.cordaService(TemporaryKeyManager::class.java).key()
        val paymentsWhereImPayee = request.toPay.filter { it.second == secretKey }.toSet()
        //val addedPayments = request.recipientPartyAndAmounts.map { Triple(it.first, it.second, it.third.amount) }.toSet()
        //val paymentsToAdd = paymentsWhereImPayee - addedPayments
        println("payments where im payee: " + paymentsWhereImPayee)
        //println("Added payments: " + addedPayments)
        //println("Payments to add: " + paymentsToAdd)
        // 5. Collect certs if we are a payee and we haven't already added our certs.

        if (paymentsWhereImPayee.isNotEmpty()) {
            logger.info("${ourIdentity.name.organisation}: I'm a payee with certs still to add!")
            paymentsWhereImPayee.forEach { (from, to, amount) ->
                val legalIdentityAnonymous = serviceHub.keyManagementService.freshKeyAndCert(
                        serviceHub.myInfo.legalIdentitiesAndCerts.first(),
                        false
                )
                logger.info("${ourIdentity.name.organisation}: Adding a cert!")
                val partyAndAmount = PartyAndAmount(legalIdentityAnonymous.party.anonymise(), amount)
                request.recipientPartyAndAmounts.add(Triple(from, to, partyAndAmount))
            }
            request.toPay -= paymentsWhereImPayee
        }
        // 6. Generate payments if we are a payer.
        val addedCerts = request.recipientPartyAndAmounts.filter { it.first == secretKey }.toSet()
        if (addedCerts.isNotEmpty()) {
            logger.info("${ourIdentity.name.organisation}: I'm a payer!")
            println("payments where im payer: " + addedCerts)
            // Check to see if we have collected all of the certs.
            val numPaymentsExpected = request.numPayments[secretKey]!!
            logger.info("${ourIdentity.name.organisation}: Number of certs collected: " + addedCerts.size)
            logger.info("${ourIdentity.name.organisation}: Number of total payees: " + numPaymentsExpected)
            if (addedCerts.size == numPaymentsExpected) {
                logger.info("${ourIdentity.name.organisation}: Generate spend!!")
                val notary = serviceHub.networkMapCache.notaryIdentities.firstOrNull()
                val builder = TransactionBuilder(notary = notary)
                val payments = addedCerts.map { it.third }
                Cash.generateSpend(serviceHub, builder, payments)
                val payment = Payment(
                        builder.inputStates().map { serviceHub.toStateAndRef<Cash.State>(it) },
                        builder.outputStates().map { it.data as Cash.State },
                        builder.commands().map { Command(it.value as Cash.Commands.Move, it.signers) }
                )
                logger.info("${ourIdentity.name.organisation}: **************PAYMENT MADE*************" + payment)
                request.payments.add(payment)
                request.recipientPartyAndAmounts -= addedCerts
            }
        }
        subFlow(SendGatherStatesRequest(nextNode, request))
    }
}

@InitiatingFlow
class SendGatherStatesRequest(val target: Party, val request: CollectStatesRequest) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        logger.info("${ourIdentity.name.organisation}: Sending gather obligations request to ${target.name.organisation}")
        val stateAndRefs = request.gatheredObligations + request.payments.flatMap { it.inputs }
        val session = initiateFlow(target)
        subFlow(SendStateAndRefFlow(session, stateAndRefs.toList()))
        session.send(request)
    }
}

@InitiatedBy(SendGatherStatesRequest::class)
class ReceiveGatherStatesRequest(val otherFlow: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        subFlow(ReceiveStateAndRefFlow<ContractState>(otherFlow))
        val request = otherFlow.receive<CollectStatesRequest>().unwrap { it }
        logger.info("${ourIdentity.name.organisation}: Receiving gather obligations request from ${otherFlow.counterparty.name.organisation}")
        val secretKey = serviceHub.cordaService(TemporaryKeyManager::class.java).key()
        if (request.source == secretKey) {
            logger.info("${ourIdentity.name.organisation}: We've done a full circle so collected all the obligations.")
            println(request.payments)
            println(request.payments.size)
            println(request.numPayments.values.sum())
            if (request.payments.size == request.numPayments.keys.size) {
                logger.info("${ourIdentity.name.organisation}: We've collected all the payments.")
                ExecuteDataStore.payments.addAll(request.payments)
                ExecuteDataStore.obligations.addAll(request.gatheredObligations)
                subFlow(BuildTransactionFlow(request))
            } else {
                logger.info("${ourIdentity.name.organisation}: number of payments collected " + request.payments.size)
                logger.info("${ourIdentity.name.organisation}: number of payments to make " + request.toPay.map { it.first }.toSet().size)
                logger.info("${ourIdentity.name.organisation}: We've still got payments to collect.")
                subFlow(GatherStatesFlow(otherFlow.counterparty, request))
            }
        } else {
            logger.info("${ourIdentity.name.organisation}: We still have some nodes to visit.")
            subFlow(GatherStatesFlow(otherFlow.counterparty, request))
        }
    }
}

/**
 * Build the transaction.
 */
class BuildTransactionFlow(val request: CollectStatesRequest) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        // 1. Setup.
        logger.info("${ourIdentity.name.organisation}: Building and signing transaction.")
        val notary = serviceHub.networkMapCache.notaryIdentities.firstOrNull()
        val builder = TransactionBuilder(notary = notary)
        // 2. Assemble transaction components.
        val settlePubKeys = ExecuteDataStore.obligations.flatMap { it.state.data.participants }.map { it.owningKey }
        val settleCommand = Command(Obligation.Settle(), settlePubKeys)
        builder.addCommand(settleCommand)
        val currentTime = serviceHub.clock.instant()
        builder.setTimeWindow(currentTime, 60.seconds)
        val cashInputs = ExecuteDataStore.payments.flatMap { it.inputs }
        cashInputs.forEach { input -> builder.addInputState(input) }
        ExecuteDataStore.obligations.forEach { input -> builder.addInputState(input) }
        val cashCommandPublicKeys = ExecuteDataStore.payments.flatMap { it.commands }.flatMap { it.signers }
        if (cashCommandPublicKeys.isNotEmpty()) builder.addCommand(Cash.Commands.Move(), cashCommandPublicKeys)
        val cashOutputs = ExecuteDataStore.payments.flatMap { it.outputs }
        cashOutputs.forEach { output -> builder.addOutputState(output, CASH_PROGRAM_ID) }
        // 3. Print the builder.
        println(builder.toWireTransaction(serviceHub))
        // 4. Determine which keys we need to sign and then sign.
        val myKeys = serviceHub.keyManagementService.filterMyKeys(builder.toWireTransaction(serviceHub).requiredSigningKeys)
        println(myKeys.map { it.toBase58String() })
        val ptx = serviceHub.signInitialTransaction(builder, myKeys)
        println(ptx.sigs.map { it.by.toBase58String() })
        // 5. Send to the next party for signing.
        val neighbours = subFlow(DetermineNeighboursFlow(ExecuteDataStore.toSettle))
        val nextNode = neighbours.first()
        // 6. Get dependency state refs.
        val stateAndRefs = ExecuteDataStore.obligations + ExecuteDataStore.payments.flatMap { it.inputs }
        val signingStructure = SigningStructure(ptx, request.source, ExecuteDataStore.toSettle.toSet(), stateAndRefs.toList())
        // Purge the datastore as we are done with it.
        ExecuteDataStore.purge()
        // 7. Send to other party.
        subFlow(SendSignedTransactionFlow(nextNode, signingStructure))
    }
}

class CheckAndSignTransactionFlow(val from: Party,
                                  val signingStructure: SigningStructure) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        // 1. Setup.
        logger.info("${ourIdentity.name.organisation}: Checking and signing transaction.")
        val tx = signingStructure.ptx
        // 2. Get my keys.
        val myKeys = serviceHub.keyManagementService.filterMyKeys(tx.requiredSigningKeys)
        println("My keys: " + myKeys.toList().size)
        // 3. check other sigs are valid.
        val signingIdentities = tx.sigs.map(TransactionSignature::by)
        println("Signed keys: " + signingIdentities.size)
        val allowedToBeMissing = tx.requiredSigningKeys - signingIdentities
        println("Allowed to be missing: " + allowedToBeMissing.size)
        println("Total keys: " + tx.requiredSigningKeys.size)
        tx.verifySignaturesExcept(*allowedToBeMissing.toTypedArray())
        println(myKeys.map { it.toBase58String() })
        // 4. Create sigs.
        val sigs = myKeys.map { key -> serviceHub.createSignature(tx, key) }
        println(sigs.map { it.by.toBase58String() })
        // 5. Add sigs.
        val ptxTwo = tx.withAdditionalSignatures(sigs)
        val neighbours = subFlow(DetermineNeighboursFlow(signingStructure.nettingCycle))
        val nextNode = if (neighbours.size == 1) neighbours.single() else (neighbours - from).first()
        // 6. Assemble new struct.
        val newSigningStruct = SigningStructure(ptxTwo, signingStructure.source, signingStructure.nettingCycle, signingStructure.stateAndRefs)
        // 7. Pass the transaction to the next party.
        subFlow(SendSignedTransactionFlow(nextNode, newSigningStruct))
    }
}

@InitiatingFlow
class SendSignedTransactionFlow(val target: Party,
                                val signingStructure: SigningStructure) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        logger.info("${ourIdentity.name.organisation}: Sending partially signed transaction to ${target.name.organisation}")
        val session = initiateFlow(target)
        subFlow(SendStateAndRefFlow(session, signingStructure.stateAndRefs))
        session.send(signingStructure)
    }
}

@InitiatedBy(SendSignedTransactionFlow::class)
class ReceiveSignedTransactionFlow(val otherFlow: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        subFlow(ReceiveStateAndRefFlow<ContractState>(otherFlow))
        val signingStructure = otherFlow.receive<SigningStructure>().unwrap { it }
        val otherParty = otherFlow.counterparty
        logger.info("${ourIdentity.name.organisation}: Receiving partially signed transaction from ${otherParty.name.organisation}")
        val secretKey = serviceHub.cordaService(TemporaryKeyManager::class.java).key()
        if (signingStructure.source == secretKey) {
            logger.info("${ourIdentity.name.organisation}: We've done a full circle so collected all the party signatures.")
            logger.info("${ourIdentity.name.organisation}: Collecting notary signature.")

            val notarySig = try {
                subFlow(NotaryFlow.Client(signingStructure.ptx))
            } catch (e: NotaryException) {
                null
            }

            if (notarySig != null) {
                val ftx = signingStructure.ptx + notarySig
                logger.info("${ourIdentity.name.organisation}: Notary signature collected - distributing transaction.")
                val neighbours = subFlow(DetermineNeighboursFlow(signingStructure.nettingCycle))
                val nextNode = neighbours.first()
                val payload = Triple(ftx, signingStructure.nettingCycle, signingStructure.source)
                subFlow(SendFinalisedTransactionFlow(nextNode, payload))
            } else {
                logger.info("${ourIdentity.name.organisation}: Double spend! One of the obligations has probably already been settled. Aborting execute flow")
                serviceHub.cordaService(TemporaryKeyManager::class.java).refreshKey()
            }
        } else {
            subFlow(CheckAndSignTransactionFlow(otherParty, signingStructure))
        }
    }
}

@InitiatingFlow
class SendFinalisedTransactionFlow(val target: Party,
                                   val payload: Triple<SignedTransaction, Set<UniqueIdentifier>, AbstractParty>) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        logger.info("${ourIdentity.name.organisation}: Sending finalised transaction to ${target.name.organisation}")
        val session = initiateFlow(target)
        session.send(payload)
    }
}

@InitiatedBy(SendFinalisedTransactionFlow::class)
class ReceiveFinalisedTransactionFlow(val otherFlow: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val secretKey = serviceHub.cordaService(TemporaryKeyManager::class.java).key()
        val payload = otherFlow.receive<Triple<SignedTransaction, Set<UniqueIdentifier>, AbstractParty>>().unwrap { it }
        // Need to do this before you record the Tx!!!
        val neighbours = subFlow(DetermineNeighboursFlow(payload.second))
        val otherParty = otherFlow.counterparty
        val nextNode = if (neighbours.size == 1) neighbours.single() else (neighbours - otherParty).first()
        logger.info("${ourIdentity.name.organisation}: Receiving partially signed transaction from ${otherParty.name.organisation}")
        logger.info("${ourIdentity.name.organisation}: Recording finalised transaction.")
        val customVaultQueryService = serviceHub.cordaService(PersistentObligationQueue::class.java)
        payload.first.tx.inputs
                .map { serviceHub.loadState(it).data }
                .filterIsInstance<Obligation.State>()
                .filter { serviceHub.identityService.wellKnownPartyFromAnonymous(it.borrower) == ourIdentity }
                .forEach {
                    println(it)
                    val result = customVaultQueryService.updateObligationStatus(it.linearId, OBLIGATION_STATUS.SETTLED.ordinal)
                    if (!result) {
                        throw FlowException("Couldn't update obligation in database queue")
                    }
                }
        serviceHub.cordaService(TemporaryKeyManager::class.java).refreshKey()
        serviceHub.recordTransactions(payload.first)
        println(payload.first)
        println(payload.first.tx)
        println(payload.first.sigs)
        if (payload.third == secretKey) {
            logger.info("${ourIdentity.name.organisation}: We've done a full circle so all parties have the transaction.")
        } else {
            subFlow(SendFinalisedTransactionFlow(nextNode, payload))
        }
    }
}

/**
 * Utility flows.
 */
class DetermineNeighboursFlow(val obligations: Set<UniqueIdentifier>) : FlowLogic<Set<Party>>() {
    private fun resolveKey(anonymous: AbstractParty) = serviceHub.identityService.requireWellKnownPartyFromAnonymous(anonymous)
    @Suspendable
    override fun call(): Set<Party> {
        // 1. Setup.
        // 2. Get obligations.
        val query = QueryCriteria.LinearStateQueryCriteria(uuid = obligations.map { it.id })
        val myObligations = serviceHub.vaultService.queryBy<Obligation.State>(query).states.map { it.state.data }
        // 3. Return parties.
        // Should always be able to resolve the anonymous keys as these guys are our counter-parties.
        return myObligations.map { (_, lender, borrower) ->
            val lenderIdentity = resolveKey(lender)
            val borrowerIdentity = resolveKey(borrower)
            if (lenderIdentity == ourIdentity) borrowerIdentity
            else lenderIdentity
        }.toSet()
    }
}
