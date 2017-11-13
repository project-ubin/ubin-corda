package com.r3.demos.ubin2a.api.controller

import com.r3.demos.ubin2a.account.BalanceByBanksFlow
import com.r3.demos.ubin2a.account.GetBalanceFlow
import com.r3.demos.ubin2a.account.GetTransactionHistory
import com.r3.demos.ubin2a.base.*
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import org.slf4j.Logger
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Path("bank")
class AccountApi(val services: CordaRPCOps) {
    /** I am always the first identity returned, apparently... */
    private val me: CordaX500Name = services.nodeInfo().legalIdentities.first().name

    companion object {
        private val logger: Logger = loggerFor<AccountApi>()
    }

    /** For JSON serialisation */
    @CordaSerializable
    data class PartyInfo(val bic: String, val X500Name: String) {
        companion object {
            fun fromNodeInfo(nodeInfo: NodeInfo): PartyInfo {
                val party = nodeInfo.legalIdentities.first()
                return PartyInfo(party.name.organisation, party.toString())
            }
        }
    }

    /** Helpers for filtering the network map cache. */
    private fun isNotary(nodeInfo: NodeInfo) = services.notaryIdentities().any { nodeInfo.isLegalIdentity(it) }
    private fun isNetworkMap(nodeInfo: NodeInfo) = nodeInfo.legalIdentities.first().name == NETWORKMAP_X500
    private fun isCentralBank(nodeInfo: NodeInfo) = nodeInfo.legalIdentities.first().name == CENTRAL_PARTY_X500
    private fun isMe(nodeInfo: NodeInfo) = nodeInfo.legalIdentities.first().name == me
    private fun isRegulator(nodeInfo: NodeInfo) = nodeInfo.legalIdentities.first().name == REGULATOR_PARTY_X500

    /** Returns list of assets that exist in the vault. */
    @GET
    @Path("assets")
    @Produces(MediaType.APPLICATION_JSON)
    fun getAssets(): List<StateAndRef<ContractState>> {
        return services.vaultQueryBy<ContractState>().states
    }

    /** Returns list of all transactions that exist in the vault. */
    @GET
    @Path("transactions/raw")
    @Produces(MediaType.APPLICATION_JSON)
    fun getRawTransactions(): List<SignedTransaction> {
        // TODO: Find out what supersedes this.
        return services.internalVerifiedTransactionsSnapshot()
    }

    /** Returns the node's name. */
    @GET
    @Path("info")
    @Produces(MediaType.APPLICATION_JSON)
    fun getBalance(): Response {
        val result: BankModel
        val (status, message) = try {
            val flowHandle = services.startTrackedFlowDynamic(GetBalanceFlow::class.java)
            flowHandle.progress.subscribe { logger.info("Api.getBalance: $it") }
            result = flowHandle.use { it.returnValue.getOrThrow() }
            Response.Status.OK to result
        } catch (ex: Exception) {
            logger.error("Api.getBalance: $ex")
            Response.Status.INTERNAL_SERVER_ERROR to ExceptionModel(statusCode = Response.Status.INTERNAL_SERVER_ERROR.statusCode, msg = ex.message.toString())
        }
        return Response.status(status).entity(message).build()
    }

    /** Returns the all other banks in the network. */
    @GET
    @Path("counterparties")
    @Produces(MediaType.APPLICATION_JSON)
    fun getPeers(): List<PartyInfo> {
        return services.networkMapSnapshot().filter { nodeInfo ->
            isNotary(nodeInfo).not() && isMe(nodeInfo).not() && isRegulator(nodeInfo).not() && isNetworkMap(nodeInfo).not()
        }.map { PartyInfo.fromNodeInfo(it) }
    }

    /** Returns everyone in the network. */
    @GET
    @Path("everyone")
    @Produces(MediaType.APPLICATION_JSON)
    fun getAllNodes(): List<PartyInfo> {
        val networkMap = services.networkMapSnapshot()
        return networkMap.map { PartyInfo.fromNodeInfo(it) }
    }

    /** Returns the balances of all banks in the network. Only can be run by central bank. */
    @GET
    @Path("balance/all")
    @Produces(MediaType.APPLICATION_JSON)
    fun getBankBalances(): Response {

        logger.info("Getting all bank balance")
        val (status, message) = try {
            val flowHandle = services.startTrackedFlowDynamic(BalanceByBanksFlow.Initiator::class.java)
            flowHandle.progress.subscribe { logger.info("CashApi.getBankBalances: $it") }
            val result = flowHandle.use {
                it.returnValue.getOrThrow()
            }
            Response.Status.OK to result
        } catch (ex: Exception) {
            logger.error("Exception during transferCash: $ex")
            Response.Status.INTERNAL_SERVER_ERROR to ExceptionModel(statusCode = Response.Status.INTERNAL_SERVER_ERROR.statusCode, msg = ex.message.toString())
        }
        return Response.status(status).entity(message).build()
    }


    /** Returns all transactions or by path param */
    @GET
    @Path("transactions{noop: (/)?}{transType: (.+)?}")
    @Produces(MediaType.APPLICATION_JSON)
    fun getTransactionHistory(@PathParam("transType") transType: String = ""): Response {

        var selectedTransType: Int? = null
        val result = mutableListOf<TransactionModel>()
        logger.info("transtype path param " + transType)
        val (status, message) = try {

            if(transType != "") { selectedTransType = TRANSACTION_TYPE.valueOf(transType.trim().toUpperCase()).ordinal }
            val flowHandle = services.startTrackedFlowDynamic(GetTransactionHistory::class.java, selectedTransType)
            flowHandle.progress.subscribe { logger.info("Api.GetTransactionHistory: $it") }
            result.addAll(flowHandle.use { it.returnValue.getOrThrow() })
            Response.Status.OK to result
        } catch (ex: Exception){
            logger.error("Api.GetTransactionHistory: $ex")
            Response.Status.INTERNAL_SERVER_ERROR to
                    ExceptionModel(
                            statusCode = Response.Status.INTERNAL_SERVER_ERROR.statusCode,
                            msg = ex.message.toString())
        }
        return Response.status(status).entity(message).build()
    }

}
