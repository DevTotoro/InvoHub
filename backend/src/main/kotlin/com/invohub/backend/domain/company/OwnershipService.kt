package com.invohub.backend.domain.company

import com.invohub.backend.domain.audit.AuditAction
import com.invohub.backend.domain.audit.AuditEvent
import com.invohub.backend.domain.audit.AuditLog
import com.invohub.backend.domain.audit.AuditTarget
import com.invohub.backend.domain.auth.EmailDeliveryException
import com.invohub.backend.domain.auth.EmailPort
import com.invohub.backend.domain.auth.MagicLinkService
import com.invohub.backend.domain.common.NotAllowedException
import com.invohub.backend.domain.invitation.InvitationRepository
import com.invohub.backend.domain.invitation.InvitationStatus
import com.invohub.backend.domain.user.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

/** A transfer with what a page shows about it */
data class OwnershipTransferDetails(
    val transfer: OwnershipTransfer,
    val company: Company,
    val fromEmail: String,
    val toEmail: String,
)

/**
 * What only the owner decides: who owns the company, and whether it is closed.
 *
 * Ownership moves only once the recipient, a member of the company, accepts; the old owner then stays on as an
 * admin. Closing deletes nothing: the company becomes read-only for its people to look at and export, and the
 * owner can reopen it whenever they like.
 */
