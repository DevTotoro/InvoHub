package com.invohub.backend.infrastructure.adapter.inbound.graphql.company

import com.invohub.backend.domain.auth.AuthenticatedUser
import com.invohub.backend.domain.company.Company
import com.invohub.backend.domain.company.CompanyPermission
import com.invohub.backend.domain.company.CompanyRole
import com.invohub.backend.domain.company.CompanyService
import com.invohub.backend.domain.company.CompanyStatus
import com.invohub.backend.domain.company.country.Countries
import com.invohub.backend.domain.company.country.CountryRulesRegistry
import com.invohub.backend.infrastructure.adapter.inbound.graphql.user.UserView
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller

data class CompanyView(
    val id: String,
    val name: String,
    val country: String,
    val currency: String,
    val status: CompanyStatus,
    val invoicingSupported: Boolean,
    /** ISO-8601 */
    val closedAt: String?,
) {
    companion object {
        fun of(
            company: Company,
            countryRules: CountryRulesRegistry,
        ) = CompanyView(
            id = company.getPublicId(),
            name = company.name,
            country = company.country,
            currency = company.currency,
            status = company.status,
            invoicingSupported = countryRules.forCountry(company.country).invoicingSupported,
            closedAt = company.closedAt?.toString(),
        )
    }
}

data class UserCompanyView(
    val company: CompanyView,
    val role: CompanyRole,
)

data class CountryView(
    val code: String,
    val currency: String,
    val invoicingSupported: Boolean,
)

data class CreateCompanyInput(
    val name: String,
    val country: String,
)

@Controller
class CompanyGraphQLController(
    private val companyService: CompanyService,
    private val companyScope: CompanyScope,
    private val countryRules: CountryRulesRegistry,
) {
    /** Fails with `not_found` for a company the user has no access to, as for one that doesn't exist */
    @QueryMapping
    fun company(
        @Argument id: String,
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): UserCompanyView {
        val access = companyScope.enter(principal, id).require(CompanyPermission.VIEW)
        return UserCompanyView(view(access.company), access.role)
    }

    /** Only reachable through `me`, so a user only ever sees their own companies */
    @SchemaMapping(typeName = "User", field = "companies")
    fun companies(user: UserView): List<UserCompanyView> =
        companyService.companiesOf(user.userId).map { UserCompanyView(view(it.company), it.role) }

    @QueryMapping
    fun countries(): List<CountryView> =
        Countries.currencies.map { (code, currency) ->
            CountryView(code, currency, countryRules.forCountry(code).invoicingSupported)
        }

    @MutationMapping
    fun createCompany(
        @Argument input: CreateCompanyInput,
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): CompanyView = view(companyService.createCompany(principal.userId, input.name, input.country))

    private fun view(company: Company) = CompanyView.of(company, countryRules)
}
