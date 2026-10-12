package com.invohub.backend.domain.company

import com.invohub.backend.domain.audit.AuditAction
import com.invohub.backend.domain.audit.AuditEvent
import com.invohub.backend.domain.audit.AuditLog
import com.invohub.backend.domain.audit.AuditTarget
import com.invohub.backend.domain.common.InvalidInputException
import com.invohub.backend.domain.company.country.Countries
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** A company the user can access, with their role in it. */
data class UserCompany(
    val company: Company,
    val role: CompanyRole,
)

@Service
class CompanyService(
    private val companyRepository: CompanyRepository,
    private val memberRepository: CompanyMemberRepository,
    private val auditLog: AuditLog,
) {
    companion object {
        const val MAX_NAME_LENGTH = 200
    }

    /**
     * Creates a company owned by the user. Only a name and a country are needed; the currency follows from
     * the country, and everything invoicing needs is asked for later.
     *
     * @throws InvalidInputException for a blank or too long name, or an unknown country
     */
    @Transactional
    fun createCompany(
        ownerUserId: Long,
        name: String,
        country: String,
    ): Company {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) throw InvalidInputException("name", "required")
        if (cleanName.length > MAX_NAME_LENGTH) throw InvalidInputException("name", "too_long")

        val countryCode = country.trim().uppercase()
        val currency = Countries.currencyOf(countryCode) ?: throw InvalidInputException("country", "unknown")

        val company = companyRepository.save(Company(cleanName, countryCode, currency, ownerUserId))
        auditLog.record(
            AuditEvent(
                AuditAction.COMPANY_CREATED,
                companyId = company.id,
                target = AuditTarget.company(company.id),
                details = mapOf("name" to cleanName, "country" to countryCode),
            ),
        )
        return company
    }

    /** Every company the user owns or is a member of, by name. */
    @Transactional(readOnly = true)
    fun companiesOf(userId: Long): List<UserCompany> {
        val owned = companyRepository.findByOwnerUserId(userId).map { UserCompany(it, CompanyRole.OWNER) }
        val memberships = memberRepository.findByUserId(userId).associateBy { it.companyId }
        val joined =
            companyRepository.findAllById(memberships.keys).map { UserCompany(it, memberships.getValue(it.id).role) }
        return (owned + joined).sortedBy { it.company.name.lowercase() }
    }
}
