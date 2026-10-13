package com.invohub.backend.domain.company.country

import com.invohub.backend.domain.company.Company
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CountryRulesRegistryTest {
    private val registry = CountryRulesRegistry(listOf(GreekRules()))

    @Test
    fun `a country with its own module is supported`() {
        val rules = registry.forCountry("GR")

        assertIs<GreekRules>(rules)
        assertTrue(rules.invoicingSupported)
    }

    @Test
    fun `any other country gets a checklist that can't be completed`() {
        val rules = registry.forCountry("DE")

        assertFalse(rules.invoicingSupported)
        val steps = rules.invoicingSetup(Company("Acme GmbH", "DE", "EUR", ownerUserId = 1))
        assertEquals(listOf(SetupStep("invoicing_not_available", done = false, available = false)), steps)
    }
}
