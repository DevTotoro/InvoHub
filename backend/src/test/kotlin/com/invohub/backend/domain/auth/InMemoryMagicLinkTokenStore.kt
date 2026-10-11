package com.invohub.backend.domain.auth

import java.time.Duration

/** Test double with the same semantics as the Redis store, minus expiry. */
class InMemoryMagicLinkTokenStore : MagicLinkTokenStore {
    private val cooldowns = mutableSetOf<String>()
    private val latest = mutableMapOf<String, String>()
    private val tokens = mutableMapOf<String, String>()

    override fun tryStartCooldown(
        email: String,
        cooldown: Duration,
    ) = cooldowns.add(email)

    override fun clearCooldown(email: String) {
        cooldowns.remove(email)
    }

    override fun replaceToken(
        email: String,
        tokenHash: String,
        ttl: Duration,
    ) {
        tokens[tokenHash] = email
        latest.put(email, tokenHash)?.let(tokens::remove)
    }

    override fun removeToken(
        email: String,
        tokenHash: String,
    ) {
        tokens.remove(tokenHash)
        latest.remove(email)
    }

    override fun consumeToken(tokenHash: String) = tokens.remove(tokenHash)
}
