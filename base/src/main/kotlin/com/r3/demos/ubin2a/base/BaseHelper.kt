package com.r3.demos.ubin2a.base

import net.corda.core.crypto.Crypto
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.node.NodeInfo
import net.corda.core.node.ServiceHub
import java.math.BigDecimal
import java.security.PublicKey
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

/** Currency helpers. */
fun Long.to2Decimals() = this.toDouble() / 100
fun Double.roundTo2DecimalPlaces() = BigDecimal(this).setScale(2, BigDecimal.ROUND_HALF_UP).toDouble()
fun Double.toPenny(): Long {
    val parseToText = DecimalFormat("#.00#######").format(Math.abs(this)).replace(",", ".")
    val decimalPlaces = parseToText.length - parseToText.indexOf('.') - 1
    return if (decimalPlaces > 2) throw IllegalArgumentException("Decimal points are larger than the smallest representable currency units")
    else (this.roundTo2DecimalPlaces() * 100).toLong()
}

/** Date serialisation. */
fun Date.toSimpleString(): String {
    val datetimeFormatter = SimpleDateFormat(datetimeFormat)
    return datetimeFormatter.format(this)
}

/** Date deserialisation. */
fun String.toDate(): Date {
    val datetimeFormatter = SimpleDateFormat(datetimeFormat)
    return datetimeFormatter.parse(this)
}

/** PublicKey and Party helpers. */
fun ByteArray.toPublicKey(): PublicKey = Crypto.decodePublicKey(this)

fun PublicKey.toParty(services: ServiceHub) = services.identityService.partyFromKey(this) ?: throw IllegalArgumentException("Unknown Party.")
fun PublicKey.toParty(services: CordaRPCOps) = services.partyFromKey(this) ?: throw IllegalArgumentException("Unknown Party.")

// TODO: Are these necessary??
fun List<ObligationModel>.toTransactionModel(services: CordaRPCOps): List<TransactionModel> {
    val result = mutableListOf<TransactionModel>()
    this.forEach {
        var status: String? = null
        if(it.status != -1) {  status = OBLIGATION_STATUS.values()[it.status].name.toLowerCase() }
        var priority: Int? = null
        if(it.priority != -1) { priority = it.priority }
        result.add(
                TransactionModel(
                        transType = TRANSACTION_TYPE.OBLIGATION.name.toLowerCase(),
                        transId = it.transId,
                        linearId = it.linearId,
                        requestedDate = it.requestedDate,
                        updatedDate = it.updatedDate,
                        sender = it.sender.toParty(services).name.organisation,
                        receiver = it.receiver.toParty(services).name.organisation,
                        transactionAmount = it.transactionAmount,
                        priority = priority,
                        currency = it.currency,
                        status = status
                )
        )
    }
    return result
}

fun ObligationModel.toTransactionModel(services: CordaRPCOps): TransactionModel {
    var status: String? = null
    if(this.status != -1) {  status = OBLIGATION_STATUS.values()[this.status].name.toLowerCase() }
    var priority: Int? = null
    if(this.priority != -1) { priority = this.priority }
    return TransactionModel(
            transType = TRANSACTION_TYPE.OBLIGATION.name.toLowerCase(),
            transId = this.transId,
            linearId = this.linearId,
            requestedDate = this.requestedDate,
            updatedDate = this.updatedDate,
            sender = this.sender.toParty(services).name.organisation,
            receiver = this.receiver.toParty(services).name.organisation,
            transactionAmount = this.transactionAmount,
            priority = priority,
            currency = this.currency,
            status = status)
}

fun NodeInfo.isNotary(services: ServiceHub) = services.networkMapCache.notaryIdentities.any { this.isLegalIdentity(it) }
fun NodeInfo.isNetworkMap() = this.legalIdentities.first().name == NETWORKMAP_X500
fun NodeInfo.isCentralBank() = this.legalIdentities.first().name == CENTRAL_PARTY_X500
fun NodeInfo.isMe(me: NodeInfo) = this.legalIdentities.first().name == me.legalIdentities.first().name
fun NodeInfo.isRegulator() = this.legalIdentities.first().name == REGULATOR_PARTY_X500
