package com.invohub.backend.support

import com.invohub.backend.domain.audit.AuditEventRepository

/**
 * Reads audit events in tests. Events can't be deleted, so they pile up across tests: always look them up
 * by something unique to the test, such as the target's ID.
 */
class AuditEvents(
    private val repository: AuditEventRepository,
) {
    fun forUser(userId: Long) = actionsFor("user", userId)

    fun forCompany(companyId: Long) = actionsFor("company", companyId)

    private fun actionsFor(
        targetType: String,
        targetId: Long,
    ): List<String> =
        repository.findByTargetTypeAndTargetIdOrderByOccurredAtAscIdAsc(targetType, targetId).map {
            it.action
        }
}
