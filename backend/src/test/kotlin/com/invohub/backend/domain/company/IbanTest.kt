package com.invohub.backend.domain.company

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IbanTest {
    @ParameterizedTest
    @ValueSource(strings = ["GR1601101250000000012300695", "DE89370400440532013000", "GB82WEST12345698765432"])
    fun `accepts a valid IBAN`(iban: String) {
        assertTrue(Iban.isValid(iban))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "GR1601101250000000012300696",
            "GR1701101250000000012300695",
            "GR16011012",
            "1601101250000000012300695GR",
            "",
        ],
    )
    fun `rejects a typo or anything that isn't an IBAN`(iban: String) {
        assertFalse(Iban.isValid(iban))
    }

    @Test
    fun `cleans up the IBAN as typed`() {
        assertEquals("GR1601101250000000012300695", Iban.normalize(" gr16 0110 1250 0000-0001 2300 695 "))
    }
}
