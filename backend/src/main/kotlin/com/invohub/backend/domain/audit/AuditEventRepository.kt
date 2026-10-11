package com.invohub.backend.domain.audit

import org.springframework.data.repository.Repository

/** Read-only on purpose: it has no save or delete. Events are only ever added, through [AuditLog]. */
interface AuditEventRepository : Repository<AuditEventRecord, Long> {
    fun findByTargetTypeAndTargetIdOrderByOccurredAtAscIdAsc(
        targetType: String,
        targetId: Long,
    ): List<AuditEventRecord>
}
