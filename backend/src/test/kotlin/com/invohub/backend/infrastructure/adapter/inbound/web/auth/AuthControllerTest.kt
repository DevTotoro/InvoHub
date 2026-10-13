package com.invohub.backend.infrastructure.adapter.inbound.web.auth

import com.invohub.backend.domain.auth.EmailDeliveryException
import com.invohub.backend.domain.user.Language
import com.invohub.backend.domain.user.User
import com.invohub.backend.domain.user.UserRepository
import com.invohub.backend.support.CapturingEmailPort
import com.invohub.backend.support.DatabaseCleaner
import com.invohub.backend.support.IntegrationTest
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
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
    private lateinit var databaseCleaner: DatabaseCleaner

    @Autowired
    private lateinit var emailPort: CapturingEmailPort

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    @Autowired
    private lateinit var userRepository: UserRepository

    @BeforeEach
    fun reset() {
        emailPort.clear()
        databaseCleaner.clean()
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
    fun `a used link says it can be renewed, and renewing sends a fresh one`() {
        requestLink("jane@example.com")
        val token = emailPort.sent.single().rawToken
        verify(token)

        verify(token).andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("expired_link") }
        }
        renew(token).andExpect { status { isOk() } }

        assertEquals(2, emailPort.sent.size)
        verify(emailPort.sent.last().rawToken).andExpect { status { isNoContent() } }
    }

    @Test
    fun `an unknown link can't be renewed`() {
        renew("not-a-real-token").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("invalid_link") }
        }
    }

    @Test
    fun `a suspended user is refused without revealing the reason`() {
        userRepository.save(User("suspended@example.com").apply { suspend("internal fraud note") })
        requestLink("suspended@example.com")

        verify(emailPort.sent.single().rawToken).andExpect {
            status { isForbidden() }
            jsonPath("$.detail") { value("This account is suspended.") }
            jsonPath("$.code") { value("account_suspended") }
            content { string(not(containsString("fraud"))) }
        }
    }

    @Test
    fun `an unknown token is rejected with a problem detail`() {
        verify("not-a-real-token").andExpect {
            status { isUnauthorized() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.detail") { value("This sign-in link is invalid or has expired.") }
            jsonPath("$.code") { value("invalid_link") }
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
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"email": "not-an-email"}"""
            }.andExpect {
                status { isBadRequest() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.code") { value("invalid_request") }
                jsonPath("$.errors[0].field") { value("email") }
            }

        assertEquals(0, emailPort.sent.size)
    }

    @Test
    fun `malformed JSON is rejected`() {
        mockMvc
            .post("/api/auth/request") {
                with(csrf())
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
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"email": "jane@example.com"}"""
            }.andExpect {
                status { isServiceUnavailable() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.detail") { value(containsString("try again")) }
                jsonPath("$.code") { value("email_delivery_failed") }
            }
    }

    @Test
    fun `the sign-in email and a new account are in the language in use`() {
        requestLink("jane@example.com", language = "EL")
        val sent = emailPort.sent.single()

        verify(sent.rawToken, language = "EL").andExpect { status { isNoContent() } }

        assertEquals(Language.EL, sent.language)
        assertEquals(Language.EL, userRepository.findByEmail("jane@example.com")?.language)
    }

    @Test
    fun `without a language, English is used`() {
        requestLink("jane@example.com")
        val sent = emailPort.sent.single()

        verify(sent.rawToken).andExpect { status { isNoContent() } }

        assertEquals(Language.EN, sent.language)
        assertEquals(Language.EN, userRepository.findByEmail("jane@example.com")?.language)
    }

    @Test
    fun `an unknown language is rejected`() {
        mockMvc
            .post("/api/auth/request") {
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"email": "jane@example.com", "language": "XX"}"""
            }.andExpect { status { isBadRequest() } }

        assertEquals(0, emailPort.sent.size)
    }

    private fun requestLink(
        email: String,
        language: String? = null,
    ) = mockMvc
        .post("/api/auth/request") {
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = json("email" to email, "language" to language)
        }.andExpect { status { isOk() } }

    private fun verify(
        token: String,
        language: String? = null,
    ) = mockMvc.post("/api/auth/verify") {
        with(csrf())
        contentType = MediaType.APPLICATION_JSON
        content = json("token" to token, "language" to language)
    }

    private fun renew(token: String) =
        mockMvc.post("/api/auth/renew") {
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = json("token" to token)
        }

    private fun json(vararg fields: Pair<String, String?>) =
        fields.filter { it.second != null }.joinToString(", ", "{", "}") { (k, v) -> "\"$k\": \"$v\"" }
}
