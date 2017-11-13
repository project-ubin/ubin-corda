package com.r3.demos.ubin2a.obligation

import com.r3.demos.ubin2a.base.ObligationModel
import com.r3.demos.ubin2a.base.to2Decimals
import com.r3.demos.ubin2a.base.toPublicKey
import com.r3.demos.ubin2a.base.toSimpleString
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.loggerFor
import java.sql.PreparedStatement
import java.time.Instant
import java.util.*
import kotlin.collections.ArrayList

@CordaService
class PersistentObligationQueue(val services: ServiceHub) : SingletonSerializeAsToken() {

    init {
        initiateObligationVault()
    }

    private companion object {
        val logger = loggerFor<PersistentObligationQueue>()
    }

    fun initiateObligationVault() {
        val createTableQuery = """
                CREATE TABLE IF NOT EXISTS OBLIGATION_QUEUE(
                    LINEARID VARCHAR(255),
                    ISSUED_DATE VARCHAR(255),
                    UPDATED_DATE VARCHAR(255),
                    STATUS INT,
                    OBLIGATION_PRIORITY INT
                )"""
        val session = services.jdbcSession()
        val prepStatement = session.prepareStatement(createTableQuery)
        try {
            logger.info("Creating table OBLIGATION_QUEUE")
            prepStatement.executeUpdate()
        } catch (e: Exception) {
            logger.error("Obligation table creation failed: ${e.message.toString()}")
        } finally {
            prepStatement.close()
        }
    }

        fun addObligationToQueue(state: Obligation.State, priority: Int, status: Int): Boolean {
            val nativeQuery = """
                INSERT INTO
                    OBLIGATION_QUEUE
                VALUES(?, ?, ?, ?, ?)
            """
            logger.debug("Adding obligation ${state.linearId} to queue: $nativeQuery")

            val session = services.jdbcSession()
            val prepStatement: PreparedStatement = session.prepareStatement(nativeQuery)

            try {
                val date = Date(state.issueDate.toEpochMilli())
                val linearId = state.linearId.toString()
                prepStatement.setString(1, linearId)
                prepStatement.setString(2, date.toSimpleString())
                prepStatement.setString(3, Date(Instant.now().toEpochMilli()).toSimpleString())
                prepStatement.setInt(4, status)
                prepStatement.setInt(5, priority)

                prepStatement.executeUpdate()

                logger.info("Obligation $linearId added to queue")
            } catch (e: Exception) {
                logger.error("Exception during adding obligation to queue: ${e.message.toString()}")
                return false
            } finally {
                prepStatement.close()
            }

            return true
        }

    fun retrieveOutgoingObligations(): List<ObligationModel> {

        val obligations = ArrayList<ObligationModel>()

        val obligationRetrievalQuery = """
                   SELECT
                        STATE.TRANSACTION_ID,
                        STATE.AMOUNT,
                        STATE.BORROWER,
                        STATE.LENDER,
                        STATE.LINEARID,
                        STATE.CURRENCY,
                        QUEUE.ISSUED_DATE,
                        QUEUE.UPDATED_DATE,
                        QUEUE.STATUS,
                        QUEUE.OBLIGATION_PRIORITY
                    FROM
                        OBLIGATIONS  as state
                    JOIN
                        OBLIGATION_QUEUE as queue
                    ON
                        state.LINEARID = queue.LINEARID
                    ORDER BY queue.OBLIGATION_PRIORITY DESC, queue.ISSUED_DATE ASC
                """

        logger.debug("Retrieve outgoing obligations from queue: $obligationRetrievalQuery")

        val session = services.jdbcSession()
        val prepStatement: PreparedStatement = session.prepareStatement(obligationRetrievalQuery)

        try {

            val rs = prepStatement.executeQuery()
            while (rs.next()) {
                val receiver = rs.getBytes(("LENDER")).toPublicKey()
                val sender = rs.getBytes(("BORROWER")).toPublicKey()
                val obligation = ObligationModel(
                        transId = rs.getString("TRANSACTION_ID"),
                        linearId = rs.getString("LINEARID"),
                        requestedDate = rs.getString("ISSUED_DATE"),
                        updatedDate = rs.getString("UPDATED_DATE"),
                        receiver = receiver,
                        sender = sender,
                        transactionAmount = rs.getString("AMOUNT").toLong().to2Decimals(),
                        priority = rs.getInt("OBLIGATION_PRIORITY"),
                        currency = rs.getString("CURRENCY"),
                        status = rs.getInt("STATUS")
                )
                obligations.add(obligation)
            }

            logger.info("Total outgoing obligations: ${obligations.size}")

        } catch(e: Exception) {
            logger.error("Exception during retrieve outgoing obligation to queue: ${e.message.toString()}")

        } finally {
            prepStatement.close()
        }

        return obligations
    }

