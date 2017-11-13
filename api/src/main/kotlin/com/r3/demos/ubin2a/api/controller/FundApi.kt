package com.r3.demos.ubin2a.api.controller

import com.r3.demos.ubin2a.base.ExceptionModel
import com.r3.demos.ubin2a.base.TransactionModel
import com.r3.demos.ubin2a.cash.PostTransfersFlow
import net.corda.core.contracts.StateAndRef
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.finance.contracts.asset.Cash
import org.slf4j.Logger
import javax.ws.rs.GET
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response


// TODO: Add a basic front-end to issue cash and list all cash issued.
// TODO: Use the Bank of Corda instead of self issuing cash.

@Path("fund")
class FundApi(val services: CordaRPCOps) {
    companion object {
        private val logger: Logger = loggerFor<FundApi>()
    }

    /** Returns all cash states in the vault. */
    @GET
    @Path("cash")
    @Produces(MediaType.APPLICATION_JSON)
    fun getCash(): List<StateAndRef<Cash.State>> {
        val vaultStates = services.vaultQueryBy<Cash.State>()
        return vaultStates.states
    }

    /**
     * Transfers digital currency between commercial banks
     * JSON(application/json):
     * { "priority": 1, "receiver": "BANKSWIFT","transactionAmount": 200.00 }
     */
    @POST
    @Path("transfer")
    @Produces(MediaType.APPLICATION_JSON)
    fun transferCash(value: TransactionModel): Response {
        val (status, message) = try {
            val flowHandle = services.startTrackedFlowDynamic(PostTransfersFlow::class.java, value)
            flowHandle.progress.subscribe { logger.info("FundApi.transferCash: $it") }
            val result = flowHandle.use {
                it.returnValue.getOrThrow()
            }
            Response.Status.CREATED to result
        } catch (ex: Exception) {
            logger.error("Exception during transferCash: $ex")
            Response.Status.INTERNAL_SERVER_ERROR to ExceptionModel(statusCode = Response.Status.INTERNAL_SERVER_ERROR.statusCode, msg = ex.message.toString())
        }
        return Response.status(status).entity(message).build()
    }

}