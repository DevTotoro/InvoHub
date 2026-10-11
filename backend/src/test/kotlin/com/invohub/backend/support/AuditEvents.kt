package com.invohub.backend.support

import org.springframework.jdbc.core.JdbcTemplate

/**
 * Reads audit events in tests. Events can't be deleted, so they pile up across tests: always look them up
 * by something unique to the test, such as the target's ID.
 */
class AuditEvents(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun actionsFor(
        targetType: String,
        targetId: Long,
    ): List<String> =
        jdbcTemplate
            .queryForList(
                "SELECT action FROM audit.events WHERE target_type = ? AND target_id = ? ORDER BY occurred_at, id",
                String::class.java,
                targetType,
                targetId,
            ).filterNotNull()

    fun forUser(userId: Long) = actionsFor("user", userId)
}
