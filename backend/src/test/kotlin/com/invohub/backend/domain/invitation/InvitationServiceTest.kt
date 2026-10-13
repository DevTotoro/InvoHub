package com.invohub.backend.domain.invitation

import com.invohub.backend.domain.audit.AuditEventRepository
import com.invohub.backend.domain.auth.EmailDeliveryException
import com.invohub.backend.domain.auth.MagicLinkService
import com.invohub.backend.domain.common.InvalidInputException
import com.invohub.backend.domain.company.Company
import com.invohub.backend.domain.company.CompanyAccessService
import com.invohub.backend.domain.company.CompanyMember
import com.invohub.backend.domain.company.CompanyMemberRepository
import com.invohub.backend.domain.company.CompanyPermissionDeniedException
import com.invohub.backend.domain.company.CompanyRepository
import com.invohub.backend.domain.company.CompanyRole
import com.invohub.backend.domain.company.CompanyService
import com.invohub.backend.domain.company.CompanyStatus
import com.invohub.backend.domain.company.CompanySuspendedException
import com.invohub.backend.domain.user.Language
import com.invohub.backend.domain.user.UserService
import com.invohub.backend.support.CapturingEmailPort
import com.invohub.backend.support.DatabaseCleaner
import com.invohub.backend.support.IntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.util.UriComponentsBuilder
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@IntegrationTest
class InvitationServiceTest {
    @Autowired
    private lateinit var invitationService: InvitationService

    @Autowired
    private lateinit var invitationRepository: InvitationRepository

    @Autowired
    private lateinit var accessService: CompanyAccessService

    @Autowired
    private lateinit var companyService: CompanyService

    @Autowired
    private lateinit var companyRepository: CompanyRepository

    @Autowired
    private lateinit var memberRepository: CompanyMemberRepository

    @Autowired
    private lateinit var userService: UserService

    @Autowired
    private lateinit var auditEventRepository: AuditEventRepository

    @Autowired
    private lateinit var emailPort: CapturingEmailPort

    @Autowired
    private lateinit var databaseCleaner: DatabaseCleaner

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var limits: InvitationLimits

    @Autowired
    private lateinit var magicLinkService: MagicLinkService

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    private val maria = "maria@example.com"

    private var owner = 0L
    private lateinit var acme: Company
    private lateinit var globex: Company

    @BeforeEach
    fun reset() {
        emailPort.clear()
        databaseCleaner.clean()
        redisTemplate.connectionFactory!!.connection.use { it.serverCommands().flushAll() }
        owner = newUser("owner@example.com")
        acme = companyService.createCompany(owner, "Acme", "GR")
        globex = companyService.createCompany(owner, "Globex", "GR")
    }

    @Test
    fun `invites to several companies at once, with one email about all of them`() {
        val invitations = invite(maria, acme to CompanyRole.ADMIN, globex to CompanyRole.VIEWER)

        assertEquals(listOf(InvitationStatus.PENDING, InvitationStatus.PENDING), invitations.map { it.status })
        val email = emailPort.invitationEmails.single()
        assertEquals(maria, email.email)
        assertEquals(
            listOf("Acme" to CompanyRole.ADMIN, "Globex" to CompanyRole.VIEWER),
            email.invitations.map {
                it.companyName to
                    it.role
            },
        )
        assertEquals("owner@example.com", email.invitations.first().invitedByEmail)
        assertEquals("http://localhost:5173/auth/verify", email.link.toString().substringBefore('?'))
        assertEquals(Language.EN, email.language)
        assertEquals(listOf("invitation.sent"), actionsFor(invitations.first()))
    }

    @Test
    fun `the email is in the person's own language if they have an account`() {
        userService.getOrCreateUser(maria, Language.EL)

        invite(maria, acme to CompanyRole.MEMBER)

        assertEquals(Language.EL, emailPort.invitationEmails.single().language)
    }

    @Test
    fun `refuses bad input`() {
        assertEquals(
            "email",
            assertFailsWith<InvalidInputException> {
                invite("not-an-email", acme to CompanyRole.MEMBER)
            }.field,
        )
        assertEquals("companies", assertFailsWith<InvalidInputException> { invite(maria) }.field)
        assertEquals("role", assertFailsWith<InvalidInputException> { invite(maria, acme to CompanyRole.OWNER) }.field)
    }

