package com.invohub.backend.infrastructure.adapter.inbound.graphql.company

import com.invohub.backend.domain.auth.AuthenticatedUser
import com.invohub.backend.domain.common.TSIDCodec
import com.invohub.backend.domain.company.MemberNotFoundException
import com.invohub.backend.domain.company.OwnershipService
import com.invohub.backend.domain.company.OwnershipTransferDetails
import com.invohub.backend.domain.company.OwnershipTransferNotFoundException
import com.invohub.backend.domain.company.country.CountryRulesRegistry
import com.invohub.backend.infrastructure.adapter.inbound.graphql.user.UserView
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller

data class OwnershipTransferView(
    val id: String,
    val company: CompanyView,
    val from: String,
    val to: String,
    /** ISO-8601 */
    val expiresAt: String,
)

/** Transferring ownership, closing and reopening: the owner's decisions */
@Controller
class OwnershipGraphQLController(
    private val ownershipService: OwnershipService,
    private val companyScope: CompanyScope,
    private val countryRules: CountryRulesRegistry,
) {
    @QueryMapping
    fun ownershipTransfer(
        @Argument companyId: String,
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): OwnershipTransferView? = ownershipService.pendingFor(companyScope.enter(principal, companyId))?.let(::view)

    @MutationMapping
    fun offerOwnership(
        @Argument companyId: String,
        @Argument userId: String,
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): OwnershipTransferView {
        val toUserId = runCatching { TSIDCodec.decode(userId) }.getOrNull() ?: throw MemberNotFoundException()
        return view(ownershipService.offer(companyScope.enter(principal, companyId), toUserId))
    }

    @MutationMapping
    fun cancelOwnershipTransfer(
        @Argument companyId: String,
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): Boolean {
        ownershipService.cancel(companyScope.enter(principal, companyId))
        return true
    }

    /** Only reachable through `me`: offers made to the signed-in user */
    @SchemaMapping(typeName = "User", field = "ownershipOffers")
    fun myOwnershipOffers(user: UserView): List<OwnershipTransferView> =
        ownershipService.openForUser(user.userId).map(::view)

    @MutationMapping
    fun acceptOwnership(
        @Argument transferId: String,
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): UserCompanyView {
        val owned = ownershipService.accept(principal.userId, transferId(transferId))
        return UserCompanyView(CompanyView.of(owned.company, countryRules), owned.role)
    }

    @MutationMapping
    fun declineOwnership(
        @Argument transferId: String,
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): Boolean {
        ownershipService.decline(principal.userId, transferId(transferId))
        return true
    }

    @MutationMapping
    fun closeCompany(
        @Argument companyId: String,
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): CompanyView = CompanyView.of(ownershipService.close(companyScope.enter(principal, companyId)), countryRules)

    @MutationMapping
    fun reopenCompany(
        @Argument companyId: String,
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): CompanyView = CompanyView.of(ownershipService.reopen(companyScope.enter(principal, companyId)), countryRules)

    private fun view(details: OwnershipTransferDetails) =
        OwnershipTransferView(
            id = details.transfer.getPublicId(),
            company = CompanyView.of(details.company, countryRules),
            from = details.fromEmail,
            to = details.toEmail,
            expiresAt = details.transfer.expiresAt.toString(),
        )

    private fun transferId(publicId: String) =
        runCatching { TSIDCodec.decode(publicId) }.getOrNull() ?: throw OwnershipTransferNotFoundException()
}
