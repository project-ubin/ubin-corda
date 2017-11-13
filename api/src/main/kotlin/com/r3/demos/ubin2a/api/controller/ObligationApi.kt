package com.r3.demos.ubin2a.api.controller

import com.r3.demos.ubin2a.base.*
import com.r3.demos.ubin2a.obligation.*
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import org.slf4j.Logger
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import kotlin.collections.ArrayList

@Path("queue")
class ObligationApi(val services: CordaRPCOps) {
    companion object {
        private val logger: Logger = loggerFor<ObligationApi>()
    }

    /** Get current obligations states in vault. */
    @GET
    @Path("obligations")
    @Produces(MediaType.APPLICATION_JSON)
    fun getObligations(): Response {
        logger.info("Running ObligationApi.getObligations")
        val (status, message) = try {
            val vaultStates = services.vaultQueryBy<Obligation.State>()
            Response.Status.OK to vaultStates
        } catch (ex: Exception){
            logger.error("ObligationApi.getObligations: $ex")
            Response.Status.INTERNAL_SERVER_ERROR to ExceptionModel(statusCode = Response.Status.INTERNAL_SERVER_ERROR.statusCode, msg = ex.message.toString())
        }
        return Response.status(status).entity(message).build()
    }

    /** Returns all outgoing and incoming obligations in queue. */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    fun getAllObligations(): Response {
        logger.info("Running ObligationApi.getAllObligations")
        val result = ArrayList<ObligationModel>()
        val (status, message) = try {
            val flowHandle = services.startTrackedFlowDynamic(GetQueue.AllUnconsumed::class.java)
            flowHandle.progress.subscribe { logger.info("ObligationApi.GetQueue.AllUnconsumed: $it") }
            result.addAll(flowHandle.use{ it.returnValue.getOrThrow() })
            Response.Status.OK to result.toTransactionModel(services)
        } catch (ex: Exception){
            logger.error("ObligationApi.getAllObligations: $ex")
            Response.Status.INTERNAL_SERVER_ERROR to ExceptionModel(statusCode = Response.Status.INTERNAL_SERVER_ERROR.statusCode, msg = ex.message.toString())
        }
        return Response.status(status).entity(message).build()
    }

    /** Get all outgoing obligations in queue. */
    @GET
    @Path("out")
    @Produces(MediaType.APPLICATION_JSON)
    fun getOutgoingObligations() :  Response {
        logger.info("Running ObligationApi.getOutgoingObligations")
        val result = mutableListOf<ObligationModel>()
        val (status, message) = try {
            val flowHandle = services.startTrackedFlowDynamic(GetQueue.OutgoingUnconsumed()::class.java)
            flowHandle.progress.subscribe { logger.info("ObligationApi.GetQueue.OutgoingUnconsumed: $it") }
            result.addAll(flowHandle.use{ it.returnValue.getOrThrow() })
            Response.Status.OK to result.toTransactionModel(services)
        }
        catch(ex: Exception){
            logger.error("ObligationApi.getOutgoingObligations: $ex")
            Response.Status.INTERNAL_SERVER_ERROR to ExceptionModel(statusCode = Response.Status.INTERNAL_SERVER_ERROR.statusCode, msg = ex.message.toString())
        }
        return Response.status(status).entity(message).build()
    }

    /** Get all incoming obligations in queue. */
    @GET
    @Path("in")
    @Produces(MediaType.APPLICATION_JSON)
    fun getIncomingObligations(): Response {
        logger.info("Running ObligationApi.getIncomingObligations")
        val result = mutableListOf<ObligationModel>()
        val (status, message) = try {
            val flowHandle = services.startTrackedFlowDynamic(GetQueue.Incoming::class.java)
            flowHandle.progress.subscribe { logger.info("ObligationApi.GetQueue.Incoming: $it") }
            result.addAll(flowHandle.use{ it.returnValue.getOrThrow() })
            Response.Status.OK to result.toTransactionModel(services)
        }
        catch (ex: Exception){
            logger.error("ObligationApi.getIncomingObligations: $ex")
            Response.Status.INTERNAL_SERVER_ERROR to ExceptionModel(statusCode = Response.Status.INTERNAL_SERVER_ERROR.statusCode, msg = ex.message.toString())
        }
        return Response.status(status).entity(message).build()
    }