@Service
class OwnershipService(
    private val transferRepository: OwnershipTransferRepository,
    private val companyRepository: CompanyRepository,
    private val memberRepository: CompanyMemberRepository,
    private val invitationRepository: InvitationRepository,
    private val userRepository: UserRepository,
    private val magicLinkService: MagicLinkService,
    private val emailPort: EmailPort,
    private val auditLog: AuditLog,
) {
    companion object {
        val OPEN_FOR: Duration = Duration.ofDays(7)
    }

    /**
     * Offers ownership to [toUserId] and emails them about it.
     * @throws NotAllowedException "self" for the owner themselves, "transfer_pending" while another offer is open
     * @throws MemberNotFoundException if [toUserId] isn't a member of the company
     * @throws EmailDeliveryException if the email couldn't be sent; nothing is saved
     */
    @Transactional
    fun offer(
        access: CompanyAccess,
        toUserId: Long,
    ): OwnershipTransferDetails {
        access.require(CompanyPermission.MANAGE_OWNERSHIP)
        if (toUserId == access.userId) throw NotAllowedException("self")
        if (!memberRepository.existsById(CompanyMemberId(access.companyId, toUserId))) throw MemberNotFoundException()
        val now = Instant.now()
        transferRepository.findByCompanyIdAndStatus(access.companyId, OwnershipTransferStatus.PENDING)?.let {
            if (it.isOpen(now)) throw NotAllowedException("transfer_pending")
            // Expired: close it, so the new one can take its place
            it.close(OwnershipTransferStatus.EXPIRED, now)
            transferRepository.saveAndFlush(it)
        }

        val transfer =
            transferRepository.saveAndFlush(
                OwnershipTransfer(
                    access.companyId,
                    access.userId,
                    toUserId,
                    now + OPEN_FOR,
                ),
            )
        val from = userRepository.findById(access.userId).orElseThrow()
        val to = userRepository.findById(toUserId).orElseThrow()
        auditLog.record(
            AuditEvent(
                AuditAction.OWNERSHIP_OFFERED,
                companyId = access.companyId,
                target = AuditTarget.ownershipTransfer(transfer.id),
                details = mapOf("to" to to.email),
            ),
        )
        // Last, so a failed email rolls everything back and the owner can try again
        emailPort.sendOwnershipOffer(
            to.email,
            access.company.name,
            from.email,
            magicLinkService.invitationLink(to.email),
            to.language,
        )
        return OwnershipTransferDetails(transfer, access.company, from.email, to.email)
    }

    /** The company's open offer, if any, for its owner */
    @Transactional(readOnly = true)
    fun pendingFor(access: CompanyAccess): OwnershipTransferDetails? {
        access.require(CompanyPermission.MANAGE_OWNERSHIP)
        return transferRepository
            .findByCompanyIdAndStatus(access.companyId, OwnershipTransferStatus.PENDING)
            ?.takeIf { it.isOpen(Instant.now()) }
            ?.let { details(listOf(it)).single() }
    }

    /** @throws OwnershipTransferNotFoundException without an open offer */
    @Transactional
    fun cancel(access: CompanyAccess) {
        access.require(CompanyPermission.MANAGE_OWNERSHIP)
        val transfer =
            transferRepository
                .findByCompanyIdAndStatus(access.companyId, OwnershipTransferStatus.PENDING)
                ?.takeIf { it.isOpen(Instant.now()) }
                ?: throw OwnershipTransferNotFoundException()
        closeTransfer(transfer, OwnershipTransferStatus.CANCELLED, AuditAction.OWNERSHIP_OFFER_CANCELLED)
    }

    /** Ownership offered to the user, still open */
    @Transactional(readOnly = true)
    fun openForUser(userId: Long): List<OwnershipTransferDetails> =
        details(
            transferRepository.findByToUserIdAndStatusAndExpiresAtAfterOrderByCreatedAt(
                userId,
                OwnershipTransferStatus.PENDING,
                Instant.now(),
            ),
        )

    /**
     * Makes the user the owner; the old owner becomes an admin.
     * @throws OwnershipTransferNotFoundException unless it is an open offer to the user, from the current owner
     * @throws CompanySuspendedException if the company is suspended
     */
    @Transactional
    fun accept(
        userId: Long,
        transferId: Long,
    ): UserCompany {
        val transfer = openTransferTo(userId, transferId)
        val company = companyRepository.findById(transfer.companyId).orElseThrow()
        if (company.status == CompanyStatus.SUSPENDED) throw CompanySuspendedException()
        val membership = memberRepository.findById(CompanyMemberId(company.id, userId)).orElse(null)
        // Ownership moved meanwhile, or the user is no longer in the company: the offer means nothing now
        if (company.ownerUserId != transfer.fromUserId || membership == null) throw OwnershipTransferNotFoundException()

        memberRepository.delete(membership)
        memberRepository.flush()
        company.transferOwnership(userId)
        companyRepository.saveAndFlush(company)
        memberRepository.save(CompanyMember(company.id, transfer.fromUserId, CompanyRole.ADMIN))
        transfer.close(OwnershipTransferStatus.ACCEPTED, Instant.now())
        transferRepository.saveAndFlush(transfer)

        val emails = userRepository.findAllById(listOf(transfer.fromUserId, userId)).associate { it.id to it.email }
        auditLog.record(
            AuditEvent(
                AuditAction.COMPANY_OWNERSHIP_TRANSFERRED,
                companyId = company.id,
                target = AuditTarget.company(company.id),
                details = mapOf("from" to emails.getValue(transfer.fromUserId), "to" to emails.getValue(userId)),
            ),
        )
        return UserCompany(company, CompanyRole.OWNER)
    }

    /** @throws OwnershipTransferNotFoundException unless it is an open offer to the user */
    @Transactional
    fun decline(
        userId: Long,
        transferId: Long,
    ) {
        closeTransfer(
            openTransferTo(userId, transferId),
            OwnershipTransferStatus.DECLINED,
            AuditAction.OWNERSHIP_OFFER_DECLINED,
        )
    }

    /** Called when someone leaves or is removed: an offer to them can no longer be accepted */
    @Transactional
    fun cancelOfferTo(
        companyId: Long,
        userId: Long,
    ) {
        transferRepository
            .findByCompanyIdAndStatus(companyId, OwnershipTransferStatus.PENDING)
            ?.takeIf { it.toUserId == userId }
            ?.let { closeTransfer(it, OwnershipTransferStatus.CANCELLED, AuditAction.OWNERSHIP_OFFER_CANCELLED) }
    }

    /**
     * Closes the company: read-only from now on, and its open invitations are revoked. An open ownership offer
     * stays: a closed company can still be handed over. Closing a closed company does nothing.
     */
    @Transactional
    fun close(access: CompanyAccess): Company {
        access.require(CompanyPermission.MANAGE_OWNERSHIP)
        val company = companyRepository.findById(access.companyId).orElseThrow()
        if (company.closed) return company
        val now = Instant.now()
        company.close(now)
        companyRepository.saveAndFlush(company)
        invitationRepository
            .findByCompanyIdAndStatusAndExpiresAtAfterOrderByCreatedAt(company.id, InvitationStatus.PENDING, now)
            .forEach {
                it.close(InvitationStatus.REVOKED, now)
                invitationRepository.save(it)
                auditLog.record(
                    AuditEvent(
                        AuditAction.INVITATION_REVOKED,
                        companyId = company.id,
                        target = AuditTarget.invitation(it.id),
                        details = mapOf("email" to it.email),
                    ),
                )
            }
        auditLog.record(
            AuditEvent(AuditAction.COMPANY_CLOSED, companyId = company.id, target = AuditTarget.company(company.id)),
        )
        return company
    }

    /** Back to how it was before closing. Reopening an open company does nothing. */
    @Transactional
    fun reopen(access: CompanyAccess): Company {
        access.require(CompanyPermission.MANAGE_OWNERSHIP)
        val company = companyRepository.findById(access.companyId).orElseThrow()
        if (!company.closed) return company
        company.reopen()
        companyRepository.saveAndFlush(company)
        auditLog.record(
            AuditEvent(AuditAction.COMPANY_REOPENED, companyId = company.id, target = AuditTarget.company(company.id)),
        )
        return company
    }

    private fun openTransferTo(
        userId: Long,
        transferId: Long,
    ): OwnershipTransfer =
        transferRepository
            .findById(transferId)
            .orElse(null)
            ?.takeIf { it.toUserId == userId && it.isOpen(Instant.now()) }
            ?: throw OwnershipTransferNotFoundException()

    private fun closeTransfer(
        transfer: OwnershipTransfer,
        status: OwnershipTransferStatus,
        action: AuditAction,
    ) {
        transfer.close(status, Instant.now())
        transferRepository.saveAndFlush(transfer)
        auditLog.record(
            AuditEvent(action, companyId = transfer.companyId, target = AuditTarget.ownershipTransfer(transfer.id)),
        )
    }

    private fun details(transfers: List<OwnershipTransfer>): List<OwnershipTransferDetails> {
        val companies = companyRepository.findAllById(transfers.map { it.companyId }.toSet()).associateBy { it.id }
        val emails =
            userRepository
                .findAllById(transfers.flatMap { listOf(it.fromUserId, it.toUserId) }.toSet())
                .associate { it.id to it.email }
        return transfers.map {
            OwnershipTransferDetails(
                it,
                companies.getValue(it.companyId),
                emails.getValue(it.fromUserId),
                emails.getValue(it.toUserId),
            )
        }
    }
}
