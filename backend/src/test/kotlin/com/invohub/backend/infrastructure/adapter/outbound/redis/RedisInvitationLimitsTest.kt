package com.invohub.backend.infrastructure.adapter.outbound.redis

import com.invohub.backend.domain.common.TSIDCodec
import com.invohub.backend.domain.invitation.InvitationRefusedException
import com.invohub.backend.support.IntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import java.security.MessageDigest
import java.time.Duration
import java.util.HexFormat
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@IntegrationTest
class RedisInvitationLimitsTest {
    @Autowired
    private lateinit var limits: RedisInvitationLimits

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    @BeforeEach
    fun flushRedis() {
        redisTemplate.connectionFactory!!.connection.use { it.serverCommands().flushAll() }
    }

    @Test
    fun `a refused reservation counts nothing`() {
        limits.reserve(1, "maria@example.com", 99)

        assertFailsWith<InvitationRefusedException> { limits.reserve(1, "john@example.com", 2) }

        // Still exactly one left
        limits.reserve(1, "john@example.com", 1)
        assertFailsWith<InvitationRefusedException> { limits.reserve(1, "anna@example.com", 1) }
    }

    @Test
    fun `refusing for the recipient gives the inviter's allowance back`() {
        repeat(10) { limits.reserve(100L + it, "maria@example.com", 1) }

        val refused = assertFailsWith<InvitationRefusedException> { limits.reserve(1, "maria@example.com", 50) }

        assertEquals("recipient_daily_limit", refused.reason)
        limits.reserve(1, "john@example.com", 100)
    }

    @Test
    fun `released reservations can be used again`() {
        limits.reserve(1, "maria@example.com", 100)

        limits.release(1, "maria@example.com", 100)

        limits.reserve(1, "maria@example.com", 100)
    }

    @Test
    fun `counters last a day, and no addresses are stored`() {
        limits.reserve(1, "maria@example.com", 1)

        // No KEYS here: ElastiCache Serverless doesn't allow it, so the test Redis doesn't either
        val sha256 =
            HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest("maria@example.com".toByteArray()),
            )
        val keys = listOf("invohub:invitations:by:${TSIDCodec.encode(1)}", "invohub:invitations:to:$sha256")
        keys.forEach { key ->
            assertTrue(redisTemplate.getExpire(key) in Duration.ofHours(23).seconds..Duration.ofDays(1).seconds, key)
        }
    }
}