    /** Request to settle the next oldest and highest priority obligations in queue. */
    @PUT
    @Path("settle")
    @Produces(MediaType.APPLICATION_JSON)
    fun settleNextObligation(): Response {
        logger.info("Running ObligationApi.settleNextObligation")
        val result = mutableListOf<ObligationModel>()
        val (status, message) = try {
            val flowHandle = services.startTrackedFlowDynamic(SettleNextObligations::class.java)
            flowHandle.progress.subscribe { logger.info("ObligationApi.SettleNextObligations: $it") }
            result.addAll(flowHandle.use{ it.returnValue.getOrThrow() })
            if(result.isEmpty()){
                Response.Status.NOT_MODIFIED to ExceptionModel(statusCode = Response.Status.NOT_MODIFIED.statusCode, msg = "No active obligations to settle")
            } else {
                Response.Status.OK to result.toTransactionModel(services)
            }
        } catch (ex: Exception){
            logger.error("ObligationApi.settleNextObligation:: $ex")
            Response.Status.INTERNAL_SERVER_ERROR to ExceptionModel(statusCode = Response.Status.INTERNAL_SERVER_ERROR.statusCode, msg = ex.message.toString())
        }
        return Response.status(status).entity(message).build()
    }


    /**
     * Request to cancel the obligations in queue based on the transId that issued the obligations
     * JSON(application/json):
     * [ {"transId" : "E04F48C96E428385B2AF989A2CAE26E8592428B44A0476C716236003AE0FC88E"} ]
     */
    @PUT
    @Path("cancel")
    @Produces(MediaType.APPLICATION_JSON)
    fun cancelObligation(value : List<TransactionModel>): Response {
        logger.info("Running ObligationApi.cancelObligation")
        val result = mutableListOf<Any>()
        var errorExist = false
        value.forEach { it ->
            try {
                val getObligationFlowHandle = services.startTrackedFlowDynamic(GetObligationFromTransId::class.java, it.transId)
                getObligationFlowHandle.progress.subscribe { logger.info("ObligationApi.GetObligationFromTransId: $it") }
                val obligation = getObligationFlowHandle.use { it.returnValue.getOrThrow() }

                val getObligationModelFlowHandle = services.startTrackedFlowDynamic(GetQueue.OutgoingById::class.java, obligation.linearId)
                getObligationModelFlowHandle.progress.subscribe { logger.info("ObligationApi.getObligationFromQueue: $it") }
                val queueItems = getObligationModelFlowHandle.use { it.returnValue.getOrThrow() }

                val cancelObligationFlowHandle = services.startTrackedFlowDynamic(CancelObligation.Initiator::class.java, obligation.linearId)
                cancelObligationFlowHandle.progress.subscribe { logger.info("ObligationApi.CancelObligationFlow: $it") }
                val tx = cancelObligationFlowHandle.use { it.returnValue.getOrThrow() }

                @Suppress("UNCHECKED_CAST")
                val priority = queueItems.single().priority
                result.add(TransactionModel(
                        transId = tx.tx.id.toString(),
                        linearId = obligation.linearId.toString(),
                        sender = obligation.borrower.owningKey.toParty(services).name.organisation,
                        receiver = obligation.lender.owningKey.toParty(services).name.organisation,
                        transactionAmount = (obligation.amount.quantity).to2Decimals(),
                        priority = priority,
                        currency = obligation.amount.token.currencyCode.toString(),
                        status = OBLIGATION_STATUS.CANCELLED.name.toLowerCase()))
            } catch (ex: Exception) {
                logger.error("ObligationApi.cancelObligation: $ex")
                errorExist = true
                result.add(ExceptionModel(
                        transId = it.transId,
                        statusCode = Response.Status.INTERNAL_SERVER_ERROR.statusCode,
                        msg = ex.message.toString()))
            }
        }
        return if (errorExist){
            Response.status(Response.Status.INTERNAL_SERVER_ERROR.statusCode).entity(result).build()
        } else {
            Response.status(Response.Status.CREATED.statusCode).entity(result).build()
        }
    }

