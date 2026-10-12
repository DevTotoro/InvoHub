package com.invohub.backend.domain.common

/**
 * A rule of the domain forbids this, whoever asks; not a matter of permissions. [reason] is a stable
 * identifier clients can show a message for (e.g. "owner_cannot_leave").
 */
class NotAllowedException(
    val reason: String,
) : RuntimeException("Not allowed: $reason")
