package com.invohub.backend.infrastructure.adapter.inbound.web.auth

import com.invohub.backend.domain.auth.ExpiredSignInLinkException
import com.invohub.backend.domain.auth.InvalidSignInLinkException
import com.invohub.backend.domain.auth.LinkProblem
import com.invohub.backend.domain.auth.MagicLinkService
import com.invohub.backend.domain.auth.SignInDestination
import com.invohub.backend.domain.user.Language
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import org.springframework.http.ResponseEntity
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.web.bind.annotation.*

/** [language] is the one the person is using; the sign-in email is in it unless they have an account already */
data class MagicLinkRequestDto(
    @field:NotBlank @field:Email val email: String,
    val language: Language = Language.DEFAULT,
)

/** [language] is the one the person is using; signing in for the first time creates the account in it */
data class VerifyMagicLinkRequestDto(
    @field:NotBlank val token: String,
    val language: Language = Language.DEFAULT,
)

/** Where to go once signed in: `INVITATIONS` for a link from an invitation email, else `DEFAULT` */
data class VerifyMagicLinkResponseDto(
    val destination: SignInDestination,
)

/** [token] is the old link's; [language] the one in use, for someone who has no account yet */
data class RenewMagicLinkRequestDto(
    @field:NotBlank val token: String,
    val language: Language = Language.DEFAULT,
)

@RestController
@RequestMapping("/auth")
class AuthController(
    private val magicLinkService: MagicLinkService,
    private val sessionAuthenticator: SessionAuthenticator,
) {
    /** Issues the XSRF-TOKEN cookie. Call once before the first POST; the cookie is then sent back as a header. */
    @GetMapping("/csrf")
    fun csrfToken(token: CsrfToken): ResponseEntity<Void> {
        // The token is deferred; reading it is what makes the repository write the cookie
        token.token
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/request")
    fun requestMagicLink(
        @Valid @RequestBody request: MagicLinkRequestDto,
    ): ResponseEntity<Map<String, String>> {
        magicLinkService.requestToken(request.email, request.language)

        // Same response whether or not a link was sent just now, so it reveals nothing about the address
        return ResponseEntity.ok(mapOf("message" to "Check your inbox for a sign-in link."))
    }

    /**
     * POST, not GET: email security scanners prefetch links in emails, and a GET would let them consume the
     * single-use token before the user clicks. The link opens a frontend page that posts the token here.
     *
     * A link that doesn't sign in fails with `expired_link` if it expired or was used but can be renewed
     * (see /renew), else `invalid_link`.
     */
    @PostMapping("/verify")
    fun verifyMagicLink(
        @Valid @RequestBody request: VerifyMagicLinkRequestDto,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
    ): VerifyMagicLinkResponseDto {
        val signIn =
            magicLinkService.verifyToken(request.token, request.language)
                ?: when (magicLinkService.problemWith(request.token)) {
                    LinkProblem.RENEWABLE -> throw ExpiredSignInLinkException()
                    LinkProblem.INVALID -> throw InvalidSignInLinkException()
                }

        sessionAuthenticator.signIn(signIn.user, httpRequest, httpResponse)

        return VerifyMagicLinkResponseDto(signIn.destination)
    }

    /** Emails a fresh link in place of an expired or used one, to the same address and leading to the same place */
    @PostMapping("/renew")
    fun renewMagicLink(
        @Valid @RequestBody request: RenewMagicLinkRequestDto,
    ): ResponseEntity<Map<String, String>> {
        magicLinkService.renew(request.token, request.language)
        return ResponseEntity.ok(mapOf("message" to "Check your inbox for a sign-in link."))
    }
}
