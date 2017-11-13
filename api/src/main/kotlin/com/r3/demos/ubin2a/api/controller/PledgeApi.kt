package com.r3.demos.ubin2a.api.controller

import com.r3.demos.ubin2a.base.*
import com.r3.demos.ubin2a.pledge.ApprovePledge
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import org.slf4j.Logger
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Path("fund")
class PledgeApi(val services: CordaRPCOps) {
    companion object {
        private val logger: Logger = loggerFor<PledgeApi>()
    }

    /**
     * Request to approve pledge and issue digital currency to requester
     * JSON(application/json):
     * {"receiver" : "BANKSWIFT", "transactionAmount": 100.00}
     */
    @POST
    @Path("pledge")
    @Produces(MediaType.APPLICATION_JSON)
    fun approvePledge(value: TransactionModel): Response {
        val issueAmount = SGD(value.transactionAmount!!)
        val (status, message) = try {
            val maybePledger = services.partiesFromName(value.receiver!!, exactMatch = true)
            if(maybePledger.size != 1) throw IllegalArgumentException("Unknown Party")
            val pledger = maybePledger.single()
            val flowHandle = services.startTrackedFlowDynamic(ApprovePledge.Initiator::class.java, issueAmount, pledger, true)
            flowHandle.progress.subscribe { logger.info("PledgeApi.pledge: $it") }
            val stx = flowHandle.use { it.returnValue.getOrThrow() }
            val result = TransactionModel(
                    transId = stx.tx.id.toString(),
                    sender = services.nodeInfo().legalIdentities.first().name.organisation,
                    receiver = pledger.name.organisation,
                    transactionAmount = issueAmount.quantity.to2Decimals(),
                    currency = SGD.toString()
            )
            Response.Status.CREATED to result
        } catch (ex: Exception) {
            logger.error("Exception during pledge: $ex")
            Response.Status.INTERNAL_SERVER_ERROR to ExceptionModel(statusCode = Response.Status.INTERNAL_SERVER_ERROR.statusCode, msg = ex.message.toString())
        }
        return Response.status(status).entity(message).build()
    }

}