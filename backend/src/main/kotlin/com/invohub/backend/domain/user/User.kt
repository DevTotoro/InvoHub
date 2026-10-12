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
    language: Language = Language.DEFAULT,
) : BaseEntity() {
    @Column(name = "email", nullable = false)
    var email: String = normalizeEmail(email)
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false)
    var language: Language = language
        protected set

    @Column(name = "suspended", nullable = false)
    var suspended: Boolean = false
        protected set

    @Column(name = "suspension_reason", nullable = true)
    var suspensionReason: String? = null
        protected set

    /**
     * Stops the user from signing in, in every company. An InvoHub staff action (fraud, abuse, a stolen
     * account), not something companies do. [reason] is an internal note and is never shown to the user.
     */
    fun suspend(reason: String) {
        suspended = true
        suspensionReason = reason
    }

    fun changeLanguage(language: Language) {
        this.language = language
    }

    fun unsuspend() {
        suspended = false
        suspensionReason = null
    }
}
