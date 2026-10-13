package com.invohub.backend.domain.company

import com.invohub.backend.domain.audit.AuditEventRepository
import com.invohub.backend.domain.auth.EmailDeliveryException
import com.invohub.backend.domain.common.NotAllowedException
import com.invohub.backend.domain.invitation.InvitationService
import com.invohub.backend.domain.invitation.InvitationTarget
import com.invohub.backend.domain.user.Language
import com.invohub.backend.domain.user.UserService
import com.invohub.backend.support.AuditEvents
import com.invohub.backend.support.CapturingEmailPort
import com.invohub.backend.support.IntegrationTest
import io.hypersistence.tsid.TSID
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@IntegrationTest
class OwnershipServiceTest {
    @Autowired
    private lateinit var ownershipService: OwnershipService

    @Autowired
    private lateinit var accessService: CompanyAccessService

    @Autowired
    private lateinit var companyService: CompanyService

    @Autowired
    private lateinit var companyRepository: CompanyRepository

    @Autowired
    private lateinit var memberRepository: CompanyMemberRepository

    @Autowired
    private lateinit var memberService: MemberService

    @Autowired
    private lateinit var settingsService: CompanySettingsService

    @Autowired
    private lateinit var invitationService: InvitationService

    @Autowired
    private lateinit var userService: UserService

    @Autowired
    private lateinit var auditEventRepository: AuditEventRepository

    @Autowired
    private lateinit var emailPort: CapturingEmailPort

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    private val events by lazy { AuditEvents(auditEventRepository) }

    /** A company with an owner, an admin and a member */
    private inner class Team {
        val owner = newUser("owner")
        val company = companyService.createCompany(owner, "Acme", "GR")
        val admin = join(CompanyRole.ADMIN, "admin")
        val member = join(CompanyRole.MEMBER, "member", Language.EL)

        fun join(
            role: CompanyRole,
            name: String,
            language: Language = Language.EN,
        ) = newUser(name, language).also { memberRepository.save(CompanyMember(company.id, it, role)) }

        fun accessOf(userId: Long) = accessService.resolve(userId, company.id)

        fun reload() = companyRepository.findById(company.id).get()
    }

    @BeforeEach
    fun reset() {
        emailPort.clear()
        // Invitation limits are counted in Redis
        redisTemplate.connectionFactory!!.connection.use { it.serverCommands().flushAll() }
    }

    @Test
    fun `the member accepts, becomes the owner, and the old owner stays on as an admin`() {
        val team = Team()

        val offer = ownershipService.offer(team.accessOf(team.owner), team.member)
        val email = emailPort.ownershipOffers.single()
        assertEquals("Acme" to Language.EL, email.companyName to email.language)
        assertTrue(email.link.toString().contains("redirectTo=%2Finvitations"), email.link.toString())
        assertEquals(listOf(offer.transfer.id), ownershipService.openForUser(team.member).map { it.transfer.id })

        val owned = ownershipService.accept(team.member, offer.transfer.id)

        assertEquals(CompanyRole.OWNER, owned.role)
        assertEquals(team.member, team.reload().ownerUserId)
        assertEquals(CompanyRole.OWNER, team.accessOf(team.member).role)
        assertEquals(CompanyRole.ADMIN, team.accessOf(team.owner).role)
        assertFalse(memberRepository.existsById(CompanyMemberId(team.company.id, team.member)))
        assertEquals(emptyList(), ownershipService.openForUser(team.member))
        val transferred =
            auditEventRepository
                .findByTargetTypeAndTargetIdOrderByOccurredAtAscIdAsc("company", team.company.id)
                .last()
        assertEquals("company.ownership_transferred", transferred.action)
        assertEquals(setOf("from", "to"), transferred.details.keys)
    }

    @Test
    fun `the member can decline, and the owner can cancel`() {
        val team = Team()
        val declined = ownershipService.offer(team.accessOf(team.owner), team.member)
        ownershipService.decline(team.member, declined.transfer.id)
        assertNull(ownershipService.pendingFor(team.accessOf(team.owner)))
        assertFailsWith<OwnershipTransferNotFoundException> {
            ownershipService.accept(
                team.member,
                declined.transfer.id,
            )
        }

        val cancelled = ownershipService.offer(team.accessOf(team.owner), team.admin)
        assertEquals(cancelled.transfer.id, ownershipService.pendingFor(team.accessOf(team.owner))?.transfer?.id)
        ownershipService.cancel(team.accessOf(team.owner))

        assertFailsWith<OwnershipTransferNotFoundException> {
            ownershipService.accept(
                team.admin,
                cancelled.transfer.id,
            )
        }
        assertEquals(team.owner, team.reload().ownerUserId)
    }

    @Test
    fun `only to a member, one offer at a time, and only by the owner`() {
        val team = Team()
        val outsider = newUser("outsider")

        assertFailsWith<MemberNotFoundException> { ownershipService.offer(team.accessOf(team.owner), outsider) }
        assertNotAllowed("self") { ownershipService.offer(team.accessOf(team.owner), team.owner) }
        assertFailsWith<CompanyPermissionDeniedException> {
            ownershipService.offer(
                team.accessOf(team.admin),
                team.member,
            )
        }

        ownershipService.offer(team.accessOf(team.owner), team.member)
        assertNotAllowed("transfer_pending") { ownershipService.offer(team.accessOf(team.owner), team.admin) }
    }

