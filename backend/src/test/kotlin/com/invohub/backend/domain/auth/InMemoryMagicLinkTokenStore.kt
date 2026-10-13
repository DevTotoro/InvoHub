package com.invohub.backend.domain.auth

import java.time.Duration

/** Test double with the same semantics as the Redis store, minus expiry: see [expireAll]. */
class InMemoryMagicLinkTokenStore : MagicLinkTokenStore {
    private val locked = mutableSetOf<String>()
    private val tokens = mutableMapOf<String, String>()
    private val renewable = mutableMapOf<String, String>()

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
        renewableFor: Duration,
    ) {
        tokens[tokenHash] = email
        renewable[tokenHash] = email
    }

    override fun removeToken(tokenHash: String) {
        tokens.remove(tokenHash)
        renewable.remove(tokenHash)
    }

    override fun consumeToken(tokenHash: String) = tokens.remove(tokenHash)

    override fun findRenewable(tokenHash: String) = renewable[tokenHash]

    /** As if every token had expired and every lock had run out, while they can still be renewed */
    fun expireAll() {
        tokens.clear()
        locked.clear()
    }
}
