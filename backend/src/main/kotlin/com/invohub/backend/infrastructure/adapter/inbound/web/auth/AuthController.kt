package com.invohub.backend.infrastructure.adapter.inbound.web.auth

import com.invohub.backend.domain.auth.MagicLinkService
import com.invohub.backend.domain.user.UserService
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

data class MagicLinkRequestDto(
    @field:NotBlank @field:Email val email: String,
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

    @GetMapping("/verify")
    fun verifyMagicLink(
        @RequestParam token: String,
    ): ResponseEntity<Void> {
        val user = magicLinkService.verifyToken(token) ?: return ResponseEntity.status(401).build()

        // TODO: Establish session / set secure HTTP-only cookie for the authenticated `user`

        return ResponseEntity
            .status(302)
            .header("Location", "http://localhost:5173/dashboard")
            .build()
    }
}
