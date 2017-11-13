package com.r3.demos.ubin2a.base

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import net.corda.core.contracts.Amount
import net.corda.core.identity.CordaX500Name
import net.corda.core.serialization.CordaSerializable
import java.math.BigDecimal
import java.security.PublicKey
import java.util.*

/** Enumerations. */
@CordaSerializable
enum class OBLIGATION_PRIORITY { NORMAL, HIGH }

@CordaSerializable
enum class TRANSACTION_TYPE { PLEDGE, REDEEM, TRANSFER, OBLIGATION, ISSUE }

@CordaSerializable
enum class OBLIGATION_STATUS { ACTIVE, HOLD, CANCELLED, SETTLED }

/** Currency definitions. */
@JvmField val SGD: Currency = Currency.getInstance("SGD")

fun <T : Any> AMOUNT(amount: Int, token: T): Amount<T> = Amount.fromDecimal(BigDecimal.valueOf(amount.toLong()), token)
fun <T : Any> AMOUNT(amount: Double, token: T): Amount<T> = Amount.fromDecimal(BigDecimal.valueOf(amount), token)
fun <T : Any> AMOUNT(amount: Long, token: T): Amount<T> = Amount.fromDecimal(BigDecimal.valueOf(amount), token)
fun SGD(amount: Int): Amount<Currency> = AMOUNT(amount, SGD)
fun SGD(amount: Double): Amount<Currency> = AMOUNT(amount, SGD)
fun SGD(amount: Long): Amount<Currency> = AMOUNT(amount, SGD)
val Int.SGD: Amount<Currency> get() = SGD(this)
val Double.SGD: Amount<Currency> get() = SGD(this)
val Long.SGD: Amount<Currency> get() = SGD(this)

/** Hardcoded party names. */
// TODO: to update X500 name based on the CENTRAL BANK node name in the network
val CENTRAL_PARTY_X500: CordaX500Name = CordaX500Name("MASGSGSG", "Singapore", "SG")

// TODO: to update X500 name based on the REGULATOR node name in the network
val REGULATOR_PARTY_X500: CordaX500Name = CordaX500Name("MASREGULATOR", "Singapore", "SG")

// TODO: to update X500 name based on the NETWORK MAP node name in the network
val NETWORKMAP_X500: CordaX500Name = CordaX500Name("Network Map", "Singapore", "SG")

/** Date format constants**/
val datetimeFormat = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"

@JsonIgnoreProperties(ignoreUnknown = true)

/** Needed to serialise and deserialise JSON objects sent to and from API end-points. */
@CordaSerializable
@JsonInclude(JsonInclude.Include.NON_NULL)
data class BankModel(val bic : String = "",
                     val X500Name: String = "",
                     val balance: Double = 0.00,
                     val incomingSum: Double? = null,
                     val outgoingSum: Double? = null,
                     val position: Double? = null)

@CordaSerializable
data class ObligationModel(var transType: Int = TRANSACTION_TYPE.OBLIGATION.ordinal,
                           var transId: String = "",
                           var linearId: String = "",
                           var requestedDate: String = "",
                           var updatedDate: String = "",
                           var sender: PublicKey,
                           var receiver: PublicKey,
                           var transactionAmount: Double = 0.00,
                           var priority: Int = -1,
                           var currency: String = "",
                           var status: Int = -1)

@CordaSerializable
@JsonInclude(JsonInclude.Include.NON_NULL)
data class TransactionModel(var transType: String? = null,
                            var transId: String? = null,
                            var linearId: String? = null,
                            var enqueue: Int? = null,
                            var sender: String? = null,
                            var receiver: String? = null,
                            var transactionAmount: Double? = null,
                            var priority: Int? = null,
                            var requestedDate: String? = null,
                            var updatedDate: String? = null,
                            var currency: String = SGD.currencyCode,
                            var status: String? = null,
                            var devMode: Boolean? = null)

@CordaSerializable
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ExceptionModel(var statusCode: Int? = null,
                          var transId: String? = null,
                          var msg: String? = null)

@CordaSerializable
@JsonInclude(JsonInclude.Include.NON_NULL)
data class DeadlockModel(val statusCode: Int? = null,
                         val status: String? = null,
                         val msg: String? = null,
                         val inDeadlock: Boolean? = null,
                         val notifiedDate: String? = null,
                         val updatedDate: String? = null)