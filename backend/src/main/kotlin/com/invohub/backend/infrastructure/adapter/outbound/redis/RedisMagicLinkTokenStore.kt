package com.invohub.backend.infrastructure.adapter.outbound.redis

import com.invohub.backend.domain.auth.MagicLinkTokenStore
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Keys:
 * - `magic_link:active:{email}`: present while a token for the email is active
 * - `magic_link:token:{hash}`: the email a token was issued for
 */
@Component
class RedisMagicLinkTokenStore(
    private val redisTemplate: StringRedisTemplate,
) : MagicLinkTokenStore {
    companion object {
        private fun activeKey(email: String) = "magic_link:active:$email"

        private fun tokenKey(tokenHash: String) = "magic_link:token:$tokenHash"
    }

    private val values = redisTemplate.opsForValue()

    override fun tryLock(
        email: String,
        duration: Duration,
    ): Boolean = values.setIfAbsent(activeKey(email), "1", duration) == true

    override fun unlock(email: String) {
        redisTemplate.delete(activeKey(email))
    }

    override fun saveToken(
        tokenHash: String,
        email: String,
        ttl: Duration,
    ) {
        values.set(tokenKey(tokenHash), email, ttl)
    }

    override fun removeToken(tokenHash: String) {
        redisTemplate.delete(tokenKey(tokenHash))
    }

    override fun consumeToken(tokenHash: String): String? = values.getAndDelete(tokenKey(tokenHash))
}
