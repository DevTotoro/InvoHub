package com.invohub.backend.domain.company.country

import com.invohub.backend.domain.common.InvalidInputException
import com.invohub.backend.domain.company.Company

/**
 * What InvoHub knows about doing business in one country: everything that differs between countries lives in
 * a module like this, so the rest of the app never asks "which country?". A country is supported exactly when
 * it has a module ([CountryRulesRegistry]); every other country gets [UnsupportedCountryRules].
 */
interface CountryRules {
    /** ISO 3166-1 alpha-2 */
    val country: String

    /** Whether InvoHub knows how invoicing works here. If not, companies can't complete invoicing setup. */
    val invoicingSupported: Boolean

    /** The tax ID as typed, cleaned up: no spaces, punctuation or country prefix */
    fun normalizeTaxId(raw: String): String

    /** What's wrong with a normalized tax ID, or null if it's valid */
    fun validateTaxId(taxId: String): TaxIdProblem?

    /** What a company must complete before issuing its first invoice, in order */
    fun invoicingSetup(company: Company): List<SetupStep>

    /** The country's own business details (stored in Company.countryDetails), in the order a form shows them */
    val countryDetailKeys: List<String>

    /** A country detail as typed, cleaned up */
    fun normalizeCountryDetail(
        key: String,
        raw: String,
    ): String = raw.trim()

    /** What's wrong with a normalized country detail, as a stable reason (e.g. "invalid"), or null if it's valid */
    fun validateCountryDetail(
        key: String,
        value: String,
    ): String? = if (value.length > MAX_COUNTRY_DETAIL_LENGTH) "too_long" else null

    companion object {
        const val MAX_COUNTRY_DETAIL_LENGTH = 100
    }
}

/**
 * A tax ID as typed, cleaned up and checked by these rules; null when blank.
 * @throws InvalidInputException "taxId" with "check_digit" (most likely a typo) or "invalid"
 */
fun CountryRules.checkedTaxId(raw: String?): String? {
    val taxId = raw?.let(::normalizeTaxId)?.ifEmpty { null } ?: return null
    return when (validateTaxId(taxId)) {
        null -> taxId
        TaxIdProblem.WRONG_CHECK_DIGIT -> throw InvalidInputException("taxId", "check_digit")
        else -> throw InvalidInputException("taxId", "invalid")
    }
}

enum class TaxIdProblem {
    EMPTY,
    WRONG_FORMAT,
    WRONG_CHECK_DIGIT,
}

/**
 * One item of the invoicing setup checklist. [key] is a stable identifier clients show a message for.
 * [available] is false for steps that can't be done in InvoHub yet (e.g. connecting myDATA).
 */
data class SetupStep(
    val key: String,
    val done: Boolean,
    val available: Boolean = true,
)
