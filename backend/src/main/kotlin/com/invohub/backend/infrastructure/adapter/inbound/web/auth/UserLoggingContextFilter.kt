package com.invohub.backend.infrastructure.adapter.inbound.web.auth

import com.invohub.backend.domain.auth.AuthenticatedUser
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Adds the signed-in user's public ID to the logging context (MDC) as `userId`, next to `requestId`, so every
 * log line of the request says whose it was. Never the email: the public ID is enough to look the user up.
 *
 * Must run inside the security chain, once the security context is loaded: before that, nobody is signed in.
 * Not a @Component: Spring Boot would also register it as a plain servlet filter, outside the security chain.
 */
class UserLoggingContextFilter : OncePerRequestFilter() {
    companion object {
        const val MDC_KEY = "userId"
    }

    private val strategy = SecurityContextHolder.getContextHolderStrategy()

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val user = strategy.context.authentication?.principal as? AuthenticatedUser
        if (user == null) {
            chain.doFilter(request, response)
            return
        }

        MDC.put(MDC_KEY, user.publicId)
        try {
            chain.doFilter(request, response)
        } finally {
            MDC.remove(MDC_KEY)
        }
    }
}
