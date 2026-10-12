package com.invohub.backend.infrastructure.adapter.outbound.session

import com.invohub.backend.domain.common.TSIDCodec
import com.invohub.backend.support.IntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.session.Session
import org.springframework.session.SessionRepository
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@IntegrationTest
class RedisUserSessionsTest {
    @Autowired
    private lateinit var userSessions: RedisUserSessions

    @Autowired
    private lateinit var sessionRepository: SessionRepository<*>

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    private val userId = 42L
    private val key = "invohub:user-sessions:${TSIDCodec.encode(userId)}"

    @BeforeEach
    fun flushRedis() {
        redisTemplate.connectionFactory!!.connection.use { it.serverCommands().flushAll() }
    }

    @Test
    fun `ending all sessions deletes every registered session and the list`() {
        val laptop = newSession()
        val phone = newSession()
        userSessions.register(userId, laptop)
        userSessions.register(userId, phone)

        userSessions.endAll(userId)

        assertNull(sessionRepository.findById(laptop))
        assertNull(sessionRepository.findById(phone))
        assertEquals(false, redisTemplate.hasKey(key))
    }

    @Test
    fun `other users' sessions are untouched`() {
        val mine = newSession()
        val theirs = newSession()
        userSessions.register(userId, mine)
        userSessions.register(7, theirs)

        userSessions.endAll(userId)

        assertNotNull(sessionRepository.findById(theirs))
    }

    @Test
    fun `sessions that ended on their own are dropped at the next sign-in`() {
        val expired = newSession()
        userSessions.register(userId, expired)
        sessionRepository.deleteById(expired)

        val current = newSession()
        userSessions.register(userId, current)

        assertEquals(setOf(current), redisTemplate.opsForSet().members(key))
    }

    @Test
    fun `the list expires with the maximum session age`() {
        userSessions.register(userId, newSession())

        val ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS)

        assertTrue(ttl in Duration.ofDays(7).minusMinutes(1).seconds..Duration.ofDays(7).seconds, "TTL was ${ttl}s")
    }

    private fun newSession(): String {
        @Suppress("UNCHECKED_CAST")
        val repository = sessionRepository as SessionRepository<Session>
        val session = repository.createSession()
        repository.save(session)
        return session.id
    }
}
