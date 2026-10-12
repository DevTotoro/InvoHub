package com.invohub.backend.domain.company.country

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
}

enum class TaxIdProblem {
    EMPTY,
    WRONG_FORMAT,
    WRONG_CHECK_DIGIT,
}

/** One item of the invoicing setup checklist. [key] is a stable identifier clients show a message for. */
data class SetupStep(
    val key: String,
    val done: Boolean,
)
