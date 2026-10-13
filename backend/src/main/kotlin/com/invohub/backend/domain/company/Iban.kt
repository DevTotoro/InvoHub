package com.invohub.backend.domain.company

/** International bank account numbers: cleaning up as typed, and the ISO 13616 check digits. */
object Iban {
    /** No spaces or punctuation, upper case */
    fun normalize(raw: String) = raw.uppercase().filter { it.isLetterOrDigit() }

    /** A normalized IBAN: a country code, 2 check digits and the account, with the check digits right */
    fun isValid(iban: String): Boolean {
        if (!Regex("^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}$").matches(iban)) return false
        // Move the first four characters to the end, turn letters into numbers (A = 10), then mod 97 must be 1
        val rearranged = iban.drop(4) + iban.take(4)
        val remainder =
            rearranged.fold(0) { acc, char ->
                val digits = if (char.isDigit()) char.toString() else (char - 'A' + 10).toString()
                digits.fold(acc) { r, d -> (r * 10 + d.digitToInt()) % 97 }
            }
        return remainder == 1
    }
}
