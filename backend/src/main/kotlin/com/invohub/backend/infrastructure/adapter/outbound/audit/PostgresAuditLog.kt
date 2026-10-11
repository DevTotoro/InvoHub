package com.invohub.backend.infrastructure.adapter.outbound.audit

import com.invohub.backend.domain.audit.AuditEvent
import com.invohub.backend.domain.audit.AuditLog
import com.invohub.backend.domain.auth.AuthenticatedUser
import com.invohub.backend.infrastructure.adapter.inbound.web.filter.RequestIdFilter
import io.hypersistence.tsid.TSID
import org.slf4j.MDC
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper

/**
 * Writes to audit.events, which the app's database login may only insert into and read; a trigger refuses
 * changes from every login. The timestamp is set by the database.
 */
@Component
class PostgresAuditLog(
    private val jdbcClient: JdbcClient,
    private val jsonMapper: JsonMapper,
) : AuditLog {
    @Transactional(propagation = Propagation.MANDATORY)
    override fun record(event: AuditEvent) = insert(event)

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun recordStandalone(event: AuditEvent) = insert(event)

    private fun insert(event: AuditEvent) {
        jdbcClient
            .sql(
                """
                INSERT INTO audit.events (id, action, actor_user_id, company_id, target_type, target_id, details, request_id)
                VALUES (:id, :action, :actorUserId, :companyId, :targetType, :targetId, CAST(:details AS jsonb), :requestId)
                """.trimIndent(),
            ).param("id", TSID.fast().toLong())
            .param("action", event.action.code)
            .param("actorUserId", event.actorUserId ?: signedInUserId())
            .param("companyId", event.companyId)
            .param("targetType", event.target?.type)
            .param("targetId", event.target?.id)
            .param("details", jsonMapper.writeValueAsString(event.details))
            .param("requestId", MDC.get(RequestIdFilter.MDC_KEY))
            .update()
    }

    private fun signedInUserId(): Long? =
        (SecurityContextHolder.getContext().authentication?.principal as? AuthenticatedUser)?.userId
}
