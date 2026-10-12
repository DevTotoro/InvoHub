package com.invohub.backend.domain.invitation

import com.invohub.backend.domain.common.BaseEntity
import com.invohub.backend.domain.company.CompanyRole
import com.invohub.backend.domain.user.normalizeEmail
import jakarta.persistence.*
import java.time.Instant

enum class InvitationStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    REVOKED,

    /** Closed once a new invitation replaces it; until then, an expired one is just PENDING past [Invitation.expiresAt] */
    EXPIRED,
}

/**
 * An invitation for an email address to join a company with a role. It belongs to the address: whoever signs
 * in with it sees it on their Invitations page. The email that tells them is sent separately and may be held
 * back a while (see InvitationNotifier).
 *
 * The partial indexes (one pending invitation per company and email, pending ones by email) can't be written in
 * @Table; they are in the migration only.
 */
@Entity
@Table(
    name = "invitations",
    indexes = [
        Index(name = "ix_invitations_company_created", columnList = "company_id, created_at"),
        Index(name = "ix_invitations_invited_by_created", columnList = "invited_by_user_id, created_at"),
        Index(name = "ix_invitations_email_notified", columnList = "email, notified_at"),
    ],
)
class Invitation(
    companyId: Long,
    email: String,
    role: CompanyRole,
    invitedByUserId: Long,
    expiresAt: Instant,
) : BaseEntity() {
    @Column(name = "company_id", nullable = false, updatable = false)
    val companyId: Long = companyId

    @Column(name = "email", nullable = false, updatable = false)
    val email: String = normalizeEmail(email)

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, updatable = false)
    val role: CompanyRole = role

    @Column(name = "invited_by_user_id", nullable = false, updatable = false)
    val invitedByUserId: Long = invitedByUserId

    @Column(name = "expires_at", nullable = false, updatable = false)
    val expiresAt: Instant = expiresAt

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: InvitationStatus = InvitationStatus.PENDING
        protected set

    /** When the person was emailed about it; null while that email is still due */
    @Column(name = "notified_at")
    var notifiedAt: Instant? = null
        protected set

    @Column(name = "closed_at")
    var closedAt: Instant? = null
        protected set

    init {
        require(role != CompanyRole.OWNER) { "Nobody is invited as owner" }
    }

    /** Pending and not expired: it can still be accepted */
    fun isOpen(now: Instant) = status == InvitationStatus.PENDING && expiresAt.isAfter(now)

    fun markNotified(at: Instant) {
        notifiedAt = at
    }

    fun close(
        status: InvitationStatus,
        at: Instant,
    ) {
        check(this.status == InvitationStatus.PENDING) { "Invitation $id is already ${this.status}" }
        require(status != InvitationStatus.PENDING)
        this.status = status
        closedAt = at
    }
}
