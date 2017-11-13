package com.r3.demos.ubin2a.redeem

import com.r3.demos.ubin2a.base.*
import com.sun.jersey.api.client.Client
import com.sun.jersey.api.client.ClientResponse
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.loggerFor
import java.util.*
import org.eclipse.jetty.http.HttpStatus
import javax.ws.rs.core.MediaType
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.*
import java.io.IOException


object ExternalRedeemService {

    @CordaService
    class Service(val services: ServiceHub) : SingletonSerializeAsToken() {

        private companion object {
            val logger = loggerFor<ExternalRedeemService.Service>()
        }

        fun approveRedeemInMEPS(value : TransactionModel): Boolean {
            try {
                val client = Client.create()
                val ApproveRedeemURI = getApproveRedeemURI("ApproveRedeemURI")
                logger.info("Approve Redeem URI from properties " + ApproveRedeemURI)
                val webResource = client.resource(ApproveRedeemURI)
                val mapper = ObjectMapper()
                val response = webResource.accept(MediaType.APPLICATION_JSON)
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .post(ClientResponse::class.java, mapper.writeValueAsString(value))
                logger.info("Response from MEPS " + response.status)
                if (response.status != HttpStatus.CREATED_201) {
                    throw RuntimeException("Failed : HTTP error code : "
                            + response.status)
                }
                return true
            } catch (ex: Exception) {
                logger.error(ex.message)
                return false
            }
        }

        // Try to read config properties to get the approve redeem URI
        fun getApproveRedeemURI(value : String) : String {

            val prop = Properties()
            var input: InputStream? = null

            try {
                input = FileInputStream("./config.properties")

                // load a properties file
                prop.load(input)
                val result = prop.getProperty(value)
                logger.info("prop loaded " + result )
                return result
            } catch (ex: IOException) { throw ex }
            finally {
                if (input != null) {
                    try {
                        input.close()
                    } catch (ex: IOException) { ex.printStackTrace() }
                } else {
                    logger.info("Input from FileInputStream " + input.toString())
                    throw IllegalArgumentException("config.properties not found or is null")
                }
            }
        }

    }
}



