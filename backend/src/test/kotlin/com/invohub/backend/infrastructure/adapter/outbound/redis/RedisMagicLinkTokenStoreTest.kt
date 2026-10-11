package com.invohub.backend.infrastructure.adapter.outbound.redis

import com.invohub.backend.support.IntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@IntegrationTest
class RedisMagicLinkTokenStoreTest {
    @Autowired
    private lateinit var store: RedisMagicLinkTokenStore

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    private val email = "jane@example.com"
    private val ttl = Duration.ofMinutes(5)

    @BeforeEach
    fun flushRedis() {
        redisTemplate.connectionFactory!!.connection.use { it.serverCommands().flushAll() }
    }

    @Test
    fun `only one concurrent request can take the lock`() {
        val results = concurrently(16) { store.tryLock(email, ttl) }

        assertEquals(1, results.count { it })
    }

    @Test
    fun `lock can be taken again once released`() {
        assertTrue(store.tryLock(email, ttl))
        assertFalse(store.tryLock(email, ttl))

        store.unlock(email)

        assertTrue(store.tryLock(email, ttl))
    }

    @Test
    fun `only one concurrent request can consume a token`() {
        store.saveToken("hash", email, ttl)

        val results = concurrently(16) { store.consumeToken("hash") }

        assertEquals(listOf(email), results.filterNotNull())
    }

    @Test
    fun `removing a token invalidates it`() {
        store.saveToken("hash", email, ttl)

        store.removeToken("hash")

        assertNull(store.consumeToken("hash"))
    }

    @Test
    fun `keys expire`() {
        store.tryLock(email, ttl)
        store.saveToken("hash", email, ttl)

        redisTemplate.keys("magic_link:*").forEach { key ->
            assertTrue(redisTemplate.getExpire(key) > 0, "$key has no TTL")
        }
    }

    private fun <T> concurrently(
        threads: Int,
        action: () -> T,
    ): List<T> =
        Executors.newFixedThreadPool(threads).use { pool ->
            pool.invokeAll(List(threads) { Callable(action) }).map { it.get() }
        }
}
