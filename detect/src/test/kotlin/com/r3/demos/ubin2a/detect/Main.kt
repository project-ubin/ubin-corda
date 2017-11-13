package com.r3.demos.ubin2a.detect

import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.concurrent.transpose
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.transactions.ValidatingNotaryService
import net.corda.nodeapi.internal.ServiceInfo

fun main(args: Array<String>) {
    // No permissions required as we are not invoking flows.
    val user = net.corda.nodeapi.User("user1", "test", permissions = setOf())
    net.corda.testing.driver.driver(isDebug = true) {
        startNode(providedName = CordaX500Name("Controller", "London", "GB"), advertisedServices = setOf(ServiceInfo(ValidatingNotaryService.type)))
        val (nodeA, nodeB, nodeC) = listOf(
                startNode(providedName = CordaX500Name("PartyA", "London", "GB"), rpcUsers = listOf(user)),
                startNode(providedName = CordaX500Name("PartyB", "London", "GB"), rpcUsers = listOf(user)),
                startNode(providedName = CordaX500Name("PartyC", "London", "GB"), rpcUsers = listOf(user))).transpose().getOrThrow()

        startWebserver(nodeA)
        startWebserver(nodeB)
        startWebserver(nodeC)

        waitForAllNodesToFinish()
    }
}
