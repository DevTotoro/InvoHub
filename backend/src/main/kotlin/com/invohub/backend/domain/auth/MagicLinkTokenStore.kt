package com.invohub.backend.domain.auth

import java.time.Duration

/**
 * Storage for magic link tokens (only ever their hashes) and the per-email lock that prevents issuing a
 * new token while one is active. Implementations must make [tryLock] and [consumeToken] atomic.
 *
 * Besides the token itself, which works for a short while and once, a token is remembered for longer as
 * renewable: a person opening an old link can ask for a fresh one, sent to the same address.
 */
interface MagicLinkTokenStore {
    /** Locks [email] against new tokens for [duration]. Returns false if it is already locked. */
    fun tryLock(
        email: String,
        duration: Duration,
    ): Boolean

    fun unlock(email: String)

    /** [ttl]: how long the token works; [renewableFor]: how long afterwards it can still be renewed */
    fun saveToken(
        tokenHash: String,
        email: String,
        ttl: Duration,
        renewableFor: Duration,
    )

    fun removeToken(tokenHash: String)

    /** Returns the email the token was issued for and deletes it, so it can be used at most once. */
    fun consumeToken(tokenHash: String): String?

    /** The email an expired or used token was issued for, while it can still be renewed */
    fun findRenewable(tokenHash: String): String?
}
