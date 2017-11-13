package com.r3.demos.ubin2a.api.controller

import com.r3.demos.ubin2a.base.ExceptionModel
import com.r3.demos.ubin2a.base.TransactionModel
import com.r3.demos.ubin2a.redeem.GetRedeemsFlow
import com.r3.demos.ubin2a.redeem.PostApproveRedeem
import com.r3.demos.ubin2a.redeem.PostIssueRedeem
import com.r3.demos.ubin2a.redeem.PrintRedeemURI
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import org.slf4j.Logger
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response


@Path("fund")
class RedeemApi(val services: CordaRPCOps) {
    companion object {
        private val logger: Logger = loggerFor<RedeemApi>()
    }

    /** Returns all active redeem states in vault. */
    @GET
    @Path("redeems")
    @Produces(MediaType.APPLICATION_JSON)
    fun getAllRedeems(): Response {
        logger.info("Running RedeemApi.getAllRedeems")
        val result = ArrayList<TransactionModel>()
        val (status, message) = try {
            val flowHandle = services.startTrackedFlowDynamic(GetRedeemsFlow.Unconsumed::class.java)
            flowHandle.progress.subscribe { logger.info("RedeemApi.GetRedeemsFlow.Unconsumed: $it") }
            result.addAll(flowHandle.use{ it.returnValue.getOrThrow() })
            Response.Status.OK to result
        } catch (ex: Exception){
            logger.error("RedeemApi.GetRedeemsFlow.Unconsumed: $ex")
            Response.Status.INTERNAL_SERVER_ERROR to ExceptionModel(statusCode = Response.Status.INTERNAL_SERVER_ERROR.statusCode, msg = ex.message.toString())
        }
        return Response.status(status).entity(message).build()
    }

    /** Returns the redeem URI in MEPS. */
    @GET
    @Path("redeem/poke")
    @Produces(MediaType.APPLICATION_JSON)
    fun poke(): Response {
        logger.info("Running RedeemApi.poke")
        val (status, message) = try {
            val flowHandle = services.startTrackedFlowDynamic(PrintRedeemURI::class.java)
            val result = flowHandle.returnValue.getOrThrow()
            flowHandle.progress.subscribe { logger.info("RedeemApi.poke: $it") }
            Response.Status.OK to result
        } catch (ex: Exception){
            logger.error("RedeemApi.poke: $ex")
            Response.Status.INTERNAL_SERVER_ERROR to ExceptionModel(statusCode = Response.Status.INTERNAL_SERVER_ERROR.statusCode, msg = ex.message.toString())
        }
        return Response.status(status).entity(message).build()
    }

    /**
     * Post to request redeem to remove funds from DLT
     * JSON(application/json):
     * {"transactionAmount": 100.00}
     */
    @POST
    @Path("redeem")
    @Produces(MediaType.APPLICATION_JSON)
    fun issueRedeem(value: TransactionModel): Response {
        val (status, message) = try {
            val flowHandle = services.startTrackedFlowDynamic(PostIssueRedeem::class.java, value.transactionAmount)
            flowHandle.progress.subscribe { logger.info("RedeemApi.issueRedeem: $it") }
            val result = flowHandle.use {
                it.returnValue.getOrThrow()
            }
            Response.Status.CREATED to result
        } catch (ex: Exception) {
            logger.error("Exception during issueRedeem: $ex")
            Response.Status.INTERNAL_SERVER_ERROR to ExceptionModel(statusCode = Response.Status.INTERNAL_SERVER_ERROR.statusCode, msg = ex.message.toString())
        }
        return Response.status(status).entity(message).build()
    }

    /**
     * Approve the redeem request to remove funds from DLT
     * JSON(application/json):
     * {"transId": "3434F10963EED85B0241E8006C5711DF39B80243F1BE74CE044B4363BAA03A5E"}
     */
    @PUT
    @Path("redeem")
    @Produces(MediaType.APPLICATION_JSON)
    fun approveRedeem(value: TransactionModel): Response {
        val (status, message) = try {

            val devMode = if(value.devMode!=null){
                value.devMode
            } else { false }

            val flowHandle = services.startTrackedFlowDynamic(PostApproveRedeem::class.java, value.transId, devMode)
            flowHandle.progress.subscribe { logger.info("RedeemApi.approveRedeem: $it") }
            val result = flowHandle.use {
                it.returnValue.getOrThrow()
            }
            Response.Status.CREATED to result
        } catch (ex: Exception) {
            logger.error("Exception during issueRedeem: $ex")
            Response.Status.INTERNAL_SERVER_ERROR to ExceptionModel(statusCode = Response.Status.INTERNAL_SERVER_ERROR.statusCode, msg = ex.message.toString())
        }
        return Response.status(status).entity(message).build()
    }

}