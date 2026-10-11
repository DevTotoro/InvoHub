package com.invohub.backend.infrastructure.adapter.outbound.audit

import com.invohub.backend.domain.audit.AuditAction
import com.invohub.backend.domain.audit.AuditEvent
import com.invohub.backend.domain.audit.AuditEventRepository
import com.invohub.backend.domain.audit.AuditLog
import com.invohub.backend.domain.audit.AuditTarget
import com.invohub.backend.domain.auth.AuthenticatedUser
import com.invohub.backend.infrastructure.adapter.inbound.web.filter.RequestIdFilter
import com.invohub.backend.support.IntegrationTest
import io.hypersistence.tsid.TSID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.env.Environment
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@IntegrationTest
class JpaAuditLogTest {
    @Autowired
    private lateinit var auditLog: AuditLog

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var environment: Environment

    @Autowired
    private lateinit var repository: AuditEventRepository

    @AfterEach
    fun clearContext() {
        SecurityContextHolder.clearContext()
        MDC.clear()
    }

    @Test
    fun `refuses to record outside a transaction, so no change can go unrecorded`() {
        assertFailsWith<IllegalTransactionStateException> { auditLog.record(event(newTarget())) }
    }

    @Test
    fun `an event rolls back with the change it belongs to`() {
        val target = newTarget()

        transactionTemplate.executeWithoutResult { status ->
            auditLog.record(event(target))
            status.setRollbackOnly()
        }

        assertEquals(0, countFor(target))
    }

    @Test
    fun `a standalone event is kept even if the surrounding transaction rolls back`() {
        val target = newTarget()

        transactionTemplate.executeWithoutResult { status ->
            auditLog.recordStandalone(event(target))
            status.setRollbackOnly()
        }

        assertEquals(1, countFor(target))
    }

    @Test
    fun `fills in the signed-in user, the request ID and the details`() {
        val target = newTarget()
        val principal = AuthenticatedUser(userId = 7, email = "staff@example.com")
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken.authenticated(principal, null, emptyList())
        MDC.put(RequestIdFilter.MDC_KEY, "req-123")

        auditLog.recordStandalone(event(target).copy(details = mapOf("reason" to "chargeback")))

        val stored = repository.findByTargetTypeAndTargetIdOrderByOccurredAtAscIdAsc(target.type, target.id).single()
        assertEquals("user.suspended", stored.action)
        assertEquals(7L, stored.actorUserId)
        assertEquals("req-123", stored.requestId)
        assertEquals(mapOf("reason" to "chargeback"), stored.details)
        assertNotNull(stored.occurredAt)
    }

    @Test
    fun `the app's login can only add and read events`() {
        val target = newTarget()
        auditLog.recordStandalone(event(target))

        assertDenied { jdbcTemplate.update("UPDATE audit.events SET action = 'x' WHERE target_id = ?", target.id) }
        assertDenied { jdbcTemplate.update("DELETE FROM audit.events WHERE target_id = ?", target.id) }
        assertDenied { jdbcTemplate.execute("TRUNCATE audit.events") }
        assertDenied { jdbcTemplate.execute("ALTER TABLE audit.events DISABLE TRIGGER events_append_only") }
        assertEquals(1, countFor(target))
    }

    @Test
    fun `not even the database owner can change or delete events`() {
        val target = newTarget()
        auditLog.recordStandalone(event(target))
        val owner = JdbcTemplate(ownerDataSource())

        val update =
            assertFailsWith<DataAccessException> {
                owner.update("UPDATE audit.events SET action = 'x' WHERE target_id = ?", target.id)
            }
        assertContains(update.mostSpecificCause.message!!, "append-only")
        assertFailsWith<DataAccessException> { owner.update("DELETE FROM audit.events WHERE target_id = ?", target.id) }
        assertFailsWith<DataAccessException> { owner.execute("TRUNCATE audit.events") }
        assertEquals(1, countFor(target))
    }

    private fun event(target: AuditTarget) = AuditEvent(AuditAction.USER_SUSPENDED, target = target)

    private fun newTarget() = AuditTarget.user(TSID.fast().toLong())

    private fun countFor(target: AuditTarget) =
        jdbcTemplate.queryForObject("SELECT count(*) FROM audit.events WHERE target_id = ?", Int::class.java, target.id)

    private fun assertDenied(statement: () -> Unit) {
        val e = assertFailsWith<DataAccessException> { statement() }
        assertTrue(
            e.mostSpecificCause.message!!.contains("permission denied") ||
                e.mostSpecificCause.message!!.contains("must be owner"),
            e.mostSpecificCause.message,
        )
    }

    private fun ownerDataSource() =
        DriverManagerDataSource(
            environment.getRequiredProperty("spring.datasource.url"),
            environment.getRequiredProperty("spring.flyway.user"),
            environment.getRequiredProperty("spring.flyway.password"),
        )
}
