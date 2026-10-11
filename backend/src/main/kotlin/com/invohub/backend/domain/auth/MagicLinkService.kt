package com.invohub.backend.domain.auth

import com.invohub.backend.domain.user.User
import com.invohub.backend.domain.user.UserService
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64

@Service
class MagicLinkService(
    private val redisTemplate: StringRedisTemplate,
    private val emailPort: EmailPort,
    private val userService: UserService,
) {
    companion object {
        private val TOKEN_TTL = Duration.ofHours(1)

        private fun getActiveKey(email: String) = "magic_link:active:$email"

        private fun getTokenKey(tokenHash: String) = "magic_link:token:$tokenHash"
    }

    /**
     * Attempts to create and store a magic link token. If a link is successfully
     * generated, an email is sent to the provided email.
     */
    fun requestToken(email: String) {
        val activeKey = getActiveKey(email)

        // If an active token already exists, do not regenerate or spam a new email
        if (redisTemplate.hasKey(activeKey) == true) {
            return
        }

        val secureRandom = SecureRandom()
        val bytes = ByteArray(32).apply { secureRandom.nextBytes(this) }
        val rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val tokenHash = hashToken(rawToken)

        val tokenKey = getTokenKey(tokenHash)

        // Anti-spam lock for email
        redisTemplate.opsForValue().set(activeKey, tokenHash, TOKEN_TTL)
        // Token to email map for verification
        redisTemplate.opsForValue().set(tokenKey, email, TOKEN_TTL)

        emailPort.sendMagicLink(email, rawToken)
    }

    /**
     * Verifies the token and deletes it.
     * @return Newly-created or pre-existing User associated with the token
     */
    fun verifyToken(rawToken: String): User? {
        val tokenHash = hashToken(rawToken)
        val tokenKey = getTokenKey(tokenHash)

        // Retrieve email associated with this token hash
        val email = redisTemplate.opsForValue().get(tokenKey) ?: return null

        val activeKey = getActiveKey(email)

        redisTemplate.delete(tokenKey)
        redisTemplate.delete(activeKey)

        return userService.getOrCreateUser(email)
    }

    private fun hashToken(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(token.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
