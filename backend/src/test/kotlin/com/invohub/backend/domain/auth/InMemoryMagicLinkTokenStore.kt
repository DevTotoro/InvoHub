package com.invohub.backend.domain.auth

import java.time.Duration

/** Test double with the same semantics as the Redis store, minus expiry. */
class InMemoryMagicLinkTokenStore : MagicLinkTokenStore {
    private val locked = mutableSetOf<String>()
    private val tokens = mutableMapOf<String, String>()

    override fun tryLock(
        email: String,
        duration: Duration,
    ) = locked.add(email)

    override fun unlock(email: String) {
        locked.remove(email)
    }

    override fun saveToken(
        tokenHash: String,
        email: String,
        ttl: Duration,
    ) {
        tokens[tokenHash] = email
    }

    override fun removeToken(tokenHash: String) {
        tokens.remove(tokenHash)
    }

    override fun consumeToken(tokenHash: String) = tokens.remove(tokenHash)
}