    @Test
    fun `an expired offer can't be accepted, and makes room for a new one`() {
        val team = Team()
        val expired = ownershipService.offer(team.accessOf(team.owner), team.member)
        jdbcTemplate.update(
            "UPDATE ownership_transfers SET expires_at = now() - interval '1 minute' WHERE id = ?",
            expired.transfer.id,
        )

        assertFailsWith<OwnershipTransferNotFoundException> {
            ownershipService.accept(
                team.member,
                expired.transfer.id,
            )
        }
        assertNull(ownershipService.pendingFor(team.accessOf(team.owner)))
        ownershipService.offer(team.accessOf(team.owner), team.admin)
    }

    @Test
    fun `only the recipient can accept`() {
        val team = Team()
        val offer = ownershipService.offer(team.accessOf(team.owner), team.member)

        assertFailsWith<OwnershipTransferNotFoundException> { ownershipService.accept(team.admin, offer.transfer.id) }
        assertFailsWith<OwnershipTransferNotFoundException> { ownershipService.decline(team.admin, offer.transfer.id) }
    }

    @Test
    fun `an offer to someone who leaves or is removed is cancelled`() {
        val team = Team()
        val toMember = ownershipService.offer(team.accessOf(team.owner), team.member)
        memberService.remove(team.accessOf(team.admin), team.member)
        assertFailsWith<OwnershipTransferNotFoundException> {
            ownershipService.accept(
                team.member,
                toMember.transfer.id,
            )
        }
        assertNull(ownershipService.pendingFor(team.accessOf(team.owner)))

        val toAdmin = ownershipService.offer(team.accessOf(team.owner), team.admin)
        memberService.leave(team.accessOf(team.admin))
        assertFailsWith<OwnershipTransferNotFoundException> { ownershipService.accept(team.admin, toAdmin.transfer.id) }
    }

    @Test
    fun `an email that can't be sent saves no offer`() {
        val team = Team()
        emailPort.failure = EmailDeliveryException("SMTP down")

        assertFailsWith<EmailDeliveryException> { ownershipService.offer(team.accessOf(team.owner), team.member) }
        emailPort.failure = null
        assertNull(ownershipService.pendingFor(team.accessOf(team.owner)))
    }

    @Test
    fun `a read-only company can still be handed over`() {
        val team = Team()
        companyRepository.save(team.reload().apply { changeStatus(CompanyStatus.READ_ONLY) })

        val offer = ownershipService.offer(team.accessOf(team.owner), team.member)
        ownershipService.accept(team.member, offer.transfer.id)

        assertEquals(team.member, team.reload().ownerUserId)
    }

    @Test
    fun `closing keeps everything, read-only, until the owner reopens`() {
        val team = Team()
        invitationService.invite(
            team.owner,
            "new@example.com",
            listOf(InvitationTarget(team.accessOf(team.owner), CompanyRole.MEMBER)),
        )

        val closed = ownershipService.close(team.accessOf(team.owner))

        assertNotNull(closed.closedAt)
        // Still there for everyone in it to look at
        assertEquals(4, memberService.people(team.accessOf(team.member)).size)
        assertFailsWith<CompanyClosedException> {
            settingsService.updateProfile(team.accessOf(team.owner), "Acme 2", "GR")
        }
        assertFailsWith<CompanyClosedException> { memberService.remove(team.accessOf(team.admin), team.member) }
        // Its invitations are revoked
        assertEquals(emptyList(), invitationService.openForUser(newUser("new", email = "new@example.com")))
        assertFailsWith<CompanyPermissionDeniedException> { ownershipService.reopen(team.accessOf(team.admin)) }

        ownershipService.reopen(team.accessOf(team.owner))

        assertNull(team.reload().closedAt)
        settingsService.updateProfile(team.accessOf(team.owner), "Acme 2", "GR")
        assertEquals(
            listOf("company.closed", "company.reopened"),
            events.forCompany(team.company.id).filter { it == "company.closed" || it == "company.reopened" },
        )
    }

    @Test
    fun `a closed company can still be handed over, and takes nobody new`() {
        val team = Team()
        val newcomer = newUser("newcomer")
        val invitation =
            invitationService
                .invite(
                    team.owner,
                    userEmail(newcomer),
                    listOf(InvitationTarget(team.accessOf(team.owner), CompanyRole.MEMBER)),
                ).single()
        val offer = ownershipService.offer(team.accessOf(team.owner), team.member)
        ownershipService.close(team.accessOf(team.owner))

        assertFailsWith<com.invohub.backend.domain.invitation.InvitationNotFoundException> {
            invitationService.accept(newcomer, invitation.id)
        }
        ownershipService.accept(team.member, offer.transfer.id)
        assertEquals(team.member, team.reload().ownerUserId)
        assertTrue(team.reload().closed)
    }

    private fun userEmail(userId: Long) =
        jdbcTemplate.queryForObject("SELECT email FROM users WHERE id = ?", String::class.java, userId)!!

    private fun newUser(
        name: String,
        language: Language = Language.EN,
        email: String = "$name-${TSID.fast()}@example.com",
    ) = userService.getOrCreateUser(email, language).id

    private fun assertNotAllowed(
        reason: String,
        action: () -> Unit,
    ) {
        val e = assertFailsWith<NotAllowedException> { action() }
        assertEquals(reason, e.reason)
    }
}
