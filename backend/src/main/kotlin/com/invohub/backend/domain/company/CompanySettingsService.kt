package com.invohub.backend.domain.company

import com.invohub.backend.domain.audit.AuditAction
import com.invohub.backend.domain.audit.AuditEvent
import com.invohub.backend.domain.audit.AuditLog
import com.invohub.backend.domain.audit.AuditTarget
import com.invohub.backend.domain.common.InvalidInputException
import com.invohub.backend.domain.common.NotAllowedException
import com.invohub.backend.domain.common.optionalText
import com.invohub.backend.domain.common.requiredText
import com.invohub.backend.domain.company.country.Countries
import com.invohub.backend.domain.company.country.CountryRulesRegistry
import com.invohub.backend.domain.company.country.checkedTaxId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Business details as typed; blank means none */
data class BusinessDetailsInput(
    val legalName: String?,
    val street: String?,
    val city: String?,
    val postalCode: String?,
    val taxId: String?,
    /** The country's own details, by key (see CountryRules.countryDetailKeys) */
    val countryDetails: Map<String, String?>,
)

/**
 * Editing a company: its name and country, its business details (what invoices need) and its bank accounts.
 * Only the owner and admins of an active company can (CompanyPermission.MANAGE_SETTINGS). Every change is
 * recorded as company.updated, with each changed field before and after.
 */
