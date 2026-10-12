package com.invohub.backend.infrastructure.adapter.outbound.email

import com.invohub.backend.domain.auth.EmailDeliveryException
import com.invohub.backend.domain.auth.InvitationNotice
import com.invohub.backend.domain.company.CompanyRole
import com.invohub.backend.domain.user.Language
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.context.support.ResourceBundleMessageSource
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
import kotlin.test.assertFalse

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
        adapter(
            port = mailpit.getMappedPort(1025),
        ).sendMagicLink("jane@example.com", link, Duration.ofHours(1), Language.EN)

        val message = latestMessage()
        assertEquals("jane@example.com", message.at("/To/0/Address").asString())
        assertEquals("no-reply@invohub.com", message.at("/From/Address").asString())
        assertContains(message.at("/Text").asString(), link.toString())
        assertContains(message.at("/Text").asString(), "expires in 1 hour")
        // HTML, with the link as a button and written out for when the button doesn't work
        val html = message.at("/HTML").asString()
        assertContains(html, """<a href="$link" style="display:inline-block""")
        assertContains(html, ">Sign in</a>")
        assertContains(html, "Button not working?")
    }

    @Test
    fun `sends the magic link in the user's language`() {
        adapter(
            port = mailpit.getMappedPort(1025),
        ).sendMagicLink("jane@example.com", link, Duration.ofMinutes(30), Language.EL)

        val message = latestMessage()
        assertEquals("InvoHub - Σύνδεσμος σύνδεσης", message.at("/Subject").asString())
        assertContains(message.at("/Text").asString(), link.toString())
        assertContains(message.at("/Text").asString(), "λήγει σε 30 λεπτά")
    }

    @Test
    fun `sends an invitation email listing every company, in the person's language`() {
        val notices =
            listOf(
                InvitationNotice("Acme", "jane@example.com", CompanyRole.ADMIN),
                InvitationNotice("Globex", "john@example.com", CompanyRole.VIEWER),
            )

        adapter(port = mailpit.getMappedPort(1025))
            .sendInvitations("maria@example.com", notices, link, Duration.ofHours(1), Language.EL)

        val message = latestMessage()
        assertEquals("InvoHub - Πρόσκληση", message.at("/Subject").asString())
        val text = message.at("/Text").asString()
        assertContains(text, "- Acme: Διαχειριστής, πρόσκληση από jane@example.com")
        assertContains(text, "- Globex: Μόνο προβολή, πρόσκληση από john@example.com")
        assertContains(text, link.toString())
        assertContains(message.at("/HTML").asString(), ">Προβολή προσκλήσεων</a>")
    }

    @Test
    fun `the English invitation email keeps its apostrophes`() {
        adapter(port = mailpit.getMappedPort(1025)).sendInvitations(
            "maria@example.com",
            listOf(InvitationNotice("Acme", "jane@example.com", CompanyRole.MEMBER)),
            link,
            Duration.ofHours(1),
            Language.EN,
        )

        val text = latestMessage().at("/Text").asString()
        assertContains(text, "You've been invited to InvoHub:")
        assertContains(text, "- Acme: Member, invited by jane@example.com")
    }

    @Test
    fun `other people's words can't add markup`() {
        adapter(port = mailpit.getMappedPort(1025)).sendInvitations(
            "maria@example.com",
            listOf(
                InvitationNotice("<a href=\"https://evil.example\">Click</a>", "jane@example.com", CompanyRole.MEMBER),
            ),
            link,
            Duration.ofHours(1),
            Language.EN,
        )

        val html = latestMessage().at("/HTML").asString()
        assertContains(html, "&lt;a href=&quot;https://evil.example&quot;&gt;Click&lt;/a&gt;")
        assertFalse(html.contains("<a href=\"https://evil.example\""))
    }

    @Test
    fun `reports a delivery failure as EmailDeliveryException`() {
        val unreachable = adapter(port = 1)

        assertFailsWith<EmailDeliveryException> {
            unreachable.sendMagicLink("jane@example.com", link, Duration.ofHours(1), Language.EN)
        }
    }

    private fun adapter(port: Int) =
        SmtpEmailAdapter(
            JavaMailSenderImpl().apply {
                host = mailpit.host
                this.port = port
                defaultEncoding = "UTF-8"
            },
            MailProperties(from = "no-reply@invohub.com"),
            // As configured in application.yaml (spring.messages)
            ResourceBundleMessageSource().apply {
                setBasename("i18n/emails")
                setDefaultEncoding("UTF-8")
                setFallbackToSystemLocale(false)
            },
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
