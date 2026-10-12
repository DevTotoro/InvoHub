package com.invohub.backend.domain.invitation

import com.invohub.backend.domain.audit.AuditAction
import com.invohub.backend.domain.audit.AuditEvent
import com.invohub.backend.domain.audit.AuditLog
import com.invohub.backend.domain.audit.AuditTarget
import com.invohub.backend.domain.common.InvalidInputException
import com.invohub.backend.domain.company.Company
import com.invohub.backend.domain.company.CompanyAccess
import com.invohub.backend.domain.company.CompanyMember
import com.invohub.backend.domain.company.CompanyMemberId
import com.invohub.backend.domain.company.CompanyMemberRepository
import com.invohub.backend.domain.company.CompanyPermission
import com.invohub.backend.domain.company.CompanyReadOnlyException
import com.invohub.backend.domain.company.CompanyRepository
import com.invohub.backend.domain.company.CompanyRole
import com.invohub.backend.domain.company.CompanyStatus
import com.invohub.backend.domain.company.CompanySuspendedException
import com.invohub.backend.domain.company.UserCompany
import com.invohub.backend.domain.user.User
import com.invohub.backend.domain.user.UserRepository
import com.invohub.backend.domain.user.normalizeEmail
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

/** One company to invite to, as the inviter's access to it, with the role to give */
data class InvitationTarget(
    val access: CompanyAccess,
    val role: CompanyRole,
)

/** An invitation with what a page shows about it */
data class InvitationDetails(
    val invitation: Invitation,
    val company: Company,
    val invitedByEmail: String,
)

/** Published once invitations are saved; the email about them goes out after the commit */
data class InvitationsCreated(
    val email: String,
)

/**
 * Inviting people to companies, and accepting or declining as the invitee. Inviting checks every company
 * against the inviter's access and the limits, and saves all or nothing.
 */
