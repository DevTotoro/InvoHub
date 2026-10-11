package com.invohub.backend.infrastructure.adapter.outbound.audit

import com.invohub.backend.domain.audit.AuditEvent
import com.invohub.backend.domain.audit.AuditEventRecord
import com.invohub.backend.domain.audit.AuditLog
import com.invohub.backend.domain.auth.AuthenticatedUser
import com.invohub.backend.infrastructure.adapter.inbound.web.filter.RequestIdFilter
import jakarta.persistence.EntityManager
import org.slf4j.MDC
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * Stores events as [AuditEventRecord]s, filling in what the caller doesn't know: the signed-in user and the
 * request ID. Uses persist, which only ever inserts, never merge.
 */
@Component
class JpaAuditLog(
    private val entityManager: EntityManager,
) : AuditLog {
    @Transactional(propagation = Propagation.MANDATORY)
    override fun record(event: AuditEvent) = persist(event)

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun recordStandalone(event: AuditEvent) = persist(event)

    private fun persist(event: AuditEvent) {
        entityManager.persist(
            AuditEventRecord(
                action = event.action.code,
                actorUserId = event.actorUserId ?: signedInUserId(),
                companyId = event.companyId,
                targetType = event.target?.type,
                targetId = event.target?.id,
                details = event.details,
                requestId = MDC.get(RequestIdFilter.MDC_KEY),
            ),
        )
    }

    private fun signedInUserId(): Long? =
        (SecurityContextHolder.getContext().authentication?.principal as? AuthenticatedUser)?.userId
}
