package com.invohub.backend.infrastructure.adapter.outbound.email

import com.invohub.backend.domain.auth.EmailDeliveryException
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Sends through real SMTP into Mailpit and reads the message back through its API. */
class SmtpEmailAdapterTest {
    companion object {
        private val mailpit =
            GenericContainer(DockerImageName.parse("axllent/mailpit:v1.31")).withExposedPorts(1025, 8025)

        @JvmStatic
        @BeforeAll
        fun start() = mailpit.start()

        @JvmStatic
        @AfterAll
        fun stop() = mailpit.stop()
    }

    private val link = URI("https://app.example.com/auth/verify?token=abc")

    @Test
    fun `sends the magic link`() {
        adapter(port = mailpit.getMappedPort(1025)).sendMagicLink("jane@example.com", link, Duration.ofHours(1))

        val message = latestMessage()
        assertEquals("jane@example.com", message.at("/To/0/Address").asString())
        assertEquals("no-reply@invohub.com", message.at("/From/Address").asString())
        assertContains(message.at("/Text").asString(), link.toString())
        assertContains(message.at("/Text").asString(), "expire in 1 hour")
    }

    @Test
    fun `reports a delivery failure as EmailDeliveryException`() {
        val unreachable = adapter(port = 1)

        assertFailsWith<EmailDeliveryException> {
            unreachable.sendMagicLink("jane@example.com", link, Duration.ofHours(1))
        }
    }

    private fun adapter(port: Int) =
        SmtpEmailAdapter(
            JavaMailSenderImpl().apply {
                host = mailpit.host
                this.port = port
            },
            MailProperties(from = "no-reply@invohub.com"),
        )

    private fun latestMessage() = JsonMapper().readTree(get("/api/v1/message/latest"))

    private fun get(path: String): String {
        val uri = URI("http://${mailpit.host}:${mailpit.getMappedPort(8025)}$path")
        return HttpClient
            .newHttpClient()
            .send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString())
            .body()
    }
}
