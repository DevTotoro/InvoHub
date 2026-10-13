package com.invohub.backend.domain.company

import com.invohub.backend.domain.common.BaseEntity
import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

enum class OwnershipTransferStatus {
    PENDING,
    ACCEPTED,
    DECLINED,

    /** By the owner, or because the recipient left the company */
    CANCELLED,

    /** Closed once a new offer replaces it; until then, an expired one is just PENDING past its expiry */
    EXPIRED,
}

/**
 * The owner offering ownership of a company to one of its members. Ownership comes with billing and the right to
 * close the company, so it only moves once the recipient accepts.
 *
 * The partial indexes (one pending transfer per company, pending ones by recipient) can't be written in @Table;
 * they are in the migration only.
 */
@Entity
@Table(name = "ownership_transfers")
class OwnershipTransfer(
    companyId: Long,
    fromUserId: Long,
    toUserId: Long,
    expiresAt: Instant,
) : BaseEntity() {
    @Column(name = "company_id", nullable = false, updatable = false)
    val companyId: Long = companyId

    @Column(name = "from_user_id", nullable = false, updatable = false)
    val fromUserId: Long = fromUserId

    @Column(name = "to_user_id", nullable = false, updatable = false)
    val toUserId: Long = toUserId

    @Column(name = "expires_at", nullable = false, updatable = false)
    val expiresAt: Instant = expiresAt

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: OwnershipTransferStatus = OwnershipTransferStatus.PENDING
        protected set

    @Column(name = "closed_at")
    var closedAt: Instant? = null
        protected set

    init {
        require(fromUserId != toUserId) { "Ownership can't be offered to its owner" }
    }

    /** Pending and not expired: it can still be accepted */
    fun isOpen(now: Instant) = status == OwnershipTransferStatus.PENDING && expiresAt.isAfter(now)

    fun close(
        status: OwnershipTransferStatus,
        at: Instant,
    ) {
        check(this.status == OwnershipTransferStatus.PENDING) { "Transfer $id is already ${this.status}" }
        require(status != OwnershipTransferStatus.PENDING)
        this.status = status
        closedAt = at
    }
}

interface OwnershipTransferRepository : JpaRepository<OwnershipTransfer, Long> {
    fun findByCompanyIdAndStatus(
        companyId: Long,
        status: OwnershipTransferStatus,
    ): OwnershipTransfer?

    fun findByToUserIdAndStatusAndExpiresAtAfterOrderByCreatedAt(
        toUserId: Long,
        status: OwnershipTransferStatus,
        now: Instant,
    ): List<OwnershipTransfer>
}

/** No such open transfer, for this user or company */
class OwnershipTransferNotFoundException : RuntimeException("Ownership transfer not found")