    @Test
    fun `only the owner and admins invite`() {
        val viewer = newUser("viewer@example.com")
        memberRepository.save(CompanyMember(acme.id, viewer, CompanyRole.VIEWER))

        assertFailsWith<CompanyPermissionDeniedException> {
            invitationService.invite(
                viewer,
                maria,
                listOf(InvitationTarget(accessService.resolve(viewer, acme.id), CompanyRole.MEMBER)),
            )
        }
    }

    @Test
    fun `nobody already in the company, or already invited to it, is invited`() {
        val member = "member@example.com"
        memberRepository.save(CompanyMember(acme.id, newUser(member), CompanyRole.MEMBER))
        invite(maria, acme to CompanyRole.MEMBER)

        assertRefused("already_member", acme) { invite(member, acme to CompanyRole.ADMIN) }
        assertRefused("already_member", acme) { invite("owner@example.com", acme to CompanyRole.ADMIN) }
        assertRefused("already_invited", acme) { invite(maria, acme to CompanyRole.ADMIN) }
    }

    @Test
    fun `inviting is all or nothing`() {
        invite(maria, globex to CompanyRole.MEMBER)

        assertRefused(
            "already_invited",
            globex,
        ) { invite(maria, acme to CompanyRole.MEMBER, globex to CompanyRole.ADMIN) }

        assertEquals(listOf("Globex"), openNames(maria))
    }

    @Test
    fun `once an invitation has expired, the person can be invited again`() {
        val first = invite(maria, acme to CompanyRole.MEMBER).single()
        expire(first)

        val second = invite(maria, acme to CompanyRole.ADMIN).single()

        assertEquals(InvitationStatus.EXPIRED, invitationRepository.findById(first.id).get().status)
        assertEquals(listOf(second.id), invitationService.openForUser(newUser(maria)).map { it.invitation.id })
    }

    @Test
    fun `an inviter can't send more than their daily limit, across companies`() {
        // 99 sent today already, by the same person
        limits.reserve(owner, "earlier@example.com", 99)

        invite(maria, acme to CompanyRole.MEMBER)
        val refused =
            assertFailsWith<InvitationRefusedException> {
                invite(
                    "john@example.com",
                    globex to CompanyRole.MEMBER,
                )
            }

        assertEquals("daily_limit", refused.reason)
        assertEquals(listOf(), openNames("john@example.com"))
    }

    @Test
    fun `an address can't get more than its daily limit of invitation emails, whoever sends them`() {
        repeat(10) { limits.reserve(newUser("sender$it@example.com"), maria, 1) }

        val refused = assertFailsWith<InvitationRefusedException> { invite(maria, acme to CompanyRole.MEMBER) }

        assertEquals("recipient_daily_limit", refused.reason)
        assertEquals(listOf(), emailPort.invitationEmails)
    }

    @Test
    fun `if the email can't be sent, nothing is saved or counted`() {
        emailPort.failure = EmailDeliveryException("SMTP down")

        assertFailsWith<EmailDeliveryException> { invite(maria, acme to CompanyRole.MEMBER) }
        emailPort.failure = null

        assertEquals(listOf(), openNames(maria))
        // Nothing was used up: the full allowance is still there
        limits.reserve(owner, maria, 100)
    }

    @Test
    fun `the email signs the person in on their Invitations page`() {
        invite(maria, acme to CompanyRole.MEMBER)

        val sent = emailPort.invitationEmails.single()

        assertEquals(
            "%2Finvitations",
            UriComponentsBuilder
                .fromUri(sent.link)
                .build()
                .queryParams
                .getFirst("redirectTo"),
        )
        assertEquals(maria, magicLinkService.verifyToken(sent.rawToken, Language.EN)?.email)
    }

    @Test
    fun `accepting makes the person a member with the invited role`() {
        val invitation = invite(maria, acme to CompanyRole.ADMIN).single()
        val mariaId = newUser(maria)

        val joined = invitationService.accept(mariaId, invitation.id)

        assertEquals(CompanyRole.ADMIN, joined.role)
        assertEquals(CompanyRole.ADMIN, accessService.resolve(mariaId, acme.id).role)
        assertEquals(InvitationStatus.ACCEPTED, invitationRepository.findById(invitation.id).get().status)
        assertEquals(listOf("invitation.sent", "invitation.accepted"), actionsFor(invitation))
        assertEquals(
            "member.added",
            auditEventRepository.findByTargetTypeAndTargetIdOrderByOccurredAtAscIdAsc("user", mariaId).last().action,
        )
        assertEquals(listOf(), openNames(maria))
    }

