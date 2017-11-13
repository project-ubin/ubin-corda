package com.r3.demos.ubin2a.execute

import net.corda.core.serialization.SerializationWhitelist
import net.corda.finance.contracts.asset.PartyAndAmount

class ExecutePlugin : SerializationWhitelist {
    override val whitelist: List<Class<*>> get() = listOf(
            java.util.Stack::class.java,
            java.util.LinkedHashMap::class.java,
            Triple::class.java,
            PartyAndAmount::class.java,
            emptyMap<Any, Any>().javaClass
    )
}
