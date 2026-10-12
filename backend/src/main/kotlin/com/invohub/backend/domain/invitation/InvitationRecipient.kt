package com.invohub.backend.domain.invitation

import com.invohub.backend.domain.common.VersionedEntity
import com.invohub.backend.domain.user.normalizeEmail
import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

/**
 * An address emailed about invitations, and when last. Claiming the next email updates this row, and its
 * version makes concurrent claims conflict: exactly one wins (see InvitationNotifier).
 */
@Entity
@Table(name = "invitation_recipients")
class InvitationRecipient(
    email: String,
) : VersionedEntity() {
    @Id
    @Column(name = "email", nullable = false, updatable = false)
    val email: String = normalizeEmail(email)

    @Column(name = "last_notified_at")
    var lastNotifiedAt: Instant? = null
        protected set

    fun notified(at: Instant?) {
        lastNotifiedAt = at
    }
}

interface InvitationRecipientRepository : JpaRepository<InvitationRecipient, String>