    @Test
    fun `only the invited address can accept or decline, and only once`() {
        val invitation = invite(maria, acme to CompanyRole.MEMBER).single()
        val someoneElse = newUser("john@example.com")

        assertFailsWith<InvitationNotFoundException> { invitationService.accept(someoneElse, invitation.id) }
        assertFailsWith<InvitationNotFoundException> { invitationService.decline(someoneElse, invitation.id) }

        val mariaId = newUser(maria)
        invitationService.accept(mariaId, invitation.id)
        assertFailsWith<InvitationNotFoundException> { invitationService.accept(mariaId, invitation.id) }
    }

    @Test
    fun `an expired invitation can't be accepted`() {
        val invitation = invite(maria, acme to CompanyRole.MEMBER).single()
        expire(invitation)

        assertFailsWith<InvitationNotFoundException> { invitationService.accept(newUser(maria), invitation.id) }
    }

    @Test
    fun `a suspended company takes nobody new`() {
        val invitation = invite(maria, acme to CompanyRole.MEMBER).single()
        companyRepository.save(
            companyRepository.findById(acme.id).get().apply { changeStatus(CompanyStatus.SUSPENDED) },
        )

        assertFailsWith<CompanySuspendedException> { invitationService.accept(newUser(maria), invitation.id) }
    }

    @Test
    fun `declining closes the invitation`() {
        val invitation = invite(maria, acme to CompanyRole.MEMBER).single()

        invitationService.decline(newUser(maria), invitation.id)

        assertEquals(InvitationStatus.DECLINED, invitationRepository.findById(invitation.id).get().status)
        assertEquals(listOf("invitation.sent", "invitation.declined"), actionsFor(invitation))
    }

    @Test
    fun `revoking closes the invitation, for the owner and admins of that company only`() {
        val invitation = invite(maria, acme to CompanyRole.MEMBER).single()

        assertFailsWith<InvitationNotFoundException> {
            invitationService.revoke(accessService.resolve(owner, globex.id), invitation.id)
        }
        invitationService.revoke(accessService.resolve(owner, acme.id), invitation.id)

        assertEquals(listOf(), openNames(maria))
        assertEquals(listOf("invitation.sent", "invitation.revoked"), actionsFor(invitation))
        assertFailsWith<InvitationNotFoundException> { invitationService.accept(newUser(maria), invitation.id) }
    }

    @Test
    fun `a company lists its open invitations`() {
        invite(maria, acme to CompanyRole.MEMBER)
        expire(invite("old@example.com", acme to CompanyRole.MEMBER).single())

        val open = invitationService.openFor(accessService.resolve(owner, acme.id))

        assertEquals(listOf(maria), open.map { it.invitation.email })
        assertTrue(open.single().invitedByEmail == "owner@example.com")
    }

    private fun invite(
        email: String,
        vararg targets: Pair<Company, CompanyRole>,
    ) = invitationService.invite(
        owner,
        email,
        targets.map { (company, role) -> InvitationTarget(accessService.resolve(owner, company.id), role) },
    )

    private fun assertRefused(
        reason: String,
        company: Company,
        block: () -> Unit,
    ) {
        val refused = assertFailsWith<InvitationRefusedException>(block = block)
        assertEquals(reason to company.id, refused.reason to refused.companyId)
    }

    private fun openNames(email: String) = invitationService.openForUser(newUser(email)).map { it.company.name }

    private fun actionsFor(invitation: Invitation) =
        auditEventRepository
            .findByTargetTypeAndTargetIdOrderByOccurredAtAscIdAsc(
                "invitation",
                invitation.id,
            ).map { it.action }

    private fun expire(invitation: Invitation) {
        jdbcTemplate.update(
            "UPDATE invitations SET expires_at = now() - interval '1 minute' WHERE id = ?",
            invitation.id,
        )
    }

    private fun newUser(email: String) = userService.getOrCreateUser(email, Language.EN).id
}
