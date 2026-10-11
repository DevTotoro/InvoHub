package com.invohub.backend.infrastructure.adapter.inbound.web.auth

import com.invohub.backend.domain.auth.AuthenticatedUser
import com.invohub.backend.domain.user.UserService
import com.invohub.backend.infrastructure.adapter.inbound.web.error.ProblemDetailWriter
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Re-checks the signed-in user on every request, so locking or deleting an account takes effect immediately
 * rather than at the next sign-in: the session is ended and the request refused. It reads the database
 * rather than anything cached in the session, so a lock applies however it was set, including by hand.
 *
 * Not a @Component: Spring Boot would also register it as a plain servlet filter, outside the security chain.
 */
class ActiveAccountFilter(
    private val userService: UserService,
    private val problemDetailWriter: ProblemDetailWriter,
) : OncePerRequestFilter() {
    private val strategy = SecurityContextHolder.getContextHolderStrategy()

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val principal = strategy.context.authentication?.principal as? AuthenticatedUser
        if (principal == null) {
            chain.doFilter(request, response)
            return
        }

        val user = userService.findById(principal.userId)
        when {
            user == null -> signOut(request, response, HttpStatus.UNAUTHORIZED, "Sign in to continue.")
            user.locked -> signOut(request, response, HttpStatus.FORBIDDEN, "This account is locked.", "account_locked")
            else -> chain.doFilter(request, response)
        }
    }

    private fun signOut(
        request: HttpServletRequest,
        response: HttpServletResponse,
        status: HttpStatus,
        detail: String,
        code: String? = null,
    ) {
        request.getSession(false)?.invalidate()
        strategy.clearContext()
        problemDetailWriter.write(response, status, detail, code)
    }
}
