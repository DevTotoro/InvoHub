package com.invohub.backend.infrastructure.adapter.inbound.web.auth

import com.invohub.backend.domain.audit.AuditAction
import com.invohub.backend.domain.audit.AuditEvent
import com.invohub.backend.domain.audit.AuditLog
import com.invohub.backend.domain.audit.AuditTarget
import com.invohub.backend.domain.auth.AuthenticatedUser
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.logout.LogoutHandler

/** Records user.signed_out; Spring Security's logout filter handles the sign-out itself. */
class AuditingLogoutHandler(
    private val auditLog: AuditLog,
) : LogoutHandler {
    override fun logout(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication?,
    ) {
        val user = authentication?.principal as? AuthenticatedUser ?: return
        auditLog.recordStandalone(
            AuditEvent(AuditAction.USER_SIGNED_OUT, actorUserId = user.userId, target = AuditTarget.user(user.userId)),
        )
    }
}
