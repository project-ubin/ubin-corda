package com.r3.demos.ubin2a.detect

import co.paralleluniverse.fibers.Suspendable
import com.r3.demos.ubin2a.base.*
import com.r3.demos.ubin2a.detect.DummyContract.Companion.DUMMY_CONTRACT_ID
import com.r3.demos.ubin2a.obligation.GetQueue
import com.r3.demos.ubin2a.obligation.InternalObligation
import com.r3.demos.ubin2a.obligation.Obligation
import net.corda.core.contracts.Amount
import net.corda.core.contracts.PrivacySalt
import net.corda.core.contracts.StateAndContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap
import net.corda.finance.contracts.getCashBalance
import java.util.*
import kotlin.collections.LinkedHashMap

// TODO: Read the design document and check this is implemented to specification.
// TODO: Address all the timing / timeout features.
// TODO: Purge all data on all nodes once the scan is complete (so we can run the scan multiple times).

/**
 * The detect flow entry-point. This flow can be started by multiple nodes concurrently.
 * Annoyingly, despite the fact that the winning flow finishes on a node which initialises THIS flow, as flows on the
 * same node cannot communicate with each other, we must use Corda transactions to alert this flow that the scan has
 * finished. We can block this flow using [FlowLogic.waitForLedgerCommit] with a prior known transaction hash. This is
 * a nasty hack but unfortunately there's no other way to block the flow.
 *
 * The flow returns the obligation graph. The output of this flow will be used to call the netting algorithm but the
 * flow can also be called via a web API or RPC if one just wants to demonstrate the algorithm.
 *
 * Note: In the case where the obligation graph can be disconnected, then we'll need to check the resulting scans from
 * all nodes -- there will be a winning scan for each disconnected sub-graph.
 */
@StartableByRPC
class DetectFlow(val currency: Currency) : FlowLogic<Pair<Set<Obligation.State>, Map<AbstractParty, Long>>>() {
    private fun generateTxHash(): SecureHash {
        // TODO: When RC3 is released. vaultQueryService -> vaultService
        val everyone = serviceHub.networkMapCache.allNodes.map { it.legalIdentities.first() } + ourIdentity
        val sortedEveryone = everyone.toSortedSet(compareBy<Party> { it.name.organisation })
        val maybeLatestKnownState = serviceHub.vaultService.queryBy<KnownState>().states
        println(maybeLatestKnownState)
        val notary = serviceHub.networkMapCache.notaryIdentities.firstOrNull()
                ?: throw IllegalStateException("No available notary.")
        val salt = ByteArray(32)
        salt[0] = 1
        val utx = TransactionBuilder(notary = notary, privacySalt = PrivacySalt(salt))
        if (maybeLatestKnownState.isEmpty()) {
            println("There's no known state")
            println(everyone)
            utx.withItems(StateAndContract(KnownState(sortedEveryone.toList()), DUMMY_CONTRACT_ID), cmd)
            val stx = serviceHub.signInitialTransaction(utx)
            val stx2 = stx.withAdditionalSignature(keypair, metadata)
            println("Known transaction hash: " + stx2.id)
            return stx2.id
        } else {
            println("There's a known state")
            val input = maybeLatestKnownState.single()
            val output = input.state.data.nextRun()
            utx.withItems(input, StateAndContract(output, DUMMY_CONTRACT_ID), cmd)
            val stx = serviceHub.signInitialTransaction(utx)
            val stx2 = stx.withAdditionalSignature(keypair, metadata)
            println("Known transaction hash: " + stx2.id)
            return stx2.id
        }
    }

