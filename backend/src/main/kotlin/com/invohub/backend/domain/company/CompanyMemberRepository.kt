package com.invohub.backend.domain.company

import org.springframework.data.jpa.repository.JpaRepository

interface CompanyMemberRepository : JpaRepository<CompanyMember, CompanyMemberId> {
    fun findByUserId(userId: Long): List<CompanyMember>

    fun findByCompanyId(companyId: Long): List<CompanyMember>
}
