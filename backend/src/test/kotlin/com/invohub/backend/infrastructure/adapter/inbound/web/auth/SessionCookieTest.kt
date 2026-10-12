package com.invohub.backend.infrastructure.adapter.inbound.web.auth

import com.invohub.backend.support.CapturingEmailPort
import com.invohub.backend.support.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.context.ActiveProfiles
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs against real Tomcat: without an embedded server (MockMvc), Spring Boot takes the session cookie
 * attributes from the servlet context instead of server.servlet.session.cookie.*, so only this proves
 * what browsers actually receive.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class, CapturingEmailPort::class)
class SessionCookieTest {
    @LocalServerPort
    private var port = 0

    @Autowired
    private lateinit var emailPort: CapturingEmailPort

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    private val client = HttpClient.newHttpClient()

    @Test
    fun `the token from the csrf endpoint is accepted when echoed in the header`() {
        val csrf = fetchCsrfToken()

        val response = send(postJson("/api/auth/request", """{"email": "jane@example.com"}""", csrf))

        assertEquals(200, response.statusCode())
    }

    @Test
    fun `state-changing requests without the CSRF header are rejected`() {
        val withoutHeader =
            HttpRequest
                .newBuilder(uri("/api/auth/request"))
                .header("Content-Type", "application/json")
                .header("Cookie", "XSRF-TOKEN=${fetchCsrfToken()}")
                .POST(HttpRequest.BodyPublishers.ofString("""{"email": "jane@example.com"}"""))

        val response = send(withoutHeader)

        assertEquals(403, response.statusCode())
        assertContains(response.body(), "Missing or invalid CSRF token.")
        assertContains(response.body(), "\"code\":\"invalid_csrf_token\"")
    }

    @Test
    fun `sign-in sets an HttpOnly, SameSite=Lax, Secure session cookie`() {
        val csrf = fetchCsrfToken()

        val requested = send(postJson("/api/auth/request", """{"email": "john@example.com"}""", csrf))
        assertEquals(200, requested.statusCode())

        val token = emailPort.sent.last { it.email == "john@example.com" }.rawToken
        val verified = send(postJson("/api/auth/verify", """{"token": "$token"}""", csrf))
        assertEquals(200, verified.statusCode())

        val sessionCookie = verified.setCookies().single { it.startsWith("SESSION=") }
        assertContains(sessionCookie, "HttpOnly")
        assertContains(sessionCookie, "SameSite=Lax")
        assertContains(sessionCookie, "Secure")
    }

    @Test
    fun `a session expires after two hours of inactivity`() {
        val csrf = fetchCsrfToken()
        send(postJson("/api/auth/request", """{"email": "ttl@example.com"}""", csrf))
        val token = emailPort.sent.last { it.email == "ttl@example.com" }.rawToken
        val verified = send(postJson("/api/auth/verify", """{"token": "$token"}""", csrf))

        // The cookie holds the Base64-encoded session ID, which keys the session in Redis
        val cookieValue =
            verified
                .setCookies()
                .single {
                    it.startsWith(
                        "SESSION=",
                    )
                }.substringAfter("=")
                .substringBefore(";")
        val sessionId = String(Base64.getDecoder().decode(cookieValue))
        val ttl = redisTemplate.getExpire("invohub:session:sessions:$sessionId", TimeUnit.SECONDS)

        assertTrue(ttl in Duration.ofMinutes(119).seconds..Duration.ofHours(2).seconds, "session TTL was ${ttl}s")
    }

    private fun fetchCsrfToken() =
        send(HttpRequest.newBuilder(uri("/api/auth/csrf")).GET())
            .setCookies()
            .single { it.startsWith("XSRF-TOKEN=") }
            .substringAfter("=")
            .substringBefore(";")

    private fun uri(path: String) = URI("http://localhost:$port$path")

    private fun postJson(
        path: String,
        body: String,
        csrf: String,
    ) = HttpRequest
        .newBuilder(uri(path))
        .header("Content-Type", "application/json")
        .header("Cookie", "XSRF-TOKEN=$csrf")
        .header("X-XSRF-TOKEN", csrf)
        .POST(HttpRequest.BodyPublishers.ofString(body))

    private fun send(request: HttpRequest.Builder) = client.send(request.build(), HttpResponse.BodyHandlers.ofString())

    private fun HttpResponse<*>.setCookies() = headers().allValues("Set-Cookie")
}
