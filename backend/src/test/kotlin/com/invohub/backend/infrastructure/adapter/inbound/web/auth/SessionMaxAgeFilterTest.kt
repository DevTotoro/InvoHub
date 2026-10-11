package com.invohub.backend.infrastructure.adapter.inbound.web.auth

import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockHttpSession
import java.time.Clock
import java.time.Duration
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SessionMaxAgeFilterTest {
    private val maxAge = Duration.ofDays(7)

    @Test
    fun `keeps a session younger than the maximum age`() {
        val session = MockHttpSession()

        filterAfter(Duration.ofDays(6), session)

        assertFalse(session.isInvalid)
    }

    @Test
    fun `ends a session older than the maximum age, and the request carries on`() {
        val session = MockHttpSession()

        val chain = filterAfter(Duration.ofDays(8), session)

        assertTrue(session.isInvalid)
        assertNotNull(chain.request)
    }

    private fun filterAfter(
        elapsed: Duration,
        session: MockHttpSession,
    ): MockFilterChain {
        val request = MockHttpServletRequest().apply { setSession(session) }
        val chain = MockFilterChain()
        SessionMaxAgeFilter(maxAge, Clock.offset(Clock.systemUTC(), elapsed))
            .doFilter(request, MockHttpServletResponse(), chain)
        return chain
    }
}
