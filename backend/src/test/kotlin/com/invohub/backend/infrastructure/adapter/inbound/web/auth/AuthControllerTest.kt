package com.invohub.backend.infrastructure.adapter.inbound.web.auth

import com.invohub.backend.domain.auth.EmailDeliveryException
import com.invohub.backend.support.CapturingEmailPort
import com.invohub.backend.support.IntegrationTest
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import kotlin.test.assertEquals

@IntegrationTest
@AutoConfigureMockMvc
class AuthControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var emailPort: CapturingEmailPort

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    @BeforeEach
    fun reset() {
        emailPort.clear()
        redisTemplate.connectionFactory!!.connection.use { it.serverCommands().flushAll() }
    }

    @Test
    fun `a requested link signs the user in exactly once`() {
        requestLink("jane@example.com")
        val token = emailPort.sent.single().rawToken

        verify(token).andExpect { status { isNoContent() } }
        verify(token).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `an unknown token is rejected with a problem detail`() {
        verify("not-a-real-token").andExpect {
            status { isUnauthorized() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.detail") { value("This sign-in link is invalid or has expired.") }
            jsonPath("$.requestId") { isNotEmpty() }
        }
    }

    @Test
    fun `opening the link with GET does not consume the token`() {
        requestLink("jane@example.com")
        val token = emailPort.sent.single().rawToken

        mockMvc.get("/api/auth/verify") { param("token", token) }.andExpect { status { isMethodNotAllowed() } }

        verify(token).andExpect { status { isNoContent() } }
    }

    @Test
    fun `an invalid email is rejected with the offending field`() {
        mockMvc
            .post("/api/auth/request") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email": "not-an-email"}"""
            }.andExpect {
                status { isBadRequest() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.errors[0].field") { value("email") }
            }

        assertEquals(0, emailPort.sent.size)
    }

    @Test
    fun `malformed JSON is rejected`() {
        mockMvc
            .post("/api/auth/request") {
                contentType = MediaType.APPLICATION_JSON
                content = "{"
            }.andExpect {
                status { isBadRequest() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            }
    }

    @Test
    fun `an email delivery failure is reported as 503`() {
        emailPort.failure = EmailDeliveryException("SMTP down")

        mockMvc
            .post("/api/auth/request") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email": "jane@example.com"}"""
            }.andExpect {
                status { isServiceUnavailable() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.detail") { value(containsString("try again")) }
            }
    }

    private fun requestLink(email: String) =
        mockMvc
            .post("/api/auth/request") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email": "$email"}"""
            }.andExpect { status { isOk() } }

    private fun verify(token: String) =
        mockMvc.post("/api/auth/verify") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"token": "$token"}"""
        }
}
