package com.invohub.backend.infrastructure.adapter.inbound.web.auth

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Ends a session once it is older than [maxAge], however active it has been; the inactivity timeout alone
 * would let a session that is used every day live forever. The request then carries on signed out.
 *
 * It only reads the creation time of the session already loaded for this request, so it costs no I/O.
 * Not a @Component: Spring Boot would also register it as a plain servlet filter, outside the security chain.
 */
class SessionMaxAgeFilter(
    private val maxAge: Duration,
    private val clock: Clock = Clock.systemUTC(),
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val session = request.getSession(false)
        if (session != null && Instant.ofEpochMilli(session.creationTime).plus(maxAge) < clock.instant()) {
            session.invalidate()
        }
        chain.doFilter(request, response)
    }
}
