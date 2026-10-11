package com.invohub.backend.infrastructure.adapter.inbound.web.auth

import com.invohub.backend.domain.user.UserRepository
import com.invohub.backend.support.CapturingEmailPort
import com.invohub.backend.support.IntegrationTest
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.ResultHandler
import org.springframework.test.web.servlet.ResultMatcher
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import kotlin.test.assertNotEquals

/**
 * Session behaviour through MockMvc. The CSRF cookie flow and cookie attributes are tested against a real
 * server in [SessionCookieTest]: `csrf()` permanently swaps the CSRF token repository of the shared filter
 * for a session-based test one, and MockMvc does not apply server.servlet.session.cookie.*.
 */
@IntegrationTest
@AutoConfigureMockMvc
class SessionTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var emailPort: CapturingEmailPort

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    @Autowired
    private lateinit var userRepository: UserRepository

    @BeforeEach
    fun reset() {
        emailPort.clear()
        userRepository.deleteAll()
        redisTemplate.connectionFactory!!.connection.use { it.serverCommands().flushAll() }
    }

    @Test
    fun `the session identifies the signed-in user`() {
        val session = sessionCookie(signIn("jane@example.com"))

        me(session).andExpect {
            status { isOk() }
            jsonPath("$.data.me.email") { value("jane@example.com") }
            jsonPath("$.data.me.id") { isNotEmpty() }
        }
    }

    @Test
    fun `the API requires a session`() {
        me(session = null).andExpect {
            status { isUnauthorized() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.requestId") { isNotEmpty() }
        }
    }

    @Test
    fun `logging out ends the session`() {
        val session = sessionCookie(signIn("jane@example.com"))

        mockMvc
            .post("/api/auth/logout") {
                with(csrf())
                cookie(session)
            }.andExpect { status { isNoContent() } }

        me(session).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `a session planted before sign-in is not reused`() {
        val plantedSession = sessionCookie(signIn("attacker@example.com"))

        val victimSession = sessionCookie(signIn("victim@example.com", existingSession = plantedSession))

        assertNotEquals(plantedSession.value, victimSession.value)
        me(plantedSession).andExpect { status { isUnauthorized() } }
        me(victimSession).andExpect { jsonPath("$.data.me.email") { value("victim@example.com") } }
    }

    private fun signIn(
        email: String,
        existingSession: Cookie? = null,
    ): MvcResult {
        mockMvc
            .post("/api/auth/request") {
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"email": "$email"}"""
            }.andExpect { status { isOk() } }
        val token = emailPort.sent.last { it.email == email }.rawToken

        return mockMvc
            .post("/api/auth/verify") {
                with(csrf())
                existingSession?.let { cookie(it) }
                contentType = MediaType.APPLICATION_JSON
                content = """{"token": "$token"}"""
            }.andExpect { status { isNoContent() } }
            .andReturn()
    }

    private fun sessionCookie(result: MvcResult) = result.response.getCookie("SESSION")!!

    private fun me(session: Cookie?): ResultActionsDsl {
        val result =
            mockMvc
                .post("/api/graphql") {
                    with(csrf())
                    session?.let { cookie(it) }
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"query": "{ me { id email } }"}"""
                }.andReturn()
        // Spring GraphQL answers asynchronously; requests rejected by security never start async processing
        val actions = if (result.request.isAsyncStarted) mockMvc.perform(asyncDispatch(result)) else Completed(result)
        return ResultActionsDsl(actions, mockMvc)
    }

    private class Completed(
        private val result: MvcResult,
    ) : ResultActions {
        override fun andExpect(matcher: ResultMatcher) = apply { matcher.match(result) }

        override fun andDo(handler: ResultHandler) = apply { handler.handle(result) }

        override fun andReturn() = result
    }
}
