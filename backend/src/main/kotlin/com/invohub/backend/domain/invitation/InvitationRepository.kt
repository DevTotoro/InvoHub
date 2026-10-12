package com.invohub.backend.domain.invitation

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface InvitationRepository : JpaRepository<Invitation, Long> {
    fun findByCompanyIdAndEmailAndStatus(
        companyId: Long,
        email: String,
        status: InvitationStatus,
    ): Invitation?

    fun findByEmailAndStatusAndExpiresAtAfterOrderByCreatedAt(
        email: String,
        status: InvitationStatus,
        now: Instant,
    ): List<Invitation>

    fun findByCompanyIdAndStatusAndExpiresAtAfterOrderByCreatedAt(
        companyId: Long,
        status: InvitationStatus,
        now: Instant,
    ): List<Invitation>

    fun countByCompanyIdAndCreatedAtAfter(
        companyId: Long,
        since: Instant,
    ): Long

    fun countByInvitedByUserIdAndCreatedAtAfter(
        invitedByUserId: Long,
        since: Instant,
    ): Long

    /** When the address last got an invitation email, if ever */
    @Query("select max(i.notifiedAt) from Invitation i where i.email = :email")
    fun lastNotifiedAt(email: String): Instant?

    /** Open invitations the address hasn't been emailed about yet */
    @Query(
        "select i from Invitation i where i.email = :email " +
            "and i.status = com.invohub.backend.domain.invitation.InvitationStatus.PENDING " +
            "and i.notifiedAt is null and i.expiresAt > :now order by i.createdAt",
    )
    fun findDue(
        email: String,
        now: Instant,
    ): List<Invitation>

    /** Addresses with open invitations they haven't been emailed about yet */
    @Query(
        "select distinct i.email from Invitation i " +
            "where i.status = com.invohub.backend.domain.invitation.InvitationStatus.PENDING " +
            "and i.notifiedAt is null and i.expiresAt > :now",
    )
    fun emailsWithDueNotifications(now: Instant): List<String>

    /**
     * Serializes the work on one address's invitation email until the transaction ends, across every server,
     * so two can't both decide the address is due and send it two emails.
     */
    @Query(
        value = "select 1 from (select pg_advisory_xact_lock(hashtextextended(:email, 0))) as locked",
        nativeQuery = true,
    )
    fun lockEmail(email: String): Int
}
