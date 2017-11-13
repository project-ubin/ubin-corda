package com.r3.demos.ubin2a.account

import co.paralleluniverse.fibers.Suspendable
import com.r3.demos.ubin2a.detect.DetectFlow
import com.r3.demos.ubin2a.execute.ExecuteFlow
import com.r3.demos.ubin2a.plan.NettingException
import com.r3.demos.ubin2a.plan.PlanFlow
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.unwrap
import java.time.Instant
import java.util.*

@CordaSerializable
enum class STATUS { STILL, DETECT, PLAN, DEADLOCK, EXECUTE, COMPLETE }

/**
 * In memory data store to save the status of each stage
 * <Current Stage, Time Updated>
 */
object LSMDataStore {
    var status: Pair<String, Instant> = Pair(STATUS.STILL.name.toLowerCase(), Instant.now())
    fun set(value: Pair<String, Instant>){
        status = Pair(value.first.toLowerCase(),value.second)
    }
    fun purge() {
        status = Pair(STATUS.STILL.name.toLowerCase(), Instant.now())
    }
}

/**
 * Start the LSM flow to go through detect, plan and execute stages
 */
@InitiatingFlow
@StartableByRPC
class StartLSMFlow : FlowLogic<Unit>() {
    @Suspendable
    fun sendDeadlockUpdate(inDeadlock: Boolean) {
        logger.info("StartLSMFlow.sendDeadlockUpdate: Sending deadlock update request")
        val everyone = serviceHub.networkMapCache.allNodes.filter { nodeInfo ->
            nodeInfo.legalIdentities.first().name != CordaX500Name("Mock Company 0", "London", "GB") &&
                    nodeInfo.legalIdentities.first().name != CordaX500Name("Mock Company 1", "London", "GB")
            // TODO: Uncomment this when deploying to Azure and vice versa
//            nodeInfo.isNotary(serviceHub).not() && nodeInfo.isRegulator().not() && nodeInfo.isNetworkMap().not()
        }
        everyone.forEach { println(it.legalIdentities.first()) }
        val timeStamp = Instant.now()
        serviceHub.cordaService(DeadlockService::class.java).updateStatus(Pair(inDeadlock, timeStamp))
        everyone.forEach {
            subFlow(DeadlockNotificationFlow.Initiator(
                    party = it.legalIdentities.first(),
                    inDeadlock = inDeadlock,
                    time = timeStamp)
            )
        }
    }

    @Suspendable
    override fun call() {
        logger.info("StartLSMFlow: Starting LSM run")
        LSMDataStore.purge()
        logger.info("StartLSMFlow: Detect")
        LSMDataStore.set(Pair(STATUS.DETECT.name, Instant.now()))

        val (obligations,limits) = subFlow(DetectFlow(Currency.getInstance("SGD")))

        logger.info("StartLSMFlow: Plan")
        LSMDataStore.set(Pair(STATUS.PLAN.name, Instant.now()))

        val (toPay, toSettle) = try {
            subFlow(PlanFlow(obligations, limits, Currency.getInstance("SGD")))
        } catch (e: NettingException) {
            sendDeadlockUpdate(inDeadlock = true)
            LSMDataStore.set(Pair(STATUS.DEADLOCK.name, Instant.now()))
            throw FlowException("Deadlock!")
        }
        logger.info("StartLSMFlow: Execute")
        LSMDataStore.set(Pair(STATUS.EXECUTE.name, Instant.now()))

        subFlow(ExecuteFlow(obligations, toSettle, toPay))
        sendDeadlockUpdate(inDeadlock = false)
        LSMDataStore.set(Pair(STATUS.COMPLETE.name, Instant.now()))
    }
}

/**
 * Deadlock notification flow to inform participant nodes in LSM to update the deadlock status
 */
object DeadlockNotificationFlow {
    @InitiatingFlow
    class Initiator(val party: Party, val inDeadlock: Boolean, val time: Instant) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            logger.info("DeadlockNotificationFlow.Initiator: Sending deadlock and timestamp")
            val session = initiateFlow(party)
            session.send(Pair(inDeadlock, time))
        }
    }

    @InitiatedBy(DeadlockNotificationFlow.Initiator::class)
    class Responder(val otherFlow: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val inDeadlock = otherFlow.receive<Pair<Boolean, Instant>>().unwrap { it }
            logger.info("DeadlockNotificationFlow.Responder: Receiving deadlock and timestamp")
            serviceHub.cordaService(DeadlockService::class.java).updateStatus(inDeadlock)
        }
    }
}

/**
 * Flow to return the current stage status, deadlock status, and timestamp since last deadlock
 * <<Current Stage, Time Updated>, In Deadlock Status, Deadlock Time>
 */
@InitiatingFlow
@StartableByRPC
class CheckDeadlockFlow : FlowLogic<Triple<Pair<String, Instant>, Boolean, Instant>>(){
    @Suspendable
    override fun call() : Triple<Pair<String, Instant>,Boolean, Instant> {
        logger.info("CheckDeadlockFlow: Querying deadlock statuses")
        val (deadlock, time) = serviceHub.cordaService(DeadlockService::class.java).getStatus()
        val status = LSMDataStore.status
        return Triple(status, deadlock, time)
    }
}

@CordaService
class DeadlockService(val services: ServiceHub) : SingletonSerializeAsToken() {
    private var status: Pair<Boolean, Instant> = Pair(false, Instant.now())
    fun updateStatus(newStatus: Pair<Boolean, Instant>) {
        status = newStatus
    }

    fun getStatus() = status
}