package com.invohub.backend.domain.company

import com.invohub.backend.domain.common.BaseEntity
import jakarta.persistence.*

/** A user's access to a company they don't own. */
@Entity
@Table(name = "company_members")
class CompanyMember(
    companyId: Long,
    userId: Long,
    role: CompanyRole,
) : BaseEntity() {
    @Column(name = "company_id", nullable = false, updatable = false)
    val companyId: Long = companyId

    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: Long = userId

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    var role: CompanyRole = role
        protected set

    init {
        require(role != CompanyRole.OWNER) { "The owner is stored on the company, not as a member" }
    }
}
