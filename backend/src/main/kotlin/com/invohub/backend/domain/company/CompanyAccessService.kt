package com.invohub.backend.domain.company

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * The one place that works out a user's access to a company: whether they have any, with which role, and
 * the company's status. Every company-scoped request goes through here.
 */
@Service
class CompanyAccessService(
    private val companyRepository: CompanyRepository,
    private val memberRepository: CompanyMemberRepository,
) {
    /** @throws CompanyNotFoundException if there is no such company or the user has no access to it */
    @Transactional(readOnly = true)
    fun resolve(
        userId: Long,
        companyId: Long,
    ): CompanyAccess {
        val company = companyRepository.findById(companyId).orElseThrow(::CompanyNotFoundException)
        val role =
            if (company.ownerUserId == userId) {
                CompanyRole.OWNER
            } else {
                memberRepository.findById(CompanyMemberId(companyId, userId)).orElse(null)?.role
                    ?: throw CompanyNotFoundException()
            }
        return CompanyAccess(company, userId, role)
    }
}
