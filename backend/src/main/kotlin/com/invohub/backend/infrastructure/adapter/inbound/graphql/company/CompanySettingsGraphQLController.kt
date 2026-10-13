package com.invohub.backend.infrastructure.adapter.inbound.graphql.company

import com.invohub.backend.domain.auth.AuthenticatedUser
import com.invohub.backend.domain.common.TSIDCodec
import com.invohub.backend.domain.company.BankAccount
import com.invohub.backend.domain.company.BankAccountNotFoundException
import com.invohub.backend.domain.company.BusinessDetailsInput
import com.invohub.backend.domain.company.Company
import com.invohub.backend.domain.company.CompanyAccess
import com.invohub.backend.domain.company.CompanyPermission
import com.invohub.backend.domain.company.CompanyPermissionDeniedException
import com.invohub.backend.domain.company.CompanySettingsService
import com.invohub.backend.domain.company.country.CountryRulesRegistry
import com.invohub.backend.domain.company.country.SetupStep
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller

data class AddressView(
    val street: String?,
    val city: String?,
    val postalCode: String?,
)

data class CountryDetailView(
    val key: String,
    val value: String?,
)

data class BankAccountView(
    val id: String,
    val bankName: String,
    val iban: String,
)

data class CompanySettingsView(
    val company: CompanyView,
    val legalName: String?,
    val address: AddressView,
    val taxId: String?,
    val countryDetails: List<CountryDetailView>,
    val bankAccounts: List<BankAccountView>,
    /** Versioned by the stored file, so a changed logo is never served stale from a cache */
    val logoUrl: String?,
    val invoicingSetup: List<SetupStep>,
)

data class CompanyProfileInput(
    val name: String,
    val country: String,
)

data class CountryDetailInput(
    val key: String,
    val value: String?,
)

data class BusinessDetailsGraphQLInput(
    val legalName: String?,
    val street: String?,
    val city: String?,
    val postalCode: String?,
    val taxId: String?,
    val countryDetails: List<CountryDetailInput>?,
)

data class BankAccountInput(
    val bankName: String,
    val iban: String,
)

/** A company's settings, for its owner and admins; each change answers with the settings as they now are */
@Controller
class CompanySettingsGraphQLController(
    private val settingsService: CompanySettingsService,
    private val companyScope: CompanyScope,
    private val countryRules: CountryRulesRegistry,
) {
    @QueryMapping
    fun companySettings(
        @Argument companyId: String,
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): CompanySettingsView {
        val access = companyScope.enter(principal, companyId)
        // Seeing them is for those who may change them, even while the company is read-only
        access.require(CompanyPermission.VIEW)
        if (!CompanyPermission.MANAGE_SETTINGS.grantedTo(access.role)) {
            throw CompanyPermissionDeniedException(CompanyPermission.MANAGE_SETTINGS)
        }
        return view(access, access.company)
    }

    @MutationMapping
    fun updateCompanyProfile(
        @Argument companyId: String,
        @Argument input: CompanyProfileInput,
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): CompanySettingsView {
        val access = companyScope.enter(principal, companyId)
        return view(access, settingsService.updateProfile(access, input.name, input.country))
    }

    @MutationMapping
    fun updateBusinessDetails(
        @Argument companyId: String,
        @Argument input: BusinessDetailsGraphQLInput,
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): CompanySettingsView {
        val access = companyScope.enter(principal, companyId)
        val details =
            BusinessDetailsInput(
                legalName = input.legalName,
                street = input.street,
                city = input.city,
                postalCode = input.postalCode,
                taxId = input.taxId,
                countryDetails = input.countryDetails.orEmpty().associate { it.key to it.value },
            )
        return view(access, settingsService.updateBusinessDetails(access, details))
    }

    @MutationMapping
    fun addBankAccount(
        @Argument companyId: String,
        @Argument input: BankAccountInput,
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): CompanySettingsView {
        val access = companyScope.enter(principal, companyId)
        settingsService.addBankAccount(access, input.bankName, input.iban)
        return view(access, access.company)
    }

    @MutationMapping
    fun removeBankAccount(
        @Argument companyId: String,
        @Argument bankAccountId: String,
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): CompanySettingsView {
        val access = companyScope.enter(principal, companyId)
        val id = runCatching { TSIDCodec.decode(bankAccountId) }.getOrNull() ?: throw BankAccountNotFoundException()
        settingsService.removeBankAccount(access, id)
        return view(access, access.company)
    }

    private fun view(
        access: CompanyAccess,
        company: Company,
    ): CompanySettingsView {
        val rules = countryRules.forCountry(company.country)
        return CompanySettingsView(
            company = CompanyView.of(company, countryRules),
            legalName = company.legalName,
            address = AddressView(company.addressStreet, company.addressCity, company.addressPostalCode),
            taxId = company.taxId,
            countryDetails =
                rules.countryDetailKeys.map {
                    CountryDetailView(
                        it,
                        company.countryDetails[it]?.toString(),
                    )
                },
            bankAccounts = settingsService.bankAccounts(access).map(::view),
            logoUrl =
                company.logoKey?.let {
                    "/api/companies/${company.getPublicId()}/logo?v=${it.substringAfterLast('/').substringBefore('.')}"
                },
            invoicingSetup = rules.invoicingSetup(company),
        )
    }

    private fun view(account: BankAccount) = BankAccountView(account.getPublicId(), account.bankName, account.iban)
}
