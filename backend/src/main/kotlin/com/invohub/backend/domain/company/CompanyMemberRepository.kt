package com.invohub.backend.domain.company

import org.springframework.data.jpa.repository.JpaRepository

interface CompanyMemberRepository : JpaRepository<CompanyMember, Long> {
    fun findByUserId(userId: Long): List<CompanyMember>
}
