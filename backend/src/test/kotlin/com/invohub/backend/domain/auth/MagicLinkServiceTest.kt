package com.invohub.backend.domain.auth

import com.invohub.backend.domain.user.User
import com.invohub.backend.domain.user.UserService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MagicLinkServiceTest {
    private val email = "jane@example.com"

    private lateinit var tokenStore: InMemoryMagicLinkTokenStore
    private lateinit var emailPort: EmailPort
    private lateinit var userService: UserService
    private lateinit var service: MagicLinkService

    private val sentTokens = mutableListOf<String>()

    @BeforeEach
    fun setUp() {
        tokenStore = InMemoryMagicLinkTokenStore()
        emailPort = mockk()
        every { emailPort.sendMagicLink(any(), capture(sentTokens)) } returns Unit
        userService = mockk()
        every { userService.getOrCreateUser(any()) } answers { User(firstArg()) }
        service = MagicLinkService(tokenStore, emailPort, userService)
    }

    @Test
    fun `sends a token that verifies to the requesting email`() {
        service.requestToken(email)

        val user = service.verifyToken(sentTokens.single())

        assertEquals(email, user?.email)
    }

    @Test
    fun `does not issue another token while one is active`() {
        service.requestToken(email)
        service.requestToken(email)

        verify(exactly = 1) { emailPort.sendMagicLink(email, any()) }
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
    fun `an unknown token does not resolve a user`() {
        assertNull(service.verifyToken("not-a-real-token"))

        verify(exactly = 0) { userService.getOrCreateUser(any()) }
    }

    @Test
    fun `a failed send is rolled back so the user can retry immediately`() {
        every { emailPort.sendMagicLink(any(), capture(sentTokens)) } throws EmailDeliveryException("SMTP down")

        assertFailsWith<EmailDeliveryException> { service.requestToken(email) }
        val undeliveredToken = sentTokens.single()

        every { emailPort.sendMagicLink(any(), capture(sentTokens)) } returns Unit
        service.requestToken(email)

        assertNull(service.verifyToken(undeliveredToken))
        assertEquals(email, service.verifyToken(sentTokens.last())?.email)
    }
}