    @Suspendable
    override fun call(): Pair<Set<Obligation.State>, Map<AbstractParty, Long>> {
        logger.info("${ourIdentity.name.organisation}: Starting Scan")
        // 1. Get the set of all our neighbours
        val neighbours = subFlow(ScanNeighbours())
        logger.info("${ourIdentity.name.organisation}: Neighbours=${neighbours.map { it.name.organisation }}")
        // 2. Abort the scan if this node has no obligations - it shouldn't show up in the resulting graph.
        require(neighbours.isNotEmpty()) { logger.info("No obligations in the vault. Aborting Scan.") }
        // 3. Create a new ScanRequest.
        val req = ScanRequest(ourIdentity, currency)
        logger.info("${ourIdentity.name.organisation}: $req")
        // 4. Store the ScanRequest.
        DataStore.updateScanRequest(ourIdentity, req)
        // 5. Create a subsequent version of the request, with a lower TTL, to propagate forward.
        val newReq = req.propagateRequest(ourIdentity)
        // 6. Propagate the ScanRequest and record that we've sent it to our neighbours.
        neighbours.forEach { party ->
            DataStore.initState(ourIdentity, party)
            subFlow(SendScanRequest(party, newReq))
        }
        // 7. This is a hack to block the flow until a transaction with a known hash is recorded.
        val knownHash = generateTxHash()
        waitForLedgerCommit(knownHash)
        logger.info("${ourIdentity.name.organisation}: Scanning finished.")
        // 8. Check who's the winner and output the graph.
        if (DataStore.scanRequest[ourIdentity]?.source == ourIdentity) {
            logger.info("${ourIdentity.name.organisation}: I'm the winner :D")
            val obligations = DataStore.getObligations(ourIdentity) ?: throw IllegalStateException("Something went wrong.")
            val limits = LinkedHashMap(DataStore.getLimits(ourIdentity))
            DataStore.purge(ourIdentity)
            val filteredEveryone = serviceHub.networkMapCache.allNodes.filter {
                nodeInfo -> nodeInfo.isNotary(serviceHub).not() && nodeInfo.isRegulator().not() && nodeInfo.isNetworkMap().not()
            }
            val everyone = filteredEveryone.map { it.legalIdentities.first() }
            everyone.forEach { party -> subFlow(SendPurgeRequest(party)) }
            return Pair(obligations, limits.toMap())
        } else {
            return Pair(setOf(), mapOf())
        }
    }
}

@InitiatingFlow
class SendPurgeRequest(val target: Party) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val session = initiateFlow(target)
        session.send(Unit)
    }
}

@InitiatedBy(SendPurgeRequest::class)
class ReceivePurgeRequest(val otherFlow: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        otherFlow.receive<Unit>()
        DataStore.purge(ourIdentity)
    }
}

/**
 * This flow queries the vault service to determine which parties share obligation relationships with the calling node.
 * @returns a set of neighbour [Party]s
 */
class ScanNeighbours : FlowLogic<Set<Party>>() {
    // Helper for converting an AnonymousPArty to an AbstractParty.
    private fun resolveKey(anonymous: AbstractParty) = serviceHub.identityService.requireWellKnownPartyFromAnonymous(anonymous)

    @Suspendable
    override fun call(): Set<Party> {
        // 1. Get all Obligation states from the vault.
        val states = serviceHub.vaultService.queryBy<Obligation.State>().states.map { it.state.data }
        // 2. Return all obligation counter-parties. Note: We can do this without compromising confidentiality as we'll
        // only ever pick out our immediate counter-parties.
        return states.map { (_, anonymousLender, anonymousBorrower) ->
            // 3. We also need to resolve anonymous keys to known identities.
            val lender = resolveKey(anonymousLender)
            val borrower = resolveKey(anonymousBorrower)
            if (borrower == ourIdentity) lender
            else borrower
        }.toSet()
    }
}

/**
 * Utility flow which sends a [ScanRequest]. Simple.
 */
@InitiatingFlow
class SendScanRequest(val target: Party, val request: ScanRequest) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val session = initiateFlow(target)
        logger.info("${ourIdentity.name.organisation}: Sending ScanRequest to ${target.name.organisation}")
        session.send(request)
    }
}

/**
 * A handler for received [ScanRequest]s. [ScanAcknowledgement]s are sent for ALL received [ScanRequest]s. After the
 * [ScanAcknowledgement] has been sent a number of things may possibly happen:
 *
 * 1. We didn't start [DetectFlow], therefore the received [ScanRequest] is the first we are aware of. Store and
 * propagate the [ScanRequest].
 *
 * 2. We already have a [ScanRequest] which we believe is the winning one and it has the same id as the one we have just
 * received. Send back a [ScanResponse.Success] with our obligation edges.
 *
 * 3. We already have a [ScanRequest] which we believe is the winning one, however the id of the [ScanRequest] we just
 * received is lower than ours. As such, we update the winning [ScanRequest] and propagate it to our neighbours if there
 * are any. Note: We don't send it BACK to the guy which just sent us the [ScanRequest]. If there is no-one else to send
 * the [ScanRequest] to, then we trigger the scanning termination case which is to send back a [ScanResponse.Success] to
 * the node which sent us the [ScanRequest].
 *
 * 4. We already have a [ScanRequest] which we believe is the winning one and it just so happens that it IS the
 * winning one. We send a [ScanResponse.Failure] back to the requesting node.
 */
@InitiatedBy(SendScanRequest::class)
class ReceiveScanRequest(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call(): Unit {
        // 1. Setup.
        val otherParty = otherSession.counterparty
        logger.info("${ourIdentity.name.organisation}: Receiving ScanRequest from ${otherParty.name.organisation}")
        // 2. Receive the ScanRequest.
        // TODO: Recalculate the ScanRequest hash to ensure no cheating.
        // TODO: Discard any ScanRequests from non-adjacent nodes -> responding to them would lead to a privacy leak.
        val request = otherSession.receive<ScanRequest>().unwrap { it }
        // 3. Send back ScanAcknowledgements. We always do this and it is to determine if the recipient node (i.e. us)
        // is online, or not.
        val ack = ScanAcknowledgement(request.id)
        subFlow(SendScanAcknowledgement(otherParty, ack))
        // 4. Get the ScanRequest we are currently storing. We might not have one if we didn't start a scan.
        val maybeWinningScanRequest = DataStore.scanRequest[ourIdentity]
        val newReq = request.propagateRequest(ourIdentity)
        // 5. Determine whom we must propagate the ScanRequest to if it is necessary to do so. We shouldn't send it back
        // to the node we just received it from or we end up with a bunch of spurious messages.
        val neighbours = subFlow(ScanNeighbours())
        val neighboursMinusRequester = neighbours - otherParty
        // 6. Generate or get the random key for the cash limits.
        val randomKey = serviceHub.cordaService(TemporaryKeyManager::class.java).key()
        // 6. We don't have a ScanRequest stored yet as we didn't start a Scan.
        if (maybeWinningScanRequest == null) {
            logger.info("${ourIdentity.name.organisation}: No ScanRequest recorded by ${ourIdentity.name.organisation}")
            DataStore.updateScanRequest(ourIdentity, request)
            if (neighboursMinusRequester.isEmpty()) {
                // It's time to respond back to the requester.
                logger.info("${ourIdentity.name.organisation}: The requester is our only neighbours, so sending them a response now.")
                // TODO: Refactor this code out into a another method.
                val obligations = subFlow(GetObligations())
                val limit = serviceHub.getCashBalance(request.currency).quantity
                val success = ScanResponse.Success(request.id, obligations, linkedMapOf(randomKey to limit))
                subFlow(SendScanResponse(otherParty, success))
            } else {
                logger.info("${ourIdentity.name.organisation}: Propagating new ScanRequest to neighbours.")
                neighbours.forEach { party ->
                    DataStore.initState(ourIdentity, party)
                    subFlow(SendScanRequest(party, newReq))
                }
            }
            return
        }

        // 6. We have a ScanRequest stored. Compare the hash our ours with the hash of the new one.
        val currentScanRequestId = maybeWinningScanRequest.id
        val receivedScanRequestId = request.id
        // Compare the hashes. '>' and '<' are aliases for 'compareTo'.
        when {
            currentScanRequestId > receivedScanRequestId -> {
                // 7. Our ScanRequest has a higher hash value than the one we just received.
                // Replace the old ScanRequest with the one we have just received. We now need to ignore all
                // acknowledgements and obligations pertaining to the old ScanRequest. Then purge the acknowledgements
                // list. We don't care about obligations for an old scan request which has subsequently being superseded
                // by another. In the ReceiveScanResponse flow we discard obligations with old (higher) hashes.
                logger.info("${ourIdentity.name.organisation}: Lose :( $currentScanRequestId loses vs $receivedScanRequestId")
                DataStore.updateScanRequest(ourIdentity, request)
                DataStore.purgeState(ourIdentity)
                if (neighboursMinusRequester.isEmpty()) {
                    // It's time to respond back to the requester.
                    // TODO: Refactor this code out into a another method.
                    val obligations = subFlow(GetObligations())
                    val limit = serviceHub.getCashBalance(request.currency).quantity
                    val success = ScanResponse.Success(request.id, obligations, linkedMapOf(randomKey to limit))
                    subFlow(SendScanResponse(otherParty, success))
                } else {
                    neighbours.forEach { party ->
                        DataStore.initState(ourIdentity, party)
                        subFlow(SendScanRequest(party, newReq))
                    }
                }
            }
            currentScanRequestId == receivedScanRequestId -> {
                // 7. Search termination case. We already have the winning ScanRequest. Send back an edge.
                logger.info("${ourIdentity.name.organisation}: Draw! $currentScanRequestId is the same as $receivedScanRequestId. " +
                        "Sending back a SUCCESS to ${otherParty.name.organisation}!")
                val obligations = subFlow(GetObligations())
                val limit = serviceHub.getCashBalance(request.currency).quantity
                val success = ScanResponse.Success(receivedScanRequestId, obligations, linkedMapOf(randomKey to limit))
                subFlow(SendScanResponse(otherParty, success))
            }
            currentScanRequestId < receivedScanRequestId -> {
                // 7. We have a ScanRequest with a lower id than the one received. It might not be the winning ScanRequest
                // but we definitely know that the requester's ScanRequest should back-off.
                logger.info("${ourIdentity.name.organisation}: Win :D $currentScanRequestId wins vs $receivedScanRequestId. " +
                        "Sending back a FAILURE to ${otherParty.name.organisation}!")
                val failure = ScanResponse.Failure(currentScanRequestId, receivedScanRequestId)
                subFlow(SendScanResponse(otherParty, failure))
            }
        }
    }
}

/**
 * For sending a [ScanAcknowledgement]. Simple.
 */
@InitiatingFlow
class SendScanAcknowledgement(val target: Party, val ack: ScanAcknowledgement) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val session = initiateFlow(target)
        logger.info("${ourIdentity.name.organisation}: Sending ScanAcknowledgement to ${target.name.organisation}")
        session.send(ack)
    }
}

/**
 * Handler for receiving [ScanAcknowledgement]s.
 *
 * The only thing to watch for here is that we need to discard [ScanAcknowledgement]s that a received in respect of
 * old [ScanRequest]s.
 *
 * TODO: Deal with timeouts.
 */
@InitiatedBy(SendScanAcknowledgement::class)
class ReceiveScanAcknowledgement(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call(): Unit {
        // 1. Setup.
        val otherParty = otherSession.counterparty
        logger.info("${ourIdentity.name.organisation}: Receiving ScanAcknowledgement from ${otherParty.name.organisation}")
        // 2. Receive and handle the ScanAcknowledgement.
        // If we are expecting acknowledgements, then winningScanRequest will always be set.
        // During the time when we propagated a ScanRequest, we have may have received a new ScanRequest with a lower id
        // from another node. Therefore we always check the acknowledgement id matches the current winning ScanRequest id
        // before storing it and discard any Acknowledgements in respect of old ScanRequests.
        otherSession.receive<ScanAcknowledgement>().unwrap { (id) ->
            val scanRequest = DataStore.getScanRequest(ourIdentity) ?: throw IllegalStateException("Something went wrong.")
            if (id == scanRequest.id) {
                logger.info("${ourIdentity.name.organisation}: Acknowledgement hash matches the current ScanRequest hash. Recording it.")
                DataStore.updateState(ourIdentity, otherParty, Status.ACKNOWLEDGED)
            }
        }
    }
}

/**
 * For sending a [ScanResponse]. Simple.
 */
@InitiatingFlow
class SendScanResponse(val target: Party, val response: ScanResponse) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val session = initiateFlow(target)
        logger.info("${ourIdentity.name.organisation}: Sending ScanResponse to ${target.name.organisation}")
        session.send(response)
    }
}

/**
 * For handling receipt of [ScanResponse]s. We expect there always to be a stored [ScanRequest] and current state of
 * said [ScanRequest]. If we recieve a success one of three things happen:
 *
 * 1. We are the source of the winning [ScanRequest] and there are no more [ScanResponse]s to receive. At this point we
 * can terminate the algorithm as we have an elected leader that has all required obligations.
 * 2. We are not the source of the winning [ScanRequest] but we have no more [ScanResponse]s to receive. Forward all
 * collected [ScanResponse]s to whichever node sent us the winning [ScanRequest].
 * 3. We could either be the source node or not AND we haven't received all obligations. So just store the received
 * [ScanResponse] and do nothing else.
 *
 * If we receive a failure then there's no need to
 */
@InitiatedBy(SendScanResponse::class)
class ReceiveScanResponse(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    fun signalCompletion() {
        val everyone = (serviceHub.networkMapCache.allNodes.map { it.legalIdentities.first() } + ourIdentity)
        val sortedEveryone = everyone.toSortedSet(compareBy<Party> { it.name.organisation })
        // TODO: If there are multiple notaries we need to always grab the same one otherwise the hash will change.
        // Hack to set the privacy salt to a known value.
        val maybeLatestKnownState = serviceHub.vaultService.queryBy<KnownState>().states
        val notary = serviceHub.networkMapCache.notaryIdentities.firstOrNull() // TODO: Need to always get the same notary.
        val salt = ByteArray(32)
        salt[0] = 1
        val utx = TransactionBuilder(notary = notary, privacySalt = PrivacySalt(salt))
        if (maybeLatestKnownState.isEmpty()) {
            println("Adding a new knownstate")
            utx.withItems(StateAndContract(KnownState(sortedEveryone.toList()), DUMMY_CONTRACT_ID), cmd)
        } else {
            println("Updating a previously known knownstate")
            val input = maybeLatestKnownState.single()
            val output = input.state.data.nextRun()
            utx.withItems(input, StateAndContract(output, DUMMY_CONTRACT_ID), cmd)
        }
        val stx = serviceHub.signInitialTransaction(utx)
        val stx2 = stx.withAdditionalSignature(keypair, metadata)
        println("Known transaction hash: " + stx2)
        subFlow(FinalityFlow(stx2, everyone.toSet()))
    }

    @Suspendable
    fun handleSuccess(req: ScanRequest, res: ScanResponse.Success) {
        // 1. Get current state.
        val neighbours = DataStore.getState(ourIdentity) ?: throw Exception("Something went wrong.")
        // 3. Update obligations.
        DataStore.updateObligations(ourIdentity, res.obligations + subFlow(GetObligations()))
        // 2. Mark this request as received.
        DataStore.updateState(ourIdentity, otherSession.counterparty, Status.RECEIVED)
        // 4. Update limits.
        val myLimit = serviceHub.getCashBalance(req.currency).quantity
        val randomKey = serviceHub.cordaService(TemporaryKeyManager::class.java).key()
        val newLimits = res.limits + mapOf(randomKey to myLimit)
        DataStore.updateLimits(ourIdentity, LinkedHashMap(newLimits))
        // 5. Have we received all obligations we expect?
        val finished: Boolean = neighbours.all { it.value == Status.RECEIVED }
        if (finished) {
            if (req.source == ourIdentity) {
                logger.info("${ourIdentity.name.organisation}: No more obligations to receive and I'm the source so we are done! Winner=${res.id}")
                signalCompletion()
            } else {
                logger.info("${ourIdentity.name.organisation}: No more obligations to receive. Build ours and send back to ${req.requester}.")
                val obligations = DataStore.getObligations(ourIdentity)?.toSet() ?: throw IllegalStateException("Something went wrong.")
                val limits = LinkedHashMap(DataStore.getLimits(ourIdentity))
                val success = ScanResponse.Success(req.id, obligations, limits)
                subFlow(SendScanResponse(req.requester, success))
            }
        } else {
            val remaining = neighbours.count { it.value != Status.RECEIVED }
            logger.info("${ourIdentity.name.organisation}: $remaining obligations to receive.")
        }
    }

    @Suspendable
    fun handleFailure(req: ScanRequest, res: ScanResponse.Failure) {
        // If we are not the source then send a failure back to the requester.
        if (req.source != req.requester) {
            logger.info("${ourIdentity.name.organisation}: Propagating failure to requesting node.")
            val failure = ScanResponse.Failure(res.id, res.failingId)
            subFlow(SendScanResponse(req.requester, failure))
        }
    }

    @Suspendable
    override fun call(): Unit {
        // 1. Setup.
        val otherParty = otherSession.counterparty
        logger.info("${ourIdentity.name.organisation}: Receiving ScanResponse from ${otherParty.name.organisation}")
        // 2. Receive Response.
        val scanResponse = otherSession.receive<ScanResponse>().unwrap { it }
        // 3. Get the current ScanRequest.
        val scanRequest = DataStore.getScanRequest(ourIdentity) ?: throw IllegalStateException("Something went wrong.")
        // 4. Discard Responses for old ScanRequests.
        if (scanResponse.id != scanRequest.id) {
            logger.info("${ourIdentity.name.organisation}: Received a response for an old ScanRequest. Ignoring.")
            return
        }
        // 5. Handle the response.
        when (scanResponse) {
            is ScanResponse.Success -> {
                // If we are the source of the scan and we've received all obligations then this terminates the algorithm.
                // If we are not the source but we have received all obligations, then we forward them to whichever node
                // send us the winning ScanRequest.
                // If we haven't received all the obligations, then just store the response.
                logger.info("${ourIdentity.name.organisation}: Received a success.")
                handleSuccess(scanRequest, scanResponse)
            }
            is ScanResponse.Failure -> {
                // We've received a failure. Back-off. We need to stop everything we are doing.
                // Don't bother recording the response as we have a different ScanRequest stored (that has a lower hash).
                logger.info("${ourIdentity.name.organisation}: Received a ScanResponse.Failure. Clash with ${scanResponse.id}. Aborting.")
                handleFailure(scanRequest, scanResponse)
            }
        }
    }
}