@Service
class CompanySettingsService(
    private val companyRepository: CompanyRepository,
    private val bankAccountRepository: BankAccountRepository,
    private val countryRules: CountryRulesRegistry,
    private val auditLog: AuditLog,
) {
    companion object {
        const val MAX_BANK_ACCOUNTS = 10
        private const val MAX_LEGAL_NAME = 255
        private const val MAX_STREET = 255
        private const val MAX_CITY = 100
        private const val MAX_POSTAL_CODE = 20
        private const val MAX_BANK_NAME = 100
    }

    /**
     * Renames the company and moves it to another country, whose currency it then uses. Moving clears the tax ID
     * and the country's own details, which mean nothing in the new country.
     * @throws InvalidInputException for a blank or too long name, or an unknown country
     */
    @Transactional
    fun updateProfile(
        access: CompanyAccess,
        name: String,
        country: String,
    ): Company {
        access.require(CompanyPermission.MANAGE_SETTINGS)
        val company = companyRepository.findById(access.companyId).orElseThrow()
        val cleanName = name.trim()
        if (cleanName.isEmpty()) throw InvalidInputException("name", "required")
        if (cleanName.length > CompanyService.MAX_NAME_LENGTH) throw InvalidInputException("name", "too_long")
        val countryCode = country.trim().uppercase()
        val currency = Countries.currencyOf(countryCode) ?: throw InvalidInputException("country", "unknown")

        val before = snapshot(company)
        company.rename(cleanName)
        company.moveTo(countryCode, currency)
        return saveAndRecord(company, before)
    }

    /**
     * Replaces the business details. The tax ID and the country's own details are checked by the country's rules.
     * @throws InvalidInputException with the field ("legalName", "street", "city", "postalCode", "taxId" or a
     * country detail's key) and the reason
     */
    @Transactional
    fun updateBusinessDetails(
        access: CompanyAccess,
        input: BusinessDetailsInput,
    ): Company {
        access.require(CompanyPermission.MANAGE_SETTINGS)
        val company = companyRepository.findById(access.companyId).orElseThrow()
        val rules = countryRules.forCountry(company.country)

        val taxId = rules.checkedTaxId(input.taxId)
        val details =
            rules.countryDetailKeys
                .mapNotNull { key ->
                    val value = input.countryDetails[key]?.let { rules.normalizeCountryDetail(key, it) }
                    if (value.isNullOrEmpty()) return@mapNotNull null
                    rules.validateCountryDetail(key, value)?.let { throw InvalidInputException(key, it) }
                    key to value
                }.toMap()

        val before = snapshot(company)
        company.updateBusinessDetails(
            legalName = optionalText(input.legalName, "legalName", MAX_LEGAL_NAME),
            street = optionalText(input.street, "street", MAX_STREET),
            city = optionalText(input.city, "city", MAX_CITY),
            postalCode = optionalText(input.postalCode, "postalCode", MAX_POSTAL_CODE),
            taxId = taxId,
            countryDetails = details,
        )
        return saveAndRecord(company, before)
    }

    /** Anyone in the company may see where it's paid */
    @Transactional(readOnly = true)
    fun bankAccounts(access: CompanyAccess): List<BankAccount> {
        access.require(CompanyPermission.VIEW)
        return bankAccountRepository.findByCompanyIdOrderByCreatedAt(access.companyId)
    }

    /**
     * @throws InvalidInputException "bankName" (required, too_long) or "iban" (invalid, duplicate)
     * @throws NotAllowedException "too_many_bank_accounts" past [MAX_BANK_ACCOUNTS]
     */
    @Transactional
    fun addBankAccount(
        access: CompanyAccess,
        bankName: String,
        rawIban: String,
    ): BankAccount {
        access.require(CompanyPermission.MANAGE_SETTINGS)
        val name = requiredText(bankName, "bankName", MAX_BANK_NAME)
        val iban = Iban.normalize(rawIban)
        if (!Iban.isValid(iban)) throw InvalidInputException("iban", "invalid")
        if (bankAccountRepository.existsByCompanyIdAndIban(access.companyId, iban)) {
            throw InvalidInputException("iban", "duplicate")
        }
        if (bankAccountRepository.countByCompanyId(access.companyId) >= MAX_BANK_ACCOUNTS) {
            throw NotAllowedException("too_many_bank_accounts")
        }

        val account = bankAccountRepository.saveAndFlush(BankAccount(access.companyId, name, iban))
        record(access.companyId, mapOf("bankAccount" to mapOf("from" to null, "to" to "$name $iban")))
        return account
    }

    /** @throws BankAccountNotFoundException unless it is one of this company's */
    @Transactional
    fun removeBankAccount(
        access: CompanyAccess,
        bankAccountId: Long,
    ) {
        access.require(CompanyPermission.MANAGE_SETTINGS)
        val account =
            bankAccountRepository.findById(bankAccountId).orElse(null)?.takeIf { it.companyId == access.companyId }
                ?: throw BankAccountNotFoundException()
        bankAccountRepository.delete(account)
        record(
            access.companyId,
            mapOf(
                "bankAccount" to mapOf("from" to "${account.bankName} ${account.iban}", "to" to null),
            ),
        )
    }

    private fun snapshot(company: Company): Map<String, Any?> =
        mapOf(
            "name" to company.name,
            "country" to company.country,
            "currency" to company.currency,
            "legalName" to company.legalName,
            "street" to company.addressStreet,
            "city" to company.addressCity,
            "postalCode" to company.addressPostalCode,
            "taxId" to company.taxId,
        ) + company.countryDetails.mapKeys { "countryDetails.${it.key}" }

    /** Saves, and records only what changed; nothing changed records nothing */
    private fun saveAndRecord(
        company: Company,
        before: Map<String, Any?>,
    ): Company {
        val saved = companyRepository.saveAndFlush(company)
        val after = snapshot(saved)
        val changes =
            (before.keys + after.keys)
                .filter { before[it] != after[it] }
                .associateWith { mapOf("from" to before[it], "to" to after[it]) }
        if (changes.isNotEmpty()) record(saved.id, changes)
        return saved
    }

    private fun record(
        companyId: Long,
        changes: Map<String, Any?>,
    ) {
        auditLog.record(
            AuditEvent(
                AuditAction.COMPANY_UPDATED,
                companyId = companyId,
                target = AuditTarget.company(companyId),
                details = changes,
            ),
        )
    }
}

/** No such bank account, for this company */
class BankAccountNotFoundException : RuntimeException("Bank account not found")
