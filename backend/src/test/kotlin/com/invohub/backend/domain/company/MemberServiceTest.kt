package com.invohub.backend.domain.company

import com.invohub.backend.domain.audit.AuditEventRepository
import com.invohub.backend.domain.common.InvalidInputException
import com.invohub.backend.domain.common.NotAllowedException
import com.invohub.backend.domain.user.Language
import com.invohub.backend.domain.user.UserService
import com.invohub.backend.support.AuditEvents
import com.invohub.backend.support.IntegrationTest
import io.hypersistence.tsid.TSID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

@IntegrationTest
class MemberServiceTest {
    @Autowired
    private lateinit var memberService: MemberService

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

    private val events by lazy { AuditEvents(auditEventRepository) }

    /** A company with an owner, an admin, a member and a viewer */
    private inner class Team {
        val owner = newUser("owner")
        val company = companyService.createCompany(owner, "Acme", "GR")
        val admin = join(CompanyRole.ADMIN, "admin")
        val member = join(CompanyRole.MEMBER, "member")
        val viewer = join(CompanyRole.VIEWER, "viewer")

        fun join(
            role: CompanyRole,
            name: String,
        ) = newUser(name).also { memberRepository.save(CompanyMember(company.id, it, role)) }

        fun accessOf(userId: Long) = accessService.resolve(userId, company.id)
    }

    @Test
    fun `lists the owner first, then the members by email, to anyone in the company`() {
        val team = Team()

        val people = memberService.people(team.accessOf(team.viewer))

        assertEquals(
            listOf(CompanyRole.OWNER, CompanyRole.ADMIN, CompanyRole.MEMBER, CompanyRole.VIEWER),
            people.map { it.role },
        )
        assertEquals(team.owner, people.first().userId)
        assertEquals(people.drop(1).map { it.email }.sorted(), people.drop(1).map { it.email })
    }

    @Test
    fun `an admin changes a member's role, and it is recorded`() {
        val team = Team()

        val changed = memberService.changeRole(team.accessOf(team.admin), team.viewer, CompanyRole.MEMBER)

        assertEquals(CompanyRole.MEMBER, changed.role)
        assertEquals(CompanyRole.MEMBER, team.accessOf(team.viewer).role)
        val event =
            auditEventRepository
                .findByTargetTypeAndTargetIdOrderByOccurredAtAscIdAsc(
                    "user",
                    team.viewer,
                ).last()
        assertEquals("member.role_changed", event.action)
        assertEquals(team.company.id, event.companyId)
        assertEquals(mapOf("from" to "VIEWER", "to" to "MEMBER"), event.details)
    }

    @Test
    fun `an admin can change another admin's role`() {
        val team = Team()
        val otherAdmin = team.join(CompanyRole.ADMIN, "admin2")

        memberService.changeRole(team.accessOf(team.admin), otherAdmin, CompanyRole.VIEWER)

        assertEquals(CompanyRole.VIEWER, team.accessOf(otherAdmin).role)
    }

    @Test
    fun `setting the role someone already has records nothing`() {
        val team = Team()

        memberService.changeRole(team.accessOf(team.owner), team.member, CompanyRole.MEMBER)

        assertEquals(listOf(), events.forUser(team.member).filter { it == "member.role_changed" })
    }

    @Test
    fun `nobody can be made owner, and the owner's role can't be changed`() {
        val team = Team()

        val owner =
            assertFailsWith<InvalidInputException> {
                memberService.changeRole(team.accessOf(team.owner), team.member, CompanyRole.OWNER)
            }
        assertEquals("role" to "owner", owner.field to owner.reason)
        val ofOwner =
            assertFailsWith<NotAllowedException> {
                memberService.changeRole(team.accessOf(team.admin), team.owner, CompanyRole.VIEWER)
            }
        assertEquals("owner", ofOwner.reason)
    }

    @Test
    fun `nobody changes their own role`() {
        val team = Team()

        val self =
            assertFailsWith<NotAllowedException> {
                memberService.changeRole(team.accessOf(team.admin), team.admin, CompanyRole.VIEWER)
            }
        assertEquals("self", self.reason)
    }

    @Test
    fun `members and viewers can't manage people`() {
        val team = Team()

        assertFailsWith<CompanyPermissionDeniedException> {
            memberService.changeRole(team.accessOf(team.member), team.viewer, CompanyRole.MEMBER)
        }
        assertFailsWith<CompanyPermissionDeniedException> {
            memberService.remove(
                team.accessOf(team.viewer),
                team.member,
            )
        }
    }

    @Test
    fun `someone outside the company can't be managed through it`() {
        val team = Team()
        val stranger = newUser("stranger")

        assertFailsWith<MemberNotFoundException> {
            memberService.changeRole(team.accessOf(team.owner), stranger, CompanyRole.VIEWER)
        }
        assertFailsWith<MemberNotFoundException> { memberService.remove(team.accessOf(team.owner), stranger) }
    }

    @Test
    fun `people can't be managed while the company is read-only`() {
        val team = Team()
        companyRepository.save(
            companyRepository.findById(team.company.id).get().apply {
                changeStatus(CompanyStatus.READ_ONLY)
            },
        )

        assertFailsWith<CompanyReadOnlyException> { memberService.remove(team.accessOf(team.owner), team.member) }
    }

    @Test
    fun `a removed member loses access at once, and it is recorded`() {
        val team = Team()

        memberService.remove(team.accessOf(team.admin), team.member)

        assertFailsWith<CompanyNotFoundException> { team.accessOf(team.member) }
        assertEquals("member.removed", events.forUser(team.member).last())
    }

    @Test
    fun `the owner can't be removed, and nobody removes themselves`() {
        val team = Team()

        assertEquals(
            "owner",
            assertFailsWith<NotAllowedException> { memberService.remove(team.accessOf(team.admin), team.owner) }.reason,
        )
        assertEquals(
            "self",
            assertFailsWith<NotAllowedException> { memberService.remove(team.accessOf(team.admin), team.admin) }.reason,
        )
    }

    @Test
    fun `a member can leave, which is recorded`() {
        val team = Team()

        memberService.leave(team.accessOf(team.viewer))

        assertFailsWith<CompanyNotFoundException> { team.accessOf(team.viewer) }
        assertEquals("member.left", events.forUser(team.viewer).last())
        assertNotNull(team.accessOf(team.member))
    }

    @Test
    fun `the owner can't leave`() {
        val team = Team()

        val refused = assertFailsWith<NotAllowedException> { memberService.leave(team.accessOf(team.owner)) }

        assertEquals("owner_cannot_leave", refused.reason)
    }

    private fun newUser(name: String) = userService.getOrCreateUser("$name-${TSID.fast()}@example.com", Language.EN).id
}
