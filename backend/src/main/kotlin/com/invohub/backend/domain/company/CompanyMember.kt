package com.invohub.backend.domain.company

import com.invohub.backend.domain.common.VersionedEntity
import jakarta.persistence.*
import org.hibernate.Hibernate
import java.io.Serializable

/** The key of a [CompanyMember]: a membership is nothing more than a user in a company. */
data class CompanyMemberId(
    var companyId: Long = 0,
    var userId: Long = 0,
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/** A user's access to a company they don't own. */
@Entity
@IdClass(CompanyMemberId::class)
@Table(
    name = "company_members",
    indexes = [Index(name = "ix_company_members_user", columnList = "user_id")],
)
class CompanyMember(
    companyId: Long,
    userId: Long,
    role: CompanyRole,
) : VersionedEntity() {
    @Id
    @Column(name = "company_id", nullable = false, updatable = false)
    val companyId: Long = companyId

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: Long = userId

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    var role: CompanyRole = role
        protected set

    init {
        requireNotOwner(role)
    }

    fun changeRole(role: CompanyRole) {
        requireNotOwner(role)
        this.role = role
    }

    private fun requireNotOwner(role: CompanyRole) =
        require(role != CompanyRole.OWNER) { "The owner is stored on the company, not as a member" }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CompanyMember) return false
        if (Hibernate.getClassLazy(this) != Hibernate.getClassLazy(other)) return false
        return companyId == other.companyId && userId == other.userId
    }

    override fun hashCode(): Int = 31 * companyId.hashCode() + userId.hashCode()
}
