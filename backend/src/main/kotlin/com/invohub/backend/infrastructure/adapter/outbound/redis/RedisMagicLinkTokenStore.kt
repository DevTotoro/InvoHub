package com.invohub.backend.infrastructure.adapter.outbound.redis

import com.invohub.backend.domain.auth.MagicLinkTokenStore
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Keys:
 * - `magic_link:cooldown:{email}`: present while a resend is not allowed
 * - `magic_link:latest:{email}`: hash of the newest token, used to invalidate it when a new one is issued
 * - `magic_link:token:{hash}`: the email a token was issued for
 *
 * Replacing a token touches several keys without a transaction; that is safe because callers only
 * replace a token while holding the email's cooldown, which serializes issuance per email.
 */
@Component
class RedisMagicLinkTokenStore(
    private val redisTemplate: StringRedisTemplate,
) : MagicLinkTokenStore {
    companion object {
        private fun cooldownKey(email: String) = "magic_link:cooldown:$email"

        private fun latestKey(email: String) = "magic_link:latest:$email"

        private fun tokenKey(tokenHash: String) = "magic_link:token:$tokenHash"
    }

    private val values = redisTemplate.opsForValue()

    override fun tryStartCooldown(
        email: String,
        cooldown: Duration,
    ): Boolean = values.setIfAbsent(cooldownKey(email), "1", cooldown) == true

    override fun clearCooldown(email: String) {
        redisTemplate.delete(cooldownKey(email))
    }

    override fun replaceToken(
        email: String,
        tokenHash: String,
        ttl: Duration,
    ) {
        values.set(tokenKey(tokenHash), email, ttl)
        val previousHash = values.setGet(latestKey(email), tokenHash, ttl)
        if (previousHash != null && previousHash != tokenHash) {
            redisTemplate.delete(tokenKey(previousHash))
        }
    }

    override fun removeToken(
        email: String,
        tokenHash: String,
    ) {
        redisTemplate.delete(listOf(tokenKey(tokenHash), latestKey(email)))
    }

    override fun consumeToken(tokenHash: String): String? = values.getAndDelete(tokenKey(tokenHash))
}