    fun getOutgoingObligationFromStatus(status: Int): List<ObligationModel> {

        val obligations = ArrayList<ObligationModel>()

        val obligationRetrievalQuery = """
                   SELECT
                        STATE.TRANSACTION_ID,
                        STATE.AMOUNT,
                        STATE.BORROWER,
                        STATE.LENDER,
                        STATE.LINEARID,
                        STATE.CURRENCY,
                        QUEUE.ISSUED_DATE,
                        QUEUE.UPDATED_DATE,
                        QUEUE.STATUS,
                        QUEUE.OBLIGATION_PRIORITY
                    FROM
                        OBLIGATIONS  as state
                    JOIN
                        OBLIGATION_QUEUE as queue
                    ON
                        state.LINEARID = queue.LINEARID
                    WHERE
                        QUEUE.STATUS = ?
                    ORDER BY queue.OBLIGATION_PRIORITY DESC, queue.ISSUED_DATE ASC
                """


        logger.debug("Retrieve outgoing obligations from queue with status $status: $obligationRetrievalQuery")

        val session = services.jdbcSession()
        val prepStatement: PreparedStatement = session.prepareStatement(obligationRetrievalQuery)

        try {

            prepStatement.setInt(1, status)

            val rs = prepStatement.executeQuery()
            while (rs.next()) {
                val receiver = rs.getBytes(("LENDER")).toPublicKey()
                val sender = rs.getBytes(("BORROWER")).toPublicKey()
                val obligation = ObligationModel(
                        transId = rs.getString("TRANSACTION_ID"),
                        linearId = rs.getString("LINEARID"),
                        requestedDate = rs.getString("ISSUED_DATE"),
                        updatedDate = rs.getString("UPDATED_DATE"),
                        receiver = receiver,
                        sender = sender,
                        transactionAmount = rs.getString("AMOUNT").toLong().to2Decimals(),
                        priority = rs.getInt("OBLIGATION_PRIORITY"),
                        currency = rs.getString("CURRENCY"),
                        status = rs.getInt("STATUS")
                )
                obligations.add(obligation)
            }

            logger.info("Total outgoing obligations: ${obligations.size}")

        } catch(e: Exception) {
            logger.error("Exception during getting database: $e.message.toString()")

        } finally {
            prepStatement.close()
        }

        return obligations
    }

    // TODO: Why does this return a list?
    fun getOutgoingObligationFromLinearId(linearId: UniqueIdentifier): List<ObligationModel> {

        val obligations = ArrayList<ObligationModel>()
        val obligationRetrievalQuery = """
                   SELECT
                        STATE.TRANSACTION_ID,
                        STATE.AMOUNT,
                        STATE.BORROWER,
                        STATE.LENDER,
                        STATE.LINEARID,
                        STATE.CURRENCY,
                        QUEUE.ISSUED_DATE,
                        QUEUE.UPDATED_DATE,
                        QUEUE.STATUS,
                        QUEUE.OBLIGATION_PRIORITY
                    FROM
                        OBLIGATIONS  as state
                    JOIN
                        OBLIGATION_QUEUE as queue
                    ON
                        state.LINEARID = queue.LINEARID
                    WHERE
                        QUEUE.LINEARID = ?
                    ORDER BY queue.OBLIGATION_PRIORITY DESC, queue.ISSUED_DATE ASC
                """

        logger.debug("Get obligations from queue with linear id $linearId: $obligationRetrievalQuery")

        val session = services.jdbcSession()
        val prepStatement: PreparedStatement = session.prepareStatement(obligationRetrievalQuery)

        try {

            prepStatement.setString(1, linearId.id.toString())

            val rs = prepStatement.executeQuery()
            while (rs.next()) {
                val receiver = rs.getBytes(("LENDER")).toPublicKey()
                val sender = rs.getBytes(("BORROWER")).toPublicKey()

                val obligation = ObligationModel(
                        transId = rs.getString("TRANSACTION_ID"),
                        linearId = rs.getString("LINEARID"),
                        requestedDate = rs.getString("ISSUED_DATE"),
                        updatedDate = rs.getString("UPDATED_DATE"),
                        receiver = receiver,
                        sender = sender,
                        transactionAmount = rs.getString("AMOUNT").toLong().to2Decimals(),
                        priority = rs.getInt("OBLIGATION_PRIORITY"),
                        currency = rs.getString("CURRENCY"),
                        status = rs.getInt("STATUS")
                )
                obligations.add(obligation)
            }
            logger.info("Total outgoing obligations: ${obligations.size}")

        } catch(e: Exception) {
            logger.error("Exception during getting database: $e.message.toString()")

        } finally {
            prepStatement.close()
        }

        return obligations
    }


    fun updateObligationStatus(linearId: UniqueIdentifier, status: Int): Boolean {
        val updateObligationQuery = """
                UPDATE
                    OBLIGATION_QUEUE
                SET
                    STATUS = ?,
                    UPDATED_DATE = ?
                WHERE
                    LINEARID = ?
            """
        logger.debug("Update obligations $linearId to status $status: $updateObligationQuery")


        val session = services.jdbcSession()
        val prepStatement: PreparedStatement = session.prepareStatement(updateObligationQuery)

        try {
            prepStatement.setInt(1, status)
            prepStatement.setString(2, Date(Instant.now().toEpochMilli()).toSimpleString())
            prepStatement.setString(3, linearId.id.toString())


            prepStatement.executeUpdate()

            logger.info("Obligations $linearId is updated to status $status")
        } catch (e: Exception) {
            logger.error("Exception during update obligation status ${e.message.toString()}")

            return false
        } finally {
            prepStatement.close()
        }

        return true
    }


    fun updateObligationPriority(linearId: UniqueIdentifier, priority: Int): Boolean {
        val nativeQuery = """
                UPDATE
                    OBLIGATION_QUEUE
                SET
                    OBLIGATION_PRIORITY = ?,
                    UPDATED_DATE = ?
                WHERE
                    LINEARID = ?
            """
        logger.info("Update obligations $linearId to priority $priority")

        val session = services.jdbcSession()
        val prepStatement: PreparedStatement = session.prepareStatement(nativeQuery)

        try {
            prepStatement.setInt(1, priority)
            prepStatement.setString(2, Date(Instant.now().toEpochMilli()).toSimpleString())
            prepStatement.setString(3, linearId.id.toString())
            prepStatement.executeUpdate()
            logger.info("Obligations $linearId is updated to priority $priority")
            
        } catch (e: Exception) {
            logger.error("Exception during update obligation priority ${e.message.toString()}")
            return false
        } finally {
            prepStatement.close()
        }

        return true
    }
}