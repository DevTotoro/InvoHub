package com.invohub.backend.infrastructure.adapter.inbound.graphql.company

import com.invohub.backend.domain.auth.AuthenticatedUser
import com.invohub.backend.domain.common.TSIDCodec
import com.invohub.backend.domain.company.CompanyAccess
import com.invohub.backend.domain.company.CompanyAccessService
import com.invohub.backend.domain.company.CompanyNotFoundException
import org.slf4j.MDC
import org.springframework.stereotype.Component

/**
 * Where every company-scoped GraphQL operation starts: resolves the signed-in user's access to the company
 * (by its public ID, as clients know it) and adds the company to the logging context. The operation then
 * requires the permission it needs from the returned access.
 *
 * Company-scoped root fields take the company as an argument named `companyId` (or are `company(id)`), which
 * CompanyIsolationTest relies on to find them all.
 */
@Component
class CompanyScope(
    private val accessService: CompanyAccessService,
) {
    companion object {
        /** Next to requestId and userId. RequestIdFilter clears the whole context after each request. */
        const val MDC_KEY = "companyId"
    }

    /** @throws CompanyNotFoundException if there is no such company or the user has no access to it */
    fun enter(
        principal: AuthenticatedUser,
        companyPublicId: String,
    ): CompanyAccess {
        val companyId =
            runCatching { TSIDCodec.decode(companyPublicId) }.getOrNull() ?: throw CompanyNotFoundException()
        val access = accessService.resolve(principal.userId, companyId)
        MDC.put(MDC_KEY, access.company.getPublicId())
        return access
    }
}
