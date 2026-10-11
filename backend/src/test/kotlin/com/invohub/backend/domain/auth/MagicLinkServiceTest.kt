package com.invohub.backend.domain.auth

import com.invohub.backend.domain.user.User
import com.invohub.backend.domain.user.UserService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URI
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MagicLinkServiceTest {
    private val email = "jane@example.com"

    private lateinit var tokenStore: InMemoryMagicLinkTokenStore
    private lateinit var emailPort: EmailPort
    private lateinit var userService: UserService
    private lateinit var service: MagicLinkService

    private val sentLinks = mutableListOf<URI>()
    private val sentTokens get() = sentLinks.map { it.query.removePrefix("token=") }

    @BeforeEach
    fun setUp() {
        tokenStore = InMemoryMagicLinkTokenStore()
        emailPort = mockk()
        every { emailPort.sendMagicLink(any(), capture(sentLinks), any()) } returns Unit
        userService = mockk()
        every { userService.getOrCreateUser(any()) } answers { User(firstArg()) }
        val properties = AuthProperties(URI("https://app.example.com"), Duration.ofHours(1))
        service = MagicLinkService(tokenStore, emailPort, userService, properties)
    }

    @Test
    fun `sends a token that verifies to the requesting email`() {
        service.requestToken(email)

        val user = service.verifyToken(sentTokens.single())

        assertEquals(email, user?.email)
    }

    @Test
    fun `links to the frontend verify page`() {
        service.requestToken(email)

        val link = sentLinks.single()
        assertEquals("https://app.example.com/auth/verify", "${link.scheme}://${link.host}${link.path}")
    }

    @Test
    fun `normalizes the email so case variants share one lock and one account`() {
        service.requestToken("  Jane@Example.COM ")
        service.requestToken(email)

        verify(exactly = 1) { emailPort.sendMagicLink(email, any(), any()) }
        assertEquals(email, service.verifyToken(sentTokens.single())?.email)
    }

    @Test
    fun `does not issue another token while one is active`() {
        service.requestToken(email)
        service.requestToken(email)

        verify(exactly = 1) { emailPort.sendMagicLink(email, any(), any()) }
    }

    @Test
    fun `a new token can be requested once the active one is used`() {
        service.requestToken(email)
        service.verifyToken(sentTokens.single())

        service.requestToken(email)

        assertEquals(2, sentTokens.size)
    }

    @Test
    fun `a token can only be used once`() {
        service.requestToken(email)
        val token = sentTokens.single()

        service.verifyToken(token)

        assertNull(service.verifyToken(token))
    }

    @Test
    fun `a locked user cannot sign in`() {
        every { userService.getOrCreateUser(email) } returns User(email, locked = true, lockReason = "chargeback")
        service.requestToken(email)

        assertFailsWith<AccountLockedException> { service.verifyToken(sentTokens.single()) }
    }

    @Test
    fun `an unknown token does not resolve a user`() {
        assertNull(service.verifyToken("not-a-real-token"))

        verify(exactly = 0) { userService.getOrCreateUser(any()) }
    }

    @Test
    fun `a failed send is rolled back so the user can retry immediately`() {
        every { emailPort.sendMagicLink(any(), capture(sentLinks), any()) } throws EmailDeliveryException("SMTP down")

        assertFailsWith<EmailDeliveryException> { service.requestToken(email) }
        val undeliveredToken = sentTokens.single()

        every { emailPort.sendMagicLink(any(), capture(sentLinks), any()) } returns Unit
        service.requestToken(email)

        assertNull(service.verifyToken(undeliveredToken))
        assertEquals(email, service.verifyToken(sentTokens.last())?.email)
    }
}
