package com.r3.demos.ubin2a.api.controller

import com.r3.demos.ubin2a.account.CheckDeadlockFlow
import com.r3.demos.ubin2a.account.StartLSMFlow
import com.r3.demos.ubin2a.base.DeadlockModel
import com.r3.demos.ubin2a.base.ExceptionModel
import com.r3.demos.ubin2a.base.toSimpleString
import com.r3.demos.ubin2a.plan.NettingException
import net.corda.core.flows.FlowException
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import org.slf4j.Logger
import java.util.*
import javax.ws.rs.GET
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response


/**
 * This API is accessible from /api/ubin2a. The endpoint paths specified below are relative to it.
 */
@Path("netting")
class NettingApi(val services: CordaRPCOps) {
    companion object {
        private val logger: Logger = loggerFor<NettingApi>()
    }

    /** Post a request to start LSM. */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    fun startLsm(): Response {
        val (status, message) = try {
            val flowHandle = services.startTrackedFlowDynamic(StartLSMFlow::class.java)
            flowHandle.progress.subscribe { logger.info("netting.startLsm: $it") }
            Response.Status.CREATED to null
        } catch (ex: Exception){
            if((ex is FlowException && ex.message!!.contains("Deadlock")) || ex is NettingException) {
                Response.Status.NOT_MODIFIED to
                        ExceptionModel(statusCode = Response.Status.NOT_MODIFIED.statusCode, msg = "Possible Deadlock. Netting Not Possible")
            }else{
                Response.Status.INTERNAL_SERVER_ERROR to
                        ExceptionModel(statusCode = Response.Status.INTERNAL_SERVER_ERROR.statusCode, msg = ex.message.toString())
            }
        }
        return Response.status(status).entity(message).build()
    }

    /** Get the current deadlock status. */
    @Path("status")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    fun checkDeadlock(): Response {
        val (status, message) = try {
            val flowHandle = services.startTrackedFlowDynamic(CheckDeadlockFlow::class.java)
            flowHandle.progress.subscribe { logger.info("netting.checkDeadlock: $it") }
            val (status, deadlock, timestamp) = flowHandle.returnValue.getOrThrow()
            val deadlockDateTime = Date(timestamp.toEpochMilli()).toSimpleString()
            val statusDateTime = Date(status.second.toEpochMilli()).toSimpleString()
            val message = if(deadlock){
                "Possible Deadlock since $deadlockDateTime"
            } else {
                "No Deadlock since $deadlockDateTime"
            }
            Response.Status.OK to DeadlockModel(
                    statusCode = Response.Status.OK.statusCode,
                    status = status.first,
                    msg = message,
                    inDeadlock = deadlock,
                    notifiedDate = deadlockDateTime,
                    updatedDate = statusDateTime)
        } catch (ex: Exception){
            Response.Status.INTERNAL_SERVER_ERROR to
                    ExceptionModel(statusCode = Response.Status.INTERNAL_SERVER_ERROR.statusCode, msg = ex.message.toString())
        }
        return Response.status(status).entity(message).build()
    }

}