class GetObligations : FlowLogic<Set<Obligation.State>>() {
    @Suspendable
    override fun call(): Set<Obligation.State> {
        // Step 1. Get the outgoing obligations IDs.
        val outgoingObligations = subFlow(GetQueue.OutgoingWithStatus(OBLIGATION_STATUS.ACTIVE.ordinal)).map {
            InternalObligation(
                    Obligation.State(
                            amount = Amount(it.transactionAmount.toLong() * 100, Currency.getInstance(it.currency)),
                            lender = AnonymousParty(it.receiver),
                            borrower = AnonymousParty(it.sender),
                            linearId = UniqueIdentifier.fromString(it.linearId),
                            issueDate = it.requestedDate.toDate().toInstant()
                    ), it.priority
            )
        }

        // Step 2. Group obligations by lender.
        val obligationsPerCounterparty = outgoingObligations.groupBy {
            serviceHub.identityService.requireWellKnownPartyFromAnonymous(it.lender)
        }
        // TODO: This ordering should be done by the queue!
        // Step 3. For each group, order the obligations and pick the highest priority one.
        val topPriorities = obligationsPerCounterparty.map { (_, value) ->
            val highPriority = value.sortedBy { it.issueDate }.filter { it.priority == 1 }
            val lowPriority = value.sortedBy { it.issueDate }.filter { it.priority == 0 }
            val priorityQueue = highPriority + lowPriority
            logger.info("${ourIdentity.name.organisation}: Queued obligations: " + priorityQueue)
            val obligation = priorityQueue.first().toExternal()
            // Replace the random keys with a common key for each party. We need to do this to create an adjacency
            // matrix during the planning phase.
            val lenderRandomKey = subFlow(RequestKeyFlow(obligation.lender))
            val borrowerRandomKey = serviceHub.cordaService(TemporaryKeyManager::class.java).key()
            obligation.copy(lender = lenderRandomKey, borrower = borrowerRandomKey)
        }
        // Step 4. Return the top priority obligations.
        println(topPriorities.toSet())
        return topPriorities.toSet()
    }
}

@InitiatingFlow
class RequestKeyFlow(val anonymousParty: AbstractParty) : FlowLogic<AnonymousParty>() {
    @Suspendable
    override fun call(): AnonymousParty {
        val namedParty = serviceHub.identityService.requireWellKnownPartyFromAnonymous(anonymousParty)
        val session = initiateFlow(namedParty)
        return session.sendAndReceive<AnonymousParty>(Unit).unwrap { it }
    }
}

@InitiatedBy(RequestKeyFlow::class)
class SendKeyFlow(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call(): Unit {
        otherSession.receive<Unit>()
        val myKey = serviceHub.cordaService(TemporaryKeyManager::class.java).key()
        otherSession.send(myKey)
    }
}

///**
// * Returns all net obligations with neighbours as a single directed edge.
// * TODO: Update this per Dave's new design document.
// * Only pick out obligations where we are the borrower.
// * For each counterparty, pick out the highest priority obligation as per the queue.
// */
//class ScanNeighbourNetObligations : FlowLogic<LinkedHashMap<Edge, Long>>() {
//    @Suspendable
//    override fun call(): LinkedHashMap<Edge, Long> {
//        val groups = LinkedHashMap<Edge, Long>()
//        val states = serviceHub.vaultQueryService.queryBy<Obligation.State>().states.map { it.state.data }
//        states.forEach { (amount, lender, borrower) ->
//            val quantity = amount.quantity
//            val edge = Edge(lender as Party, borrower as Party)
//            val entry: Long? = groups[edge]
//            when {
//                entry != null -> groups.put(edge, entry + quantity)
//                groups.containsKey(edge.reverse()) -> groups.put(edge.reverse(), groups[edge.reverse()]!! - quantity)
//                else -> groups.put(edge, quantity)
//            }
//        }
//        return groups
//    }
//}