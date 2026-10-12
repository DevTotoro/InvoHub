package com.invohub.backend.domain.company

import org.springframework.data.jpa.repository.JpaRepository

interface CompanyRepository : JpaRepository<Company, Long> {
    fun findByOwnerUserId(ownerUserId: Long): List<Company>
}
