package com.invohub.backend.infrastructure.adapter.inbound.web.auth

import com.invohub.backend.domain.auth.AuthenticatedUser
import com.invohub.backend.domain.user.User
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.stereotype.Component

/** Signs a user in by storing an authenticated security context in their (Redis-backed) session. */
@Component
class SessionAuthenticator(
    private val securityContextRepository: SecurityContextRepository,
) {
    private val strategy = SecurityContextHolder.getContextHolderStrategy()

    fun signIn(
        user: User,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        // Rotate the session ID, so a session planted in the browser before sign-in can't be used afterwards
        request.getSession(false)?.let { request.changeSessionId() }

        val principal = AuthenticatedUser(user.id, user.email)
        val authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, emptyList())
        val context = strategy.createEmptyContext().apply { this.authentication = authentication }
        strategy.context = context
        securityContextRepository.saveContext(context, request, response)
    }
}
