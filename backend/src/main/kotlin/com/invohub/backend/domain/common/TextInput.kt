package com.invohub.backend.domain.common

/**
 * A text as typed, trimmed; null when blank.
 * @throws InvalidInputException [field] "too_long" past [maxLength]
 */
fun optionalText(
    raw: String?,
    field: String,
    maxLength: Int,
): String? {
    val value = raw?.trim()?.ifEmpty { null } ?: return null
    if (value.length > maxLength) throw InvalidInputException(field, "too_long")
    return value
}

/** @throws InvalidInputException [field] "required" when blank, "too_long" past [maxLength] */
fun requiredText(
    raw: String?,
    field: String,
    maxLength: Int,
): String = optionalText(raw, field, maxLength) ?: throw InvalidInputException(field, "required")
