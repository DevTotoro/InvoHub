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

    /**
     * Open invitations the address hasn't been emailed about: never claimed, or claimed by a sender that never
     * confirmed (before [staleBefore])
     */
    @Query(
        "select i from Invitation i where i.email = :email " +
            "and i.status = com.invohub.backend.domain.invitation.InvitationStatus.PENDING " +
            "and i.notifiedAt is null and i.expiresAt > :now " +
            "and (i.notificationClaimedAt is null or i.notificationClaimedAt < :staleBefore) order by i.createdAt",
    )
    fun findDue(
        email: String,
        now: Instant,
        staleBefore: Instant,
    ): List<Invitation>

    /** Addresses with invitations [findDue] would return */
    @Query(
        "select distinct i.email from Invitation i " +
            "where i.status = com.invohub.backend.domain.invitation.InvitationStatus.PENDING " +
            "and i.notifiedAt is null and i.expiresAt > :now " +
            "and (i.notificationClaimedAt is null or i.notificationClaimedAt < :staleBefore)",
    )
    fun emailsWithDueNotifications(
        now: Instant,
        staleBefore: Instant,
    ): List<String>
}
