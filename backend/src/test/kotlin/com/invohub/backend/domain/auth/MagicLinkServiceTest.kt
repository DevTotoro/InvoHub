package com.invohub.backend.domain.auth

import com.invohub.backend.domain.audit.AuditAction
import com.invohub.backend.domain.audit.AuditLog
import com.invohub.backend.domain.user.Language
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
    private lateinit var auditLog: AuditLog
    private lateinit var service: MagicLinkService

    private val sentLinks = mutableListOf<URI>()
    private val sentTokens get() = sentLinks.map { it.query.removePrefix("token=") }

    @BeforeEach
    fun setUp() {
        tokenStore = InMemoryMagicLinkTokenStore()
        emailPort = mockk()
        every { emailPort.sendMagicLink(any(), capture(sentLinks), any(), any()) } returns Unit
        userService = mockk()
        every { userService.findByEmail(any()) } returns null
        every { userService.getOrCreateUser(any(), any()) } answers { User(firstArg(), secondArg()) }
        auditLog = mockk(relaxed = true)
        val properties =
            AuthProperties(URI("https://app.example.com"), Duration.ofHours(1), Duration.ofDays(7), Duration.ofDays(7))
        service = MagicLinkService(tokenStore, emailPort, userService, properties, auditLog)
    }

    @Test
    fun `sends a token that verifies to the requesting email`() {
        service.requestToken(email, Language.EN)

        val signIn = service.verifyToken(sentTokens.single(), Language.EN)

        assertEquals(email, signIn?.user?.email)
        assertEquals(SignInDestination.DEFAULT, signIn?.destination)
    }

    @Test
    fun `links to the frontend verify page`() {
        service.requestToken(email, Language.EN)

        val link = sentLinks.single()
        assertEquals("https://app.example.com/auth/verify", "${link.scheme}://${link.host}${link.path}")
    }

    @Test
    fun `normalizes the email so case variants share one lock and one account`() {
        service.requestToken("  Jane@Example.COM ", Language.EN)
        service.requestToken(email, Language.EN)

        verify(exactly = 1) { emailPort.sendMagicLink(email, any(), any(), any()) }
        assertEquals(email, service.verifyToken(sentTokens.single(), Language.EN)?.user?.email)
    }

    @Test
    fun `does not issue another token while one is active`() {
        service.requestToken(email, Language.EN)
        service.requestToken(email, Language.EN)

        verify(exactly = 1) { emailPort.sendMagicLink(email, any(), any(), any()) }
    }

    @Test
    fun `a new token can be requested once the active one is used`() {
        service.requestToken(email, Language.EN)
        service.verifyToken(sentTokens.single(), Language.EN)

        service.requestToken(email, Language.EN)

        assertEquals(2, sentTokens.size)
    }

    @Test
    fun `a token can only be used once`() {
        service.requestToken(email, Language.EN)
        val token = sentTokens.single()

        service.verifyToken(token, Language.EN)

        assertNull(service.verifyToken(token, Language.EN))
    }

    @Test
    fun `a suspended user cannot sign in, and the refusal is recorded`() {
        every { userService.getOrCreateUser(email, any()) } returns User(email).apply { suspend("chargeback") }
        service.requestToken(email, Language.EN)

        assertFailsWith<AccountSuspendedException> { service.verifyToken(sentTokens.single(), Language.EN) }

        verify { auditLog.recordStandalone(match { it.action == AuditAction.USER_SIGN_IN_REFUSED }) }
        verify(exactly = 0) { auditLog.recordStandalone(match { it.action == AuditAction.USER_SIGNED_IN }) }
    }

    @Test
    fun `a sign-in is recorded`() {
        service.requestToken(email, Language.EN)

        val user = service.verifyToken(sentTokens.single(), Language.EN)!!.user

        verify {
            auditLog.recordStandalone(
                match { it.action == AuditAction.USER_SIGNED_IN && it.actorUserId == user.id },
            )
        }
    }

    @Test
    fun `an unusable link records nothing`() {
        service.verifyToken("not-a-real-token", Language.EN)

        verify(exactly = 0) { auditLog.recordStandalone(any()) }
    }

    @Test
    fun `an unknown token does not resolve a user`() {
        assertNull(service.verifyToken("not-a-real-token", Language.EN))

        verify(exactly = 0) { userService.getOrCreateUser(any(), any()) }
    }

    @Test
    fun `a failed send is rolled back so the user can retry immediately`() {
        every { emailPort.sendMagicLink(any(), capture(sentLinks), any(), any()) } throws
            EmailDeliveryException("SMTP down")

        assertFailsWith<EmailDeliveryException> { service.requestToken(email, Language.EN) }
        val undeliveredToken = sentTokens.single()

        every { emailPort.sendMagicLink(any(), capture(sentLinks), any(), any()) } returns Unit
        service.requestToken(email, Language.EN)

        assertNull(service.verifyToken(undeliveredToken, Language.EN))
        assertEquals(email, service.verifyToken(sentTokens.last(), Language.EN)?.user?.email)
    }

    @Test
    fun `emails a new person in the language they are using`() {
        service.requestToken(email, Language.EL)

        verify { emailPort.sendMagicLink(email, any(), any(), Language.EL) }
    }

    @Test
    fun `emails an existing user in their own language`() {
        every { userService.findByEmail(email) } returns User(email, Language.EN)

        service.requestToken(email, Language.EL)

        verify { emailPort.sendMagicLink(email, any(), any(), Language.EN) }
    }

    @Test
    fun `signing in for the first time creates the user in the language they are using`() {
        service.requestToken(email, Language.EL)

        val signIn = service.verifyToken(sentTokens.single(), Language.EL)

        assertEquals(Language.EL, signIn?.user?.language)
    }

    @Test
    fun `an invitation link signs the person in on their Invitations page`() {
        val issued = service.invitationLink("  Maria@Example.com ")

        val signIn = service.verifyToken(issued.link.query.removePrefix("token="), Language.EN)

        assertEquals("maria@example.com", signIn?.user?.email)
        assertEquals(SignInDestination.INVITATIONS, signIn?.destination)
        assertEquals(Duration.ofHours(1), issued.validFor)
    }

    @Test
    fun `an invitation link goes out even while a sign-in link is active`() {
        service.requestToken(email, Language.EN)

        val issued = service.invitationLink(email)

        assertEquals(email, service.verifyToken(issued.link.query.removePrefix("token="), Language.EN)?.user?.email)
    }

    @Test
    fun `an expired or used link is renewable, an unknown one isn't`() {
        service.requestToken(email, Language.EN)
        val token = sentTokens.single()
        service.verifyToken(token, Language.EN)

        assertEquals(LinkProblem.RENEWABLE, service.problemWith(token))
        assertEquals(LinkProblem.INVALID, service.problemWith("not-a-real-token"))
    }

    @Test
    fun `renewing sends a fresh link to the same address, leading to the same place`() {
        val old =
            service
                .invitationLink(email)
                .link.query
                .removePrefix("token=")
        tokenStore.expireAll()

        service.renew(old, Language.EN)

        val fresh = service.verifyToken(sentTokens.single(), Language.EN)
        assertEquals(email, fresh?.user?.email)
        assertEquals(SignInDestination.INVITATIONS, fresh?.destination)
    }

    @Test
    fun `renewing sends nothing while a link for the address is active`() {
        service.requestToken(email, Language.EN)
        val first = sentTokens.single()

        service.renew(first, Language.EN)

        assertEquals(1, sentLinks.size)
    }

    @Test
    fun `an unknown link can't be renewed`() {
        assertFailsWith<InvalidSignInLinkException> { service.renew("not-a-real-token", Language.EN) }
    }
}