    /**
     * Request to toggle the status of the obligations in queue based on the transId that issued the obligations
     * JSON(application/json):
     * [ {"transId" : "E04F48C96E428385B2AF989A2CAE26E8592428B44A0476C716236003AE0FC88E"} ]
     */
    @PUT
    @Path("status")
    @Produces(MediaType.APPLICATION_JSON)
    fun activateObligation(value : List<TransactionModel>): Response {
        logger.info("Running ObligationApi.activateObligation")
        val result = mutableListOf<Any>()
        var errorExist = false
        value.forEach { it ->
            try {
                val getObligationFlowHandle = services.startTrackedFlowDynamic(GetObligationFromTransId::class.java, it.transId)
                getObligationFlowHandle.progress.subscribe { logger.info("ObligationApi.GetObligationFromTransId: $it") }
                val obligation = getObligationFlowHandle.use { it.returnValue.getOrThrow() }

                val getObligationModelFlowHandle = services.startTrackedFlowDynamic(GetQueue.OutgoingById::class.java, obligation.linearId)
                getObligationModelFlowHandle.progress.subscribe { logger.info("ObligationApi.GetObligationFromTransId: $it") }
                val obligationModel = getObligationModelFlowHandle.use { it.returnValue.getOrThrow() }.single()

                val newStatus =
                        if(obligationModel.status == OBLIGATION_STATUS.ACTIVE.ordinal) {
                            OBLIGATION_STATUS.HOLD
                        } else { OBLIGATION_STATUS.ACTIVE }

                val updateStatusFlowHandle = services.startTrackedFlowDynamic(UpdateObligationStatus::class.java, obligation.linearId, newStatus)
                updateStatusFlowHandle.progress.subscribe { logger.info("ObligationApi.UpdateObligationStatus: $it") }
                val updated = updateStatusFlowHandle.use { it.returnValue.getOrThrow() }

                if (updated) {
                    result.add(TransactionModel(
                            transId = obligationModel.transId,
                            linearId = obligationModel.linearId,
                            sender = obligationModel.sender.toParty(services).name.organisation,
                            receiver = obligationModel.receiver.toParty(services).name.organisation,
                            transactionAmount = obligationModel.transactionAmount,
                            priority = obligationModel.priority,
                            currency = SGD.currencyCode,
                            status = newStatus.name.toLowerCase()))
                } else {
                    result.add(ExceptionModel(
                            transId = it.transId,
                            statusCode =  Response.Status.NOT_MODIFIED.statusCode,
                            msg = "Table is empty or transId not found"))
                }
            } catch (ex: Exception) {
                logger.error("ObligationApi.updateObligationStatus: $ex")
                errorExist = true
                result.add(ExceptionModel(
                        transId = it.transId,
                        statusCode = Response.Status.INTERNAL_SERVER_ERROR.statusCode,
                        msg = ex.message.toString()))
            }
        }
        return if (errorExist){
            Response.status(Response.Status.INTERNAL_SERVER_ERROR.statusCode).entity(result).build()
        } else {
            Response.status(Response.Status.CREATED.statusCode).entity(result).build()
        }
    }

    /**
     * Request to change the priority of the obligations in queue based on the transId that issued the obligations
     * JSON(application/json):
     * [ {"transId" : "E04F48C96E428385B2AF989A2CAE26E8592428B44A0476C716236003AE0FC88E", "priority": 1} ]
     */
    @PUT
    @Path("priority")
    @Produces(MediaType.APPLICATION_JSON)
    fun updatePriority(value: List<TransactionModel>): Response {
        logger.info("Running ObligationApi.updatePriority")
        val result = mutableListOf<Any>()
        var errorExist = false
        value.forEach { it ->
            try {
                val getObligationFlowHandle = services.startTrackedFlowDynamic(GetObligationFromTransId::class.java, it.transId)
                getObligationFlowHandle.progress.subscribe { logger.info("ObligationApi.updatePriority::GetObligationFromTransId: $it") }
                val state = getObligationFlowHandle.use { it.returnValue.getOrThrow() }

                val updatePriorityFlowHandle = services.startTrackedFlowDynamic(UpdateObligationPriority::class.java, state.linearId, OBLIGATION_PRIORITY.values()[it.priority!!])
                updatePriorityFlowHandle.progress.subscribe { logger.info("ObligationApi.updatePriority::UpdateObligationPriority: $it") }
                val updated = updatePriorityFlowHandle.use { it.returnValue.getOrThrow() }

                if (updated) {
                    val getObligationModelFlowHandle = services.startTrackedFlowDynamic(GetQueue.OutgoingById::class.java, state.linearId)
                    getObligationModelFlowHandle.progress.subscribe { logger.info("ObligationApi.updatePriority::GetQueue.OutgoingById: $it") }
                    val obligationModel = getObligationModelFlowHandle.use { it.returnValue.getOrThrow() }.single()
                    result.add(TransactionModel(
                            transId = obligationModel.transId,
                            linearId = obligationModel.linearId,
                            sender = obligationModel.sender.toParty(services).name.organisation,
                            receiver = obligationModel.receiver.toParty(services).name.organisation,
                            transactionAmount = obligationModel.transactionAmount,
                            priority = obligationModel.priority,
                            currency = SGD.currencyCode,
                            status = OBLIGATION_STATUS.values()[obligationModel.status].name.toLowerCase()))
                } else {
                    result.add(ExceptionModel(
                            transId = it.transId,
                            statusCode =  Response.Status.NOT_MODIFIED.statusCode,
                            msg = "Table is empty or transId not found"))
                }
            } catch (ex: Exception) {
                logger.error("ObligationApi.updatePriority: $ex")
                errorExist = true
                result.add(ExceptionModel(
                        transId = it.transId,
                        statusCode = Response.Status.INTERNAL_SERVER_ERROR.statusCode,
                        msg = ex.message.toString()))
            }
        }
        return if (errorExist){
            Response.status(Response.Status.INTERNAL_SERVER_ERROR.statusCode).entity(result).build()
        } else {
            Response.status(Response.Status.CREATED.statusCode).entity(result).build()
        }
    }

}