@Service
class InvitationService(
    private val invitationRepository: InvitationRepository,
    private val companyRepository: CompanyRepository,
    private val memberRepository: CompanyMemberRepository,
    private val userRepository: UserRepository,
    private val properties: InvitationProperties,
    private val auditLog: AuditLog,
    private val events: ApplicationEventPublisher,
) {
    companion object {
        private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
        private val DAY = Duration.ofDays(1)
    }

    /**
     * @throws InvalidInputException for a malformed email ("email", "invalid"), no companies ("companies",
     * "required") or the owner role ("role", "owner")
     * @throws InvitationRefusedException when someone is already in a company or invited to it, or a limit
     * is reached
     */
    @Transactional
    fun invite(
        inviterUserId: Long,
        rawEmail: String,
        targets: List<InvitationTarget>,
    ): List<Invitation> {
        val email = normalizeEmail(rawEmail)
        if (!EMAIL.matches(email)) throw InvalidInputException("email", "invalid")
        val distinct = targets.distinctBy { it.access.companyId }
        if (distinct.isEmpty()) throw InvalidInputException("companies", "required")

        val now = Instant.now()
        val invitedUser = userRepository.findByEmail(email)
        val sentToday = invitationRepository.countByInvitedByUserIdAndCreatedAtAfter(inviterUserId, now - DAY)
        if (sentToday + distinct.size > properties.maxPerInviterPerDay) {
            throw InvitationRefusedException(null, "daily_limit")
        }

        val invitations =
            distinct.map { (access, role) ->
                access.require(CompanyPermission.MANAGE_MEMBERS)
                if (role == CompanyRole.OWNER) throw InvalidInputException("role", "owner")
                checkCanInvite(access.company, email, invitedUser, now)
                Invitation(access.companyId, email, role, inviterUserId, now + properties.expiry)
            }
        invitationRepository.saveAll(invitations)

        invitations.forEach {
            auditLog.record(
                AuditEvent(
                    AuditAction.INVITATION_SENT,
                    companyId = it.companyId,
                    target = AuditTarget.invitation(it.id),
                    details = mapOf("email" to email, "role" to it.role.name),
                ),
            )
        }
        events.publishEvent(InvitationsCreated(email))
        return invitations
    }

    /** The company's open invitations, for its owner and admins */
    @Transactional(readOnly = true)
    fun openFor(access: CompanyAccess): List<InvitationDetails> {
        access.require(CompanyPermission.MANAGE_MEMBERS)
        val open =
            invitationRepository.findByCompanyIdAndStatusAndExpiresAtAfterOrderByCreatedAt(
                access.companyId,
                InvitationStatus.PENDING,
                Instant.now(),
            )
        return details(open)
    }

    /** @throws InvitationNotFoundException unless it is an open invitation to this company */
    @Transactional
    fun revoke(
        access: CompanyAccess,
        invitationId: Long,
    ) {
        access.require(CompanyPermission.MANAGE_MEMBERS)
        val invitation =
            invitationRepository
                .findById(invitationId)
                .orElse(null)
                ?.takeIf { it.companyId == access.companyId && it.isOpen(Instant.now()) }
                ?: throw InvitationNotFoundException()

        invitation.close(InvitationStatus.REVOKED, Instant.now())
        invitationRepository.saveAndFlush(invitation)
        auditLog.record(
            AuditEvent(
                AuditAction.INVITATION_REVOKED,
                companyId = invitation.companyId,
                target = AuditTarget.invitation(invitation.id),
                details = mapOf("email" to invitation.email),
            ),
        )
    }

    /** The user's open invitations: those for their email address */
    @Transactional(readOnly = true)
    fun openForUser(userId: Long): List<InvitationDetails> {
        val user = userRepository.findById(userId).orElseThrow()
        val open =
            invitationRepository.findByEmailAndStatusAndExpiresAtAfterOrderByCreatedAt(
                user.email,
                InvitationStatus.PENDING,
                Instant.now(),
            )
        return details(open)
    }

    /**
     * Makes the user a member with the invited role.
     * @throws InvitationNotFoundException unless it is an open invitation for the user's email address
     * @throws CompanySuspendedException, CompanyReadOnlyException: only an active company takes new people
     */
    @Transactional
    fun accept(
        userId: Long,
        invitationId: Long,
    ): UserCompany {
        val user = userRepository.findById(userId).orElseThrow()
        val invitation = openInvitationOf(user, invitationId)
        val company = companyRepository.findById(invitation.companyId).orElseThrow()
        when (company.status) {
            CompanyStatus.SUSPENDED -> throw CompanySuspendedException()
            CompanyStatus.READ_ONLY -> throw CompanyReadOnlyException()
            CompanyStatus.ACTIVE -> {}
        }

        invitation.close(InvitationStatus.ACCEPTED, Instant.now())
        invitationRepository.saveAndFlush(invitation)
        auditLog.record(
            AuditEvent(
                AuditAction.INVITATION_ACCEPTED,
                companyId = company.id,
                target = AuditTarget.invitation(invitation.id),
            ),
        )

        // Already in the company (say, added through another invitation meanwhile): nothing more to do
        if (company.ownerUserId == userId) return UserCompany(company, CompanyRole.OWNER)
        memberRepository.findById(CompanyMemberId(company.id, userId)).orElse(null)?.let {
            return UserCompany(company, it.role)
        }

        memberRepository.save(CompanyMember(company.id, userId, invitation.role))
        auditLog.record(
            AuditEvent(
                AuditAction.MEMBER_ADDED,
                companyId = company.id,
                target = AuditTarget.user(userId),
                details = mapOf("role" to invitation.role.name, "invitation" to invitation.getPublicId()),
            ),
        )
        return UserCompany(company, invitation.role)
    }

    /** @throws InvitationNotFoundException unless it is an open invitation for the user's email address */
    @Transactional
    fun decline(
        userId: Long,
        invitationId: Long,
    ) {
        val invitation = openInvitationOf(userRepository.findById(userId).orElseThrow(), invitationId)
        invitation.close(InvitationStatus.DECLINED, Instant.now())
        invitationRepository.saveAndFlush(invitation)
        auditLog.record(
            AuditEvent(
                AuditAction.INVITATION_DECLINED,
                companyId = invitation.companyId,
                target = AuditTarget.invitation(invitation.id),
            ),
        )
    }

    private fun checkCanInvite(
        company: Company,
        email: String,
        invitedUser: User?,
        now: Instant,
    ) {
        if (invitedUser != null) {
            val inCompany =
                company.ownerUserId == invitedUser.id ||
                    memberRepository.existsById(CompanyMemberId(company.id, invitedUser.id))
            if (inCompany) throw InvitationRefusedException(company.id, "already_member")
        }

        invitationRepository.findByCompanyIdAndEmailAndStatus(company.id, email, InvitationStatus.PENDING)?.let {
            if (it.isOpen(now)) throw InvitationRefusedException(company.id, "already_invited")
            // Expired: close it, so the new one can take its place
            it.close(InvitationStatus.EXPIRED, now)
            invitationRepository.saveAndFlush(it)
        }

        if (invitationRepository.countByCompanyIdAndCreatedAtAfter(company.id, now - DAY) >=
            properties.maxPerCompanyPerDay
        ) {
            throw InvitationRefusedException(company.id, "company_daily_limit")
        }
    }

    private fun openInvitationOf(
        user: User,
        invitationId: Long,
    ): Invitation =
        invitationRepository
            .findById(invitationId)
            .orElse(null)
            ?.takeIf { it.email == user.email && it.isOpen(Instant.now()) }
            ?: throw InvitationNotFoundException()

    private fun details(invitations: List<Invitation>): List<InvitationDetails> {
        val companies = companyRepository.findAllById(invitations.map { it.companyId }.toSet()).associateBy { it.id }
        val inviters = userRepository.findAllById(invitations.map { it.invitedByUserId }.toSet()).associateBy { it.id }
        return invitations.map {
            InvitationDetails(it, companies.getValue(it.companyId), inviters.getValue(it.invitedByUserId).email)
        }
    }
}

/**
 * Inviting was refused for [companyId] (null when it isn't about one company), for [reason]: "already_member",
 * "already_invited", "company_daily_limit" or "daily_limit".
 */
class InvitationRefusedException(
    val companyId: Long?,
    val reason: String,
) : RuntimeException("Invitation refused: $reason")

/** No such open invitation, for this user or company */
class InvitationNotFoundException : RuntimeException("Invitation not found")
