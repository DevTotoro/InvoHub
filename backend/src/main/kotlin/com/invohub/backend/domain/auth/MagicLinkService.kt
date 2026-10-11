package com.invohub.backend.domain.auth

import com.invohub.backend.domain.user.User
import com.invohub.backend.domain.user.UserService
import com.invohub.backend.domain.user.normalizeEmail
import org.springframework.stereotype.Service
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.HexFormat

@Service
class MagicLinkService(
    private val tokenStore: MagicLinkTokenStore,
    private val emailPort: EmailPort,
    private val userService: UserService,
    private val properties: AuthProperties,
) {
    companion object {
        private const val TOKEN_BYTES = 32

        private val secureRandom = SecureRandom()
    }

    /**
     * Issues a new magic link token and emails it. While a token for this email is active, no new token is
     * issued and no email is sent, so the endpoint can't be used to flood someone's inbox.
     */
    fun requestToken(rawEmail: String) {
        val email = normalizeEmail(rawEmail)

        // Atomic, so concurrent requests can't both pass and send two links
        if (!tokenStore.tryLock(email, properties.magicLinkTtl)) {
            return
        }

        val rawToken = generateToken()
        val tokenHash = hashToken(rawToken)
        tokenStore.saveToken(tokenHash, email, properties.magicLinkTtl)

        try {
            emailPort.sendMagicLink(email, buildLink(rawToken), properties.magicLinkTtl)
        } catch (e: EmailDeliveryException) {
            // Nothing was sent, so release the lock rather than make the user wait out the full TTL
            tokenStore.removeToken(tokenHash)
            tokenStore.unlock(email)
            throw e
        }
    }

    /**
     * Consumes the token, so it works at most once even under concurrent requests.
     * @return Newly-created or pre-existing User associated with the token, or null if it is invalid or expired
     * @throws AccountSuspendedException if the user is suspended
     */
    fun verifyToken(rawToken: String): User? {
        val email = tokenStore.consumeToken(hashToken(rawToken)) ?: return null
        // The active token is used up, so allow requesting a new one (e.g. to sign in on another device)
        tokenStore.unlock(email)

        val user = userService.getOrCreateUser(email)
        if (user.suspended) {
            throw AccountSuspendedException()
        }
        return user
    }

    private fun buildLink(rawToken: String): URI =
        UriComponentsBuilder
            .fromUri(properties.frontendUrl)
            .path("/auth/verify")
            .queryParam("token", rawToken)
            .build()
            .toUri()

    private fun generateToken(): String {
        val bytes = ByteArray(TOKEN_BYTES).also(secureRandom::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hashToken(token: String): String {
        val hashBytes = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8))
        return HexFormat.of().formatHex(hashBytes)
    }
}
