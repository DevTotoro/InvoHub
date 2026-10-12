package com.invohub.backend.domain.company.country

import org.springframework.stereotype.Component

/** Finds the rules for a country. Supporting a new country means adding its [CountryRules] module. */
@Component
class CountryRulesRegistry(
    rules: List<CountryRules>,
) {
    private val byCountry = rules.associateBy { it.country }

    fun forCountry(country: String): CountryRules = byCountry[country] ?: UnsupportedCountryRules(country)
}
