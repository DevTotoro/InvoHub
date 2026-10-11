package com.invohub.backend.domain.auth

import com.invohub.backend.domain.user.User
import com.invohub.backend.domain.user.UserService
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64
import java.util.HexFormat

@Service
class MagicLinkService(
    private val tokenStore: MagicLinkTokenStore,
    private val emailPort: EmailPort,
    private val userService: UserService,
) {
    companion object {
        private val TOKEN_TTL = Duration.ofHours(1)
        private val RESEND_COOLDOWN = Duration.ofSeconds(60)
        private const val TOKEN_BYTES = 32

        private val secureRandom = SecureRandom()
    }

    /**
     * Issues a new magic link token and emails it, unless one was sent to this email within the resend
     * cooldown. A new token invalidates any earlier one for the same email.
     */
    fun requestToken(email: String) {
        // Atomic, so concurrent requests can't both pass and send two links
        if (!tokenStore.tryStartCooldown(email, RESEND_COOLDOWN)) {
            return
        }

        val rawToken = generateToken()
        val tokenHash = hashToken(rawToken)
        tokenStore.replaceToken(email, tokenHash, TOKEN_TTL)

        try {
            emailPort.sendMagicLink(email, rawToken)
        } catch (e: EmailDeliveryException) {
            // Roll back, so the user isn't left holding a token they never received and can retry right away
            tokenStore.removeToken(email, tokenHash)
            tokenStore.clearCooldown(email)
            throw e
        }
    }

    /**
     * Consumes the token, so it works at most once even under concurrent requests.
     * @return Newly-created or pre-existing User associated with the token, or null if it is invalid or expired
     */
    fun verifyToken(rawToken: String): User? {
        val email = tokenStore.consumeToken(hashToken(rawToken)) ?: return null

        return userService.getOrCreateUser(email)
    }

    private fun generateToken(): String {
        val bytes = ByteArray(TOKEN_BYTES).also(secureRandom::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hashToken(token: String): String {
        val hashBytes = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8))
        return HexFormat.of().formatHex(hashBytes)
    }
}
