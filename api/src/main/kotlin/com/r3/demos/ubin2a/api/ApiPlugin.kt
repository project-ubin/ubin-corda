package com.r3.demos.ubin2a.api

import com.r3.demos.ubin2a.api.controller.*
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.serialization.SerializationWhitelist
import net.corda.webserver.services.WebServerPluginRegistry
import java.util.function.Function

class ApiPlugin : WebServerPluginRegistry, SerializationWhitelist {
    override val webApis: List<java.util.function.Function<CordaRPCOps, out Any>> get() =
        listOf(Function(::AccountApi),
                Function(::FundApi),
                Function(::ObligationApi),
                Function(::PledgeApi),
                Function(::RedeemApi),
                Function(::NettingApi))
    override val whitelist: List<Class<*>> get() = listOf(
            java.security.cert.CertPathValidatorException.BasicReason::class.java
    )
}

