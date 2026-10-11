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
    @Column(name = "email", nullable = false)
    var email: String,
    @Column(name = "locked", nullable = false)
    var locked: Boolean = false,
    @Column(name = "lock_reason", nullable = true)
    var lockReason: String? = null,
) : BaseEntity()
