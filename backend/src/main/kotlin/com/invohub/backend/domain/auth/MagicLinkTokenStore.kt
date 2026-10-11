package com.invohub.backend.domain.auth

import java.time.Duration

/**
 * Storage for magic link tokens (only ever their hashes) and the per-email resend cooldown.
 * Implementations must make [tryStartCooldown] and [consumeToken] atomic.
 */
interface MagicLinkTokenStore {
    /** Starts the resend cooldown for [email]. Returns false if one is already running. */
    fun tryStartCooldown(
        email: String,
        cooldown: Duration,
    ): Boolean

    fun clearCooldown(email: String)

    /** Stores [tokenHash] for [email], invalidating any earlier unused token for the same email. */
    fun replaceToken(
        email: String,
        tokenHash: String,
        ttl: Duration,
    )

    fun removeToken(
        email: String,
        tokenHash: String,
    )

    /** Returns the email the token was issued for and deletes the token, so it can be used at most once. */
    fun consumeToken(tokenHash: String): String?
}
