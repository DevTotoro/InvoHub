package com.invohub.backend.infrastructure.adapter.inbound.web.auth

import com.invohub.backend.domain.auth.MagicLinkService
import com.invohub.backend.domain.user.UserService
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

data class MagicLinkRequestDto(
    @field:NotBlank @field:Email val email: String,
)

data class VerifyMagicLinkRequestDto(
    @field:NotBlank val token: String,
)

@RestController
@RequestMapping("/auth")
class AuthController(
    private val magicLinkService: MagicLinkService,
    private val userService: UserService,
) {
    @PostMapping("/request")
    fun requestMagicLink(
        @Valid @RequestBody request: MagicLinkRequestDto,
    ): ResponseEntity<Map<String, String>> {
        val normalizedEmail = request.email.lowercase()

        magicLinkService.requestToken(normalizedEmail)

        return ResponseEntity.ok(mapOf("message" to "If an account exists, a link has been sent."))
    }

    /**
     * POST, not GET: email security scanners prefetch links in emails, and a GET would let them consume the
     * single-use token before the user clicks. The link opens a frontend page that posts the token here.
     */
    @PostMapping("/verify")
    fun verifyMagicLink(
        @Valid @RequestBody request: VerifyMagicLinkRequestDto,
    ): ResponseEntity<Void> {
        val user =
            magicLinkService.verifyToken(request.token)
                ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        // TODO: Establish session / set secure HTTP-only cookie for the authenticated `user`

        return ResponseEntity.noContent().build()
    }
}
