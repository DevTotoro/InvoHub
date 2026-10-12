package com.invohub.backend.domain.common

/**
 * Input the domain refuses. [field] names the offending input and [reason] says what's wrong with it, both as
 * stable identifiers clients can show a message for (e.g. field "name", reason "required").
 */
class InvalidInputException(
    val field: String,
    val reason: String,
) : RuntimeException("Invalid $field: $reason")
