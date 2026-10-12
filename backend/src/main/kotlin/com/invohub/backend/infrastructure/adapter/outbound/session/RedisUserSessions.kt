package com.invohub.backend.infrastructure.adapter.outbound.session

import com.invohub.backend.domain.auth.AuthProperties
import com.invohub.backend.domain.auth.UserSessions
import com.invohub.backend.domain.common.TSIDCodec
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.session.SessionRepository
import org.springframework.stereotype.Component

/**
 * Keeps a set of session IDs per user, `invohub:user-sessions:{public user ID}`. Session keys are named by a random
 * ID, so this is the only way to find a user's sessions without opening every session there is.
 *
 * Signing out removes the session's ID. Sessions also expire on their own, which the set doesn't notice, so
 * ended IDs are dropped at each sign-in too (and deleting an already-ended session is harmless anyway). This deliberately avoids Spring Session's indexed
 * repository, which tidies up through keyspace notifications and PSUBSCRIBE: ElastiCache Serverless allows
 * neither. Every command here touches a single key.
 */
@Component
class RedisUserSessions(
    private val redisTemplate: StringRedisTemplate,
    private val sessionRepository: SessionRepository<*>,
    private val properties: AuthProperties,
) : UserSessions {
    companion object {
        // The public ID: nothing stored outside the app uses internal IDs
        private fun key(userId: Long) = "invohub:user-sessions:${TSIDCodec.encode(userId)}"
    }

    private val sets = redisTemplate.opsForSet()

    override fun register(
        userId: Long,
        sessionId: String,
    ) {
        val key = key(userId)

        val ended = sets.members(key).orEmpty().filter { sessionRepository.findById(it) == null }
        if (ended.isNotEmpty()) {
            sets.remove(key, *ended.toTypedArray())
        }
        sets.add(key, sessionId)

        // No session lives longer than the maximum session age, so a set that outlives its newest session
        // by that much never loses track of a live one
        redisTemplate.expire(key, properties.sessionMaxAge)
    }

    override fun unregister(
        userId: Long,
        sessionId: String,
    ) {
        sets.remove(key(userId), sessionId)
    }

    override fun endAll(userId: Long) {
        val key = key(userId)
        sets.members(key).orEmpty().forEach(sessionRepository::deleteById)
        redisTemplate.delete(key)
    }
}
