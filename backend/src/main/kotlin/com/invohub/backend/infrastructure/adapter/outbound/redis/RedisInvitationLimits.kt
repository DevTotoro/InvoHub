package com.invohub.backend.infrastructure.adapter.outbound.redis

import com.invohub.backend.domain.common.TSIDCodec
import com.invohub.backend.domain.invitation.InvitationLimits
import com.invohub.backend.domain.invitation.InvitationProperties
import com.invohub.backend.domain.invitation.InvitationRefusedException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Duration
import java.util.HexFormat

/**
 * Counters that live a day from their first invitation:
 * - `invohub:invitations:by:{public user ID}`: invitations the user sent
 * - `invohub:invitations:to:{SHA-256 of the email}`: invitation emails the address got; hashed, so no
 *   addresses sit in Redis
 *
 * Each command touches one key, as ElastiCache Serverless requires.
 */
@Component
class RedisInvitationLimits(
    private val redisTemplate: StringRedisTemplate,
    private val properties: InvitationProperties,
) : InvitationLimits {
    companion object {
        private val WINDOW = Duration.ofDays(1)

        private fun inviterKey(userId: Long) = "invohub:invitations:by:${TSIDCodec.encode(userId)}"

        private fun recipientKey(email: String): String {
            val hash = MessageDigest.getInstance("SHA-256").digest(email.toByteArray(Charsets.UTF_8))
            return "invohub:invitations:to:${HexFormat.of().formatHex(hash)}"
        }
    }

    private val values = redisTemplate.opsForValue()

    override fun reserve(
        inviterUserId: Long,
        email: String,
        count: Int,
    ) {
        if (!take(inviterKey(inviterUserId), count.toLong(), properties.maxPerInviterPerDay)) {
            throw InvitationRefusedException(null, "daily_limit")
        }
        if (!take(recipientKey(email), 1, properties.maxPerRecipientPerDay)) {
            values.decrement(inviterKey(inviterUserId), count.toLong())
            throw InvitationRefusedException(null, "recipient_daily_limit")
        }
    }

    override fun release(
        inviterUserId: Long,
        email: String,
        count: Int,
    ) {
        values.decrement(inviterKey(inviterUserId), count.toLong())
        values.decrement(recipientKey(email))
    }

    /** Adds [amount] unless that goes over [limit]; the counter's day starts with its first use */
    private fun take(
        key: String,
        amount: Long,
        limit: Int,
    ): Boolean {
        val total = values.increment(key, amount) ?: return false
        if (total == amount) redisTemplate.expire(key, WINDOW)
        if (total > limit) {
            values.decrement(key, amount)
            return false
        }
        return true
    }
}
