package com.invohub.backend.support

import com.invohub.backend.domain.audit.AuditEventRepository

/**
 * Reads audit events in tests. Events can't be deleted, so they pile up across tests: always look them up
 * by something unique to the test, such as the target's ID.
 */
class AuditEvents(
    private val repository: AuditEventRepository,
) {
    fun forUser(userId: Long): List<String> =
        repository.findByTargetTypeAndTargetIdOrderByOccurredAtAscIdAsc("user", userId).map { it.action }
}
