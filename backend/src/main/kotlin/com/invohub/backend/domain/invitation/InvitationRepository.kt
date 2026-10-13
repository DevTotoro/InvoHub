package com.invohub.backend.domain.invitation

import org.springframework.data.jpa.repository.JpaRepository
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
}
