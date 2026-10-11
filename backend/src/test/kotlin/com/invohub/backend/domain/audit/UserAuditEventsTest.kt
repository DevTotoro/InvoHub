package com.invohub.backend.domain.audit

import com.invohub.backend.domain.user.UserRepository
import com.invohub.backend.domain.user.UserService
import com.invohub.backend.support.AuditEvents
import com.invohub.backend.support.CapturingEmailPort
import com.invohub.backend.support.IntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.post
import kotlin.test.assertEquals

/** The user.* events, recorded through the real sign-in flow. */
@IntegrationTest
@AutoConfigureMockMvc
class UserAuditEventsTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var emailPort: CapturingEmailPort

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var userService: UserService

    @Autowired
    private lateinit var auditEventRepository: AuditEventRepository

    private val events by lazy { AuditEvents(auditEventRepository) }

    @BeforeEach
    fun reset() {
        emailPort.clear()
        userRepository.deleteAll()
        redisTemplate.connectionFactory!!.connection.use { it.serverCommands().flushAll() }
    }

    @Test
    fun `the first sign-in creates the user, later ones only sign in`() {
        signIn("jane@example.com")
        signIn("jane@example.com")

        assertEquals(
            listOf("user.created", "user.signed_in", "user.signed_in"),
            events.forUser(userId("jane@example.com")),
        )
    }

    @Test
    fun `signing out is recorded`() {
        val session = signIn("jane@example.com").andReturn().response.getCookie("SESSION")!!

        mockMvc
            .post("/api/auth/logout") {
                with(csrf())
                cookie(session)
            }.andExpect { status { isNoContent() } }

        assertEquals(
            listOf("user.created", "user.signed_in", "user.signed_out"),
            events.forUser(userId("jane@example.com")),
        )
    }

    @Test
    fun `suspending, a refused sign-in and unsuspending are recorded`() {
        signIn("jane@example.com")
        val id = userId("jane@example.com")

        userService.suspendUser(id, "chargeback")
        signIn("jane@example.com").andExpect { status { isForbidden() } }
        userService.unsuspendUser(id)

        assertEquals(
            listOf("user.created", "user.signed_in", "user.suspended", "user.sign_in_refused", "user.unsuspended"),
            events.forUser(id),
        )
    }

    private fun userId(email: String) = userRepository.findByEmail(email)!!.id

    private fun signIn(email: String): ResultActionsDsl {
        mockMvc
            .post("/api/auth/request") {
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"email": "$email"}"""
            }.andExpect { status { isOk() } }
        val token = emailPort.sent.last { it.email == email }.rawToken

        return mockMvc.post("/api/auth/verify") {
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"token": "$token"}"""
        }
    }
}
