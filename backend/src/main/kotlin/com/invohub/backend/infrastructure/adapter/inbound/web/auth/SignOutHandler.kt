package com.invohub.backend.infrastructure.adapter.inbound.web.auth

import com.invohub.backend.domain.audit.AuditAction
import com.invohub.backend.domain.audit.AuditEvent
import com.invohub.backend.domain.audit.AuditLog
import com.invohub.backend.domain.audit.AuditTarget
import com.invohub.backend.domain.auth.AuthenticatedUser
import com.invohub.backend.domain.auth.UserSessions
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.logout.LogoutHandler

/**
 * Records user.signed_out and drops the session from the user's session list. Spring Security's logout
 * filter handles the sign-out itself, after this: it invalidates the session, which deletes it from Redis.
 */
class SignOutHandler(
    private val auditLog: AuditLog,
    private val userSessions: UserSessions,
) : LogoutHandler {
    override fun logout(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication?,
    ) {
        val user = authentication?.principal as? AuthenticatedUser ?: return
        request.getSession(false)?.let { userSessions.unregister(user.userId, it.id) }
        auditLog.recordStandalone(
            AuditEvent(AuditAction.USER_SIGNED_OUT, actorUserId = user.userId, target = AuditTarget.user(user.userId)),
        )
    }
}
