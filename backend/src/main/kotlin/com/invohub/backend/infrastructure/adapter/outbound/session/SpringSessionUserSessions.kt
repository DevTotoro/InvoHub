package com.invohub.backend.infrastructure.adapter.outbound.session

import com.invohub.backend.domain.auth.UserSessions
import org.springframework.session.FindByIndexNameSessionRepository
import org.springframework.stereotype.Component

/**
 * Finds sessions through Spring Session's principal-name index, which needs the indexed Redis repository
 * (spring.session.data.redis.repository-type: indexed). The principal name is the user ID.
 */
@Component
class SpringSessionUserSessions(
    private val sessions: FindByIndexNameSessionRepository<*>,
) : UserSessions {
    override fun endAll(userId: Long) {
        sessions.findByPrincipalName(userId.toString()).keys.forEach(sessions::deleteById)
    }
}
