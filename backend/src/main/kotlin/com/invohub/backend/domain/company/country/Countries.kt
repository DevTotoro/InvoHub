package com.invohub.backend.domain.company.country

import java.util.Currency
import java.util.Locale

/**
 * The countries a company can be in: every ISO 3166 country the JDK knows a currency for (which leaves out
 * places without one, such as Antarctica). Whether InvoHub supports invoicing there is up to [CountryRules].
 */
object Countries {
    /** Country code → currency code, e.g. GR → EUR */
    val currencies: Map<String, String> =
        Locale
            .getISOCountries()
            .mapNotNull { code -> currencyOf(Locale.of("", code))?.let { code to it } }
            .toMap()

    fun currencyOf(country: String): String? = currencies[country]

    private fun currencyOf(locale: Locale): String? =
        runCatching { Currency.getInstance(locale)?.currencyCode }.getOrNull()
}
