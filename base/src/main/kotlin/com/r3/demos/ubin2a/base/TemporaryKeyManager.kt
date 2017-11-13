package com.r3.demos.ubin2a.base

import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken

@CordaService
class TemporaryKeyManager(val services: ServiceHub) : SingletonSerializeAsToken() {
    var key: AbstractParty

    init {
        key = refreshKey()
    }

    fun key(): AbstractParty = key
    fun refreshKey(): AbstractParty {
        key = AnonymousParty(services.keyManagementService.freshKey())
        return key
    }
}
