package com.invohub.backend.infrastructure.adapter.inbound.web.company

import com.invohub.backend.domain.auth.AuthenticatedUser
import com.invohub.backend.domain.company.LogoService
import com.invohub.backend.infrastructure.adapter.inbound.graphql.company.CompanyScope
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.time.Duration

/**
 * A company's logo, as a file: uploading is simpler over plain HTTP than through GraphQL. The same access checks
 * as everywhere else (CompanyScope): anyone in the company sees it, the owner and admins change it.
 */
@RestController
@RequestMapping("/companies/{companyId}/logo")
class LogoController(
    private val logoService: LogoService,
    private val companyScope: CompanyScope,
) {
    @GetMapping
    fun logo(
        @PathVariable companyId: String,
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): ResponseEntity<ByteArray> {
        val logo =
            logoService.logo(companyScope.enter(principal, companyId)) ?: return ResponseEntity.notFound().build()
        return ResponseEntity
            .ok()
            .contentType(MediaType.parseMediaType(logo.contentType))
            // Its URL changes with the logo (?v=…), so it can be kept; private, as it's behind a session
            .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePrivate())
            .body(logo.bytes)
    }

    @PutMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(
        @PathVariable companyId: String,
        @RequestParam("file") file: MultipartFile,
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): ResponseEntity<Void> {
        logoService.change(companyScope.enter(principal, companyId), file.bytes)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping
    fun remove(
        @PathVariable companyId: String,
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): ResponseEntity<Void> {
        logoService.remove(companyScope.enter(principal, companyId))
        return ResponseEntity.noContent().build()
    }
}
