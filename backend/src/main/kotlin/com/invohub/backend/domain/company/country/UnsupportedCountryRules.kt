package com.invohub.backend.domain.company.country

import com.invohub.backend.domain.company.Company

/**
 * Any country InvoHub doesn't support yet. Companies there can do everything except issue invoices: their
 * invoicing setup has one step that can't be completed, until the country gets rules of its own.
 */
data class UnsupportedCountryRules(
    override val country: String,
) : CountryRules {
    override val invoicingSupported = false

    override fun normalizeTaxId(raw: String) = raw.uppercase().filter { it.isLetterOrDigit() }

    override fun validateTaxId(taxId: String) = if (taxId.isEmpty()) TaxIdProblem.EMPTY else null

    override fun invoicingSetup(company: Company) = listOf(SetupStep("invoicing_not_available", false))
}
