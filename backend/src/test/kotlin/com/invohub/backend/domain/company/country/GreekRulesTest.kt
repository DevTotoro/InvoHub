package com.invohub.backend.domain.company.country

import com.invohub.backend.domain.company.Company
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GreekRulesTest {
    private val rules = GreekRules()

    @ParameterizedTest
    @ValueSource(strings = ["094014201", "997643711", "123456783"])
    fun `accepts a valid ΑΦΜ`(taxId: String) {
        assertNull(rules.validateTaxId(taxId))
    }

    @ParameterizedTest
    @ValueSource(strings = ["094014202", "997643712", "123456784"])
    fun `catches a typo through the check digit`(taxId: String) {
        assertEquals(TaxIdProblem.WRONG_CHECK_DIGIT, rules.validateTaxId(taxId))
    }

    @ParameterizedTest
    @ValueSource(strings = ["09401420", "0940142011", "09401420A", "000000000"])
    fun `rejects anything that isn't 9 digits`(taxId: String) {
        assertEquals(TaxIdProblem.WRONG_FORMAT, rules.validateTaxId(taxId))
    }

    @Test
    fun `an empty tax ID is reported as such`() {
        assertEquals(TaxIdProblem.EMPTY, rules.validateTaxId(""))
    }

    @ParameterizedTest
    @CsvSource(
        "'094 014 201',094014201",
        "EL094014201,094014201",
        "'el 094.014.201',094014201",
        "GR094014201,094014201",
    )
    fun `cleans up the tax ID as typed`(
        raw: String,
        normalized: String,
    ) {
        assertEquals(normalized, rules.normalizeTaxId(raw))
    }

    @Test
    fun `a new company has every invoicing setup step left to do`() {
        val steps = rules.invoicingSetup(Company("Acme", "GR", "EUR", ownerUserId = 1))

        assertEquals(listOf("tax_id", "business_details", "mydata"), steps.map { it.key })
        assertEquals(listOf(false, false, false), steps.map { it.done })
    }

    @Test
    fun `a ΚΑΔ keeps only its digits, and must have 2 to 8`() {
        assertEquals("62011101", rules.normalizeCountryDetail(GreekRules.MAIN_ACTIVITY, "62.01.11.01"))
        assertNull(rules.validateCountryDetail(GreekRules.MAIN_ACTIVITY, "62"))
        assertEquals("invalid", rules.validateCountryDetail(GreekRules.MAIN_ACTIVITY, "6"))
        assertEquals("invalid", rules.validateCountryDetail(GreekRules.MAIN_ACTIVITY, "620111011"))
    }

    @Test
    fun `the tax office is free text, trimmed and not too long`() {
        assertEquals("Α' Αθηνών", rules.normalizeCountryDetail(GreekRules.TAX_OFFICE, "  Α' Αθηνών "))
        assertNull(rules.validateCountryDetail(GreekRules.TAX_OFFICE, "x".repeat(100)))
        assertEquals("too_long", rules.validateCountryDetail(GreekRules.TAX_OFFICE, "x".repeat(101)))
    }

    @Test
    fun `business details are done with the address, the tax office and the ΚΑΔ, myDATA isn't available yet`() {
        val company =
            Company("Acme", "GR", "EUR", ownerUserId = 1).apply {
                updateBusinessDetails(
                    "Acme ΙΚΕ",
                    "Ερμού 1",
                    "Αθήνα",
                    "10563",
                    "094014201",
                    mapOf(GreekRules.TAX_OFFICE to "Α' Αθηνών", GreekRules.MAIN_ACTIVITY to "6201"),
                )
            }

        val steps = rules.invoicingSetup(company)

        assertEquals(listOf(true, true, false), steps.map { it.done })
        assertEquals(listOf(true, true, false), steps.map { it.available })
    }
}
