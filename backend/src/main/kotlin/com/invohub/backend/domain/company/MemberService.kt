package com.invohub.backend.domain.company

import com.invohub.backend.domain.audit.AuditAction
import com.invohub.backend.domain.audit.AuditEvent
import com.invohub.backend.domain.audit.AuditLog
import com.invohub.backend.domain.audit.AuditTarget
import com.invohub.backend.domain.common.InvalidInputException
import com.invohub.backend.domain.common.NotAllowedException
import com.invohub.backend.domain.user.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** Someone with access to a company: its owner or a member. */
data class CompanyPerson(
    val userId: Long,
    val email: String,
    val role: CompanyRole,
    /** When they got access: the company's creation for its owner */
    val since: Instant,
)

/**
 * The people of a company. Removing someone only deletes their membership: the user remains, so their past
 * work keeps their name, and their access ends with their next request (it's checked on every one).
 */
@Service
class MemberService(
    private val memberRepository: CompanyMemberRepository,
    private val userRepository: UserRepository,
    private val auditLog: AuditLog,
) {
    /** The owner first, then the members by email. Anyone in the company may see who else is in it. */
    @Transactional(readOnly = true)
    fun people(access: CompanyAccess): List<CompanyPerson> {
        access.require(CompanyPermission.VIEW)
        val company = access.company
        val members = memberRepository.findByCompanyId(company.id)
        val emails =
            userRepository.findAllById(members.map { it.userId } + company.ownerUserId).associate {
                it.id to
                    it.email
            }

        val owner =
            CompanyPerson(
                company.ownerUserId,
                emails.getValue(company.ownerUserId),
                CompanyRole.OWNER,
                company.createdAt,
            )
        val others =
            members
                .map { CompanyPerson(it.userId, emails.getValue(it.userId), it.role, it.createdAt) }
                .sortedBy { it.email }
        return listOf(owner) + others
    }

    /**
     * @throws InvalidInputException for the owner role: ownership only moves through a transfer
     * @throws NotAllowedException "owner" for the owner, "self" for the user's own role
     * @throws MemberNotFoundException if [memberUserId] isn't a member of the company
     */
    @Transactional
    fun changeRole(
        access: CompanyAccess,
        memberUserId: Long,
        role: CompanyRole,
    ): CompanyPerson {
        access.require(CompanyPermission.MANAGE_MEMBERS)
        if (role == CompanyRole.OWNER) throw InvalidInputException("role", "owner")
        val member = otherMember(access, memberUserId)

        val previous = member.role
        if (previous != role) {
            member.changeRole(role)
            memberRepository.saveAndFlush(member)
            auditLog.record(
                AuditEvent(
                    AuditAction.MEMBER_ROLE_CHANGED,
                    companyId = access.companyId,
                    target = AuditTarget.user(memberUserId),
                    details = mapOf("from" to previous.name, "to" to role.name),
                ),
            )
        }
        val email = userRepository.findById(memberUserId).orElseThrow().email
        return CompanyPerson(memberUserId, email, member.role, member.createdAt)
    }

    /**
     * @throws NotAllowedException "owner" for the owner, "self" for the user themselves (they leave instead)
     * @throws MemberNotFoundException if [memberUserId] isn't a member of the company
     */
    @Transactional
    fun remove(
        access: CompanyAccess,
        memberUserId: Long,
    ) {
        access.require(CompanyPermission.MANAGE_MEMBERS)
        val member = otherMember(access, memberUserId)

        memberRepository.delete(member)
        auditLog.record(
            AuditEvent(
                AuditAction.MEMBER_REMOVED,
                companyId = access.companyId,
                target = AuditTarget.user(memberUserId),
                details = mapOf("role" to member.role.name),
            ),
        )
    }

    /** @throws NotAllowedException "owner_cannot_leave": the owner transfers ownership first */
    @Transactional
    fun leave(access: CompanyAccess) {
        access.require(CompanyPermission.VIEW)
        if (access.role == CompanyRole.OWNER) throw NotAllowedException("owner_cannot_leave")
        val member = memberRepository.findById(CompanyMemberId(access.companyId, access.userId)).orElseThrow()

        memberRepository.delete(member)
        auditLog.record(
            AuditEvent(
                AuditAction.MEMBER_LEFT,
                companyId = access.companyId,
                target = AuditTarget.user(access.userId),
                details = mapOf("role" to member.role.name),
            ),
        )
    }

    /** Someone else's membership in the company: never the owner's (who isn't a member) or the user's own */
    private fun otherMember(
        access: CompanyAccess,
        memberUserId: Long,
    ): CompanyMember {
        if (memberUserId == access.company.ownerUserId) throw NotAllowedException("owner")
        if (memberUserId == access.userId) throw NotAllowedException("self")
        return memberRepository.findById(CompanyMemberId(access.companyId, memberUserId)).orElse(null)
            ?: throw MemberNotFoundException()
    }
}

/** The user isn't a member of the company (anymore) */
class MemberNotFoundException : RuntimeException("Member not found")
