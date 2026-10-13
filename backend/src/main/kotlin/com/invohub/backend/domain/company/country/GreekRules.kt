package com.invohub.backend.domain.company.country

import com.invohub.backend.domain.company.Company
import org.springframework.stereotype.Component

@Component
class GreekRules : CountryRules {
    companion object {
        const val TAX_OFFICE = "taxOffice"
        const val MAIN_ACTIVITY = "mainActivity"
    }

    override val country = "GR"

    override val invoicingSupported = true

    /** Also strips the EU VAT prefix, which is EL for Greece (GR is accepted too) */
    override fun normalizeTaxId(raw: String) =
        raw
            .uppercase()
            .filter { it.isLetterOrDigit() }
            .removePrefix("EL")
            .removePrefix("GR")

    /** ΑΦΜ: 9 digits, the last a check digit */
    override fun validateTaxId(taxId: String): TaxIdProblem? =
        when {
            taxId.isEmpty() -> TaxIdProblem.EMPTY
            taxId.length != 9 || !taxId.all { it in '0'..'9' } || taxId.all { it == '0' } -> TaxIdProblem.WRONG_FORMAT
            checkDigit(taxId) != taxId.last().digitToInt() -> TaxIdProblem.WRONG_CHECK_DIGIT
            else -> null
        }

    override fun invoicingSetup(company: Company) =
        listOf(
            SetupStep("tax_id", company.taxId != null),
            SetupStep(
                "business_details",
                listOf(company.legalName, company.addressStreet, company.addressCity, company.addressPostalCode)
                    .all { it != null } &&
                    company.countryDetails[TAX_OFFICE] != null &&
                    company.countryDetails[MAIN_ACTIVITY] != null,
            ),
            // Connecting myDATA comes with e-invoicing; until then this step can't be completed
            SetupStep("mydata", done = false, available = false),
        )

    /** The tax office (ΔΟΥ) as written, for now; later from AADE's registry. The main activity (ΚΑΔ) as its code */
    override val countryDetailKeys = listOf(TAX_OFFICE, MAIN_ACTIVITY)

    /** A ΚΑΔ is often written with dots (62.01.11.01): only its digits are kept */
    override fun normalizeCountryDetail(
        key: String,
        raw: String,
    ) = if (key == MAIN_ACTIVITY) raw.filter { it.isDigit() } else raw.trim()

    override fun validateCountryDetail(
        key: String,
        value: String,
    ): String? =
        when {
            key == MAIN_ACTIVITY && value.length !in 2..8 -> "invalid"
            else -> super.validateCountryDetail(key, value)
        }

    /** The first 8 digits weighted by 2^8 down to 2^1, then (sum mod 11) mod 10 */
    private fun checkDigit(taxId: String) =
        taxId.take(8).foldIndexed(0) { i, sum, digit -> sum + digit.digitToInt() * (1 shl (8 - i)) } % 11 % 10
}
