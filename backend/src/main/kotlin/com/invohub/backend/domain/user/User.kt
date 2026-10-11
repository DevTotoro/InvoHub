package com.invohub.backend.domain.user

import com.invohub.backend.domain.common.BaseEntity
import jakarta.persistence.*

@Entity
@Table(
    name = "users",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_users_email", columnNames = ["email"]),
    ],
)
class User(
    email: String,
) : BaseEntity() {
    @Column(name = "email", nullable = false)
    var email: String = normalizeEmail(email)
        protected set

    @Column(name = "locked", nullable = false)
    var locked: Boolean = false
        protected set

    @Column(name = "lock_reason", nullable = true)
    var lockReason: String? = null
        protected set

    /** Prevents the user from signing in. [reason] is an internal note and is never shown to the user. */
    fun lock(reason: String) {
        locked = true
        lockReason = reason
    }

    fun unlock() {
        locked = false
        lockReason = null
    }
}
