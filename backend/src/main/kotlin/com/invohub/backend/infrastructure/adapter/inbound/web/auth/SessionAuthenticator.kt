package com.invohub.backend.infrastructure.adapter.inbound.web.auth

import com.invohub.backend.domain.auth.AuthenticatedUser
import com.invohub.backend.domain.auth.UserSessions
import com.invohub.backend.domain.user.User
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.stereotype.Component

/** Signs a user in by storing an authenticated security context in a new (Redis-backed) session. */
@Component
class SessionAuthenticator(
    private val securityContextRepository: SecurityContextRepository,
    private val userSessions: UserSessions,
) {
    private val strategy = SecurityContextHolder.getContextHolderStrategy()

    fun signIn(
        user: User,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        // Always start a fresh session, so one planted in the browser before sign-in can't be used afterwards.
        // Ending the old one rather than changing its ID also avoids Redis RENAME, which fails on a sharded
        // store (ElastiCache Serverless) when the two keys land on different shards.
        request.getSession(false)?.invalidate()

        val principal = AuthenticatedUser.of(user)
        val authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, emptyList())
        val context = strategy.createEmptyContext().apply { this.authentication = authentication }
        strategy.context = context
        securityContextRepository.saveContext(context, request, response)

        userSessions.register(user.id, request.getSession(false)!!.id)
    }
}
