package com.invohub.backend.infrastructure.adapter.outbound.redis

import com.invohub.backend.domain.auth.MagicLinkToken
import com.invohub.backend.domain.auth.MagicLinkTokenStore
import com.invohub.backend.domain.auth.SignInDestination
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Keys:
 * - `magic_link:active:{email}`: present while a token for the email is active
 * - `magic_link:token:{hash}`: what a working token was issued for
 * - `magic_link:renewable:{hash}`: the same, kept longer, so an old link can ask for a fresh one
 *
 * Values are `{destination} {email}`: emails have no spaces.
 */
@Component
class RedisMagicLinkTokenStore(
    private val redisTemplate: StringRedisTemplate,
) : MagicLinkTokenStore {
    companion object {
        private fun activeKey(email: String) = "magic_link:active:$email"

        private fun tokenKey(tokenHash: String) = "magic_link:token:$tokenHash"

        private fun renewableKey(tokenHash: String) = "magic_link:renewable:$tokenHash"

        private fun encode(token: MagicLinkToken) = "${token.destination.name} ${token.email}"

        private fun decode(value: String?): MagicLinkToken? {
            val (destination, email) = value?.split(' ', limit = 2)?.takeIf { it.size == 2 } ?: return null
            return MagicLinkToken(email, SignInDestination.valueOf(destination))
        }
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
        token: MagicLinkToken,
        ttl: Duration,
        renewableFor: Duration,
    ) {
        values.set(tokenKey(tokenHash), encode(token), ttl)
        values.set(renewableKey(tokenHash), encode(token), ttl + renewableFor)
    }

    override fun removeToken(tokenHash: String) {
        redisTemplate.delete(listOf(tokenKey(tokenHash), renewableKey(tokenHash)))
    }

    override fun consumeToken(tokenHash: String): MagicLinkToken? = decode(values.getAndDelete(tokenKey(tokenHash)))

    override fun findRenewable(tokenHash: String): MagicLinkToken? = decode(values.get(renewableKey(tokenHash)))
}
