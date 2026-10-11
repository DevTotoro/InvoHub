package com.invohub.backend.domain.auth

import java.time.Duration

/**
 * Storage for magic link tokens (only ever their hashes) and the per-email lock that prevents issuing a
 * new token while one is active. Implementations must make [tryLock] and [consumeToken] atomic.
 */
interface MagicLinkTokenStore {
    /** Locks [email] against new tokens for [duration]. Returns false if it is already locked. */
    fun tryLock(
        email: String,
        duration: Duration,
    ): Boolean

    fun unlock(email: String)

    fun saveToken(
        tokenHash: String,
        email: String,
        ttl: Duration,
    )

    fun removeToken(tokenHash: String)

    /** Returns the email the token was issued for and deletes the token, so it can be used at most once. */
    fun consumeToken(tokenHash: String): String?
}
