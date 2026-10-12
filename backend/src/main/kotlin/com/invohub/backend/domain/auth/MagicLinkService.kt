package com.invohub.backend.domain.auth

import com.invohub.backend.domain.audit.AuditAction
import com.invohub.backend.domain.audit.AuditEvent
import com.invohub.backend.domain.audit.AuditLog
import com.invohub.backend.domain.audit.AuditTarget
import com.invohub.backend.domain.user.Language
import com.invohub.backend.domain.user.User
import com.invohub.backend.domain.user.UserService
import com.invohub.backend.domain.user.normalizeEmail
import org.springframework.stereotype.Service
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64
import java.util.HexFormat

/** A verified sign-in link: who signed in, and where the link takes them */
data class SignIn(
    val user: User,
    val destination: SignInDestination,
)

/** A sign-in link to put in an email, and how long it works */
data class IssuedLink(
    val link: URI,
    val validFor: Duration,
)

/** What verifying a link found when it didn't sign anyone in */
enum class LinkProblem {
    /** Unknown: nothing can be done with it */
    INVALID,

    /** Expired or already used, but recent enough to be renewed */
    RENEWABLE,
}

@Service
class MagicLinkService(
    private val tokenStore: MagicLinkTokenStore,
    private val emailPort: EmailPort,
    private val userService: UserService,
    private val properties: AuthProperties,
    private val auditLog: AuditLog,
) {
    companion object {
        private const val TOKEN_BYTES = 32

        private val secureRandom = SecureRandom()
    }

    /**
     * Issues a new magic link token and emails it. While a token for this email is active, no new token is
     * issued and no email is sent, so the endpoint can't be used to flood someone's inbox.
     *
     * [language] is the one the person is using right now. The email is in the user's own language if they
     * already have an account, and in this one otherwise.
     */
    fun requestToken(
        rawEmail: String,
        language: Language,
    ) {
        sendLink(normalizeEmail(rawEmail), SignInDestination.DEFAULT, language)
    }

    /**
     * A sign-in link for an invitation email: it signs the person in and opens their Invitations page. Not
     * held back by the per-email lock, as the invitation email must go out; invitations have limits of their
     * own (InvitationLimits).
     */
    fun invitationLink(rawEmail: String): IssuedLink {
        val rawToken = issue(MagicLinkToken(normalizeEmail(rawEmail), SignInDestination.INVITATIONS))
        return IssuedLink(buildLink(rawToken), properties.magicLinkTtl)
    }

    /**
     * Emails a fresh link in place of an expired or used one: to the same address, leading to the same place.
     * Like requesting one, nothing is sent while a link for the address is active.
     * @throws InvalidSignInLinkException if the old link is unknown or too old to renew
     */
    fun renew(
        rawToken: String,
        language: Language,
    ) {
        val old = tokenStore.findRenewable(hashToken(rawToken)) ?: throw InvalidSignInLinkException()
        sendLink(old.email, old.destination, language)
    }

    /**
     * Consumes the token, so it works at most once even under concurrent requests. Signing in for the first
     * time creates the user, in [language] (the one the person is using right now).
     * @return the user and where the link leads, or null if it doesn't sign anyone in (see [problemWith])
     * @throws AccountSuspendedException if the user is suspended
     */
    fun verifyToken(
        rawToken: String,
        language: Language,
    ): SignIn? {
        val token = tokenStore.consumeToken(hashToken(rawToken)) ?: return null
        // The active token is used up, so allow requesting a new one (e.g. to sign in on another device)
        tokenStore.unlock(token.email)

        val user = userService.getOrCreateUser(token.email, language)
        if (user.suspended) {
            auditLog.recordStandalone(
                AuditEvent(
                    AuditAction.USER_SIGN_IN_REFUSED,
                    actorUserId = user.id,
                    target = AuditTarget.user(user.id),
                    details = mapOf("reason" to "suspended"),
                ),
            )
            throw AccountSuspendedException()
        }

        auditLog.recordStandalone(
            AuditEvent(AuditAction.USER_SIGNED_IN, actorUserId = user.id, target = AuditTarget.user(user.id)),
        )
        return SignIn(user, token.destination)
    }

    /** Why a link didn't sign anyone in: unknown, or expired/used and renewable */
    fun problemWith(rawToken: String): LinkProblem =
        if (tokenStore.findRenewable(hashToken(rawToken)) != null) LinkProblem.RENEWABLE else LinkProblem.INVALID

    /** Emails a new link unless one for the address is still active */
    private fun sendLink(
        email: String,
        destination: SignInDestination,
        language: Language,
    ) {
        // Atomic, so concurrent requests can't both pass and send two links
        if (!tokenStore.tryLock(email, properties.magicLinkTtl)) {
            return
        }

        val rawToken = generateToken()
        val tokenHash = hashToken(rawToken)
        tokenStore.saveToken(
            tokenHash,
            MagicLinkToken(email, destination),
            properties.magicLinkTtl,
            properties.magicLinkRenewableFor,
        )

        try {
            val emailLanguage = userService.findByEmail(email)?.language ?: language
            emailPort.sendMagicLink(email, buildLink(rawToken), properties.magicLinkTtl, emailLanguage)
        } catch (e: EmailDeliveryException) {
            // Nothing was sent, so release the lock rather than make the user wait out the full TTL
            tokenStore.removeToken(tokenHash)
            tokenStore.unlock(email)
            throw e
        }
    }

    private fun issue(token: MagicLinkToken): String {
        val rawToken = generateToken()
        tokenStore.saveToken(hashToken(rawToken), token, properties.magicLinkTtl, properties.magicLinkRenewableFor)
        return rawToken
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

/** The sign-in link's token is unknown, or too old to renew */
class InvalidSignInLinkException : RuntimeException("Invalid sign-in link")

/** The sign-in link has expired or was used, and can be renewed */
class ExpiredSignInLinkException : RuntimeException("Expired sign-in link")
