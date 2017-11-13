package com.r3.demos.ubin2a.detect

import com.r3.demos.ubin2a.obligation.Obligation
import net.corda.core.contracts.*
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignatureMetadata
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.internal.abbreviate
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction
import java.math.BigInteger
import java.time.Instant
import java.util.*

/**
 * For the "known transaction hash" stuff. Latest version of M15 mandates that a command is always required.
 * TODO: Refactor the code around the "known transaction hash".
 */
val keypair = Crypto.deriveKeyPairFromEntropy(BigInteger.ONE)
val cmdData = object : CommandData {}
val cmd = Command(cmdData, keypair.public)
val metadata = SignatureMetadata(0, 0)

/**
 * A [ScanRequest] is initially sent from every node which starts a scan (via initialising the [DetectFlow] flow). The
 * requesting nodes send [ScanRequest]s to only those nodes they have [Obligation] relationships with. Recipients
 * of each [ScanRequest] compare the [ScanRequest.id] of all received [ScanRequest]s to determine which should be
 * responded to. The [ScanRequest] with the lowest [ScanRequest.id] wins, all other initiated scans deterministically
 * back-off.
 *
 * All [ScanRequest]s are acknowledged via a [ScanAcknowledgement]. Acknowledging nodes propagate new [ScanRequest]s and
 * [ScanRequest]s with the lowest id to acknowledged all neighbour nodes they share [Obligation] relationships with.
 *
 * Thus, the [ScanRequest] with the lowest id reaches the whole network whilst the others back-off.
 *
 * Each [ScanRequest] contains:
 *
 * @param source The [Party] which originated this [ScanRequest].
 * @param requester The [Party] which initiated the scan (Note: NOT the [Party] which sent the [ScanRequest]).
 * @param currency Perform a netting for obligations denominated in this [Currency].
 * @param startTime The time at which the [requester] started the scan.
 * @param id A random id comprising the [SecureHash.sha256] of the [startTime] and [requester].
 * @param ttl How many milliseconds it takes for this [ScanRequest] to timeout.
 */
@CordaSerializable
data class ScanRequest(val source: Party,
                       val currency: Currency,
                       val requester: Party = source,
                       val startTime: Instant = Instant.now(),
                       val id: SecureHash = SecureHash.sha256(startTime.toString() + source),
                       val ttl: Long = 3000) {
    fun propagateRequest(requester: Party) = copy(requester = requester, ttl = ttl - 100)
    override fun toString() = "ScanRequest(" +
            "id=${id.toString().abbreviate(10)}," +
            "source=${source.name.commonName}," +
            "currency=$currency," +
            "requester=${requester.name.commonName}," +
            "ttl=$ttl)"
}

/**
 * The [ScanAcknowledgement] is sent back to the requester node. Acknowledgements are used to ascertain whether a node
 * is off-line. Nodes that acknowledge a [ScanRequest] are expected to send a response.
 *
 * It is certainly the case that a requesting node will have their [ScanRequest] updated with a new winning one in
 * between the time when a request is sent out and [ScanAcknowledgement]s are expected. As such, because we
 * re-propagate winning [ScanRequest]s, [ScanAcknowledgement]s in respect of old [ScanRequest]s can be safely ignored.
 *
 * @param id The id of the [ScanRequest] this [ScanAcknowledgement] was in respect of.
 */
@CordaSerializable
data class ScanAcknowledgement(val id: SecureHash) {
    override fun toString() = "ScanAcknowledgement(id=${id.toString().abbreviate(10)})"
}

/**
 * Used to keep track of the current state of a [ScanRequest].
 */
@CordaSerializable
enum class Status { SENT, ACKNOWLEDGED, RECEIVED }

/**
 * Either a success or failure depending on the id of the received [ScanRequest]. Successes propagate obligation graphs
 * and cash pledge limits around the network.
 */
@CordaSerializable
sealed class ScanResponse(val id: SecureHash) {
    // TODO: Replace the set of Party's with a set of edges.
    class Success(id: SecureHash,
                  val obligations: Set<Obligation.State>,
                  val limits: LinkedHashMap<AbstractParty, Long>) : ScanResponse(id) {
        override fun toString() = "($id): $obligations, $limits"
    }

    /**
     * Failure response which includes the winning [ScanRequest] id as well as the failing one.
     */
    class Failure(winningId: SecureHash, val failingId: SecureHash) : ScanResponse(winningId)
}

/**
 * Don't care about contract code for now. So use this subbed out contract.
 */
class DummyContract : Contract {
    companion object {
        @JvmStatic
        val DUMMY_CONTRACT_ID = "com.r3.demos.ubin2a.detect.DummyContract"
    }
    override fun verify(tx: LedgerTransaction) = Unit
}

data class KnownState(override val participants: List<AbstractParty>,
                      val magicNumber: Int = 0,
                      override val linearId: UniqueIdentifier = UniqueIdentifier.fromString("067e6162-3b6f-4ae2-a171-2470b63dff00")) : LinearState {
    fun nextRun() = copy(magicNumber = magicNumber + 1)
}
