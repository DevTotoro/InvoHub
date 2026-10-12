package com.invohub.backend.domain.company.country

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CountriesTest {
    @Test
    fun `the currency follows from the country`() {
        assertEquals("EUR", Countries.currencyOf("GR"))
        assertEquals("USD", Countries.currencyOf("US"))
        assertEquals("GBP", Countries.currencyOf("GB"))
    }

    @Test
    fun `countries without a currency, or unknown codes, are not offered`() {
        assertNull(Countries.currencyOf("AQ"))
        assertNull(Countries.currencyOf("XX"))
    }
}
