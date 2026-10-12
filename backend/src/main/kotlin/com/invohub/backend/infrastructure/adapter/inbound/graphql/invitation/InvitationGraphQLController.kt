package com.invohub.backend.infrastructure.adapter.inbound.graphql.invitation

import com.invohub.backend.domain.auth.AuthenticatedUser
import com.invohub.backend.domain.common.TSIDCodec
import com.invohub.backend.domain.company.CompanyRole
import com.invohub.backend.domain.company.country.CountryRulesRegistry
import com.invohub.backend.domain.invitation.InvitationDetails
import com.invohub.backend.domain.invitation.InvitationNotFoundException
import com.invohub.backend.domain.invitation.InvitationService
import com.invohub.backend.domain.invitation.InvitationTarget
import com.invohub.backend.infrastructure.adapter.inbound.graphql.company.CompanyScope
import com.invohub.backend.infrastructure.adapter.inbound.graphql.company.CompanyView
import com.invohub.backend.infrastructure.adapter.inbound.graphql.company.UserCompanyView
import com.invohub.backend.infrastructure.adapter.inbound.graphql.user.UserView
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller

data class InvitationView(
    val id: String,
    val email: String,
    val role: CompanyRole,
    val company: CompanyView,
    val invitedBy: String,
    /** ISO-8601 */
    val expiresAt: String,
)

data class InvitationCompanyInput(
    val companyId: String,
    val role: CompanyRole,
)

data class InviteMembersInput(
    val email: String,
    val companies: List<InvitationCompanyInput>,
)

@Controller
class InvitationGraphQLController(
    private val invitationService: InvitationService,
    private val companyScope: CompanyScope,
    private val countryRules: CountryRulesRegistry,
) {
    /** Every company goes through CompanyScope, so the inviter needs access to each */
    @MutationMapping
    fun inviteMembers(
        @Argument input: InviteMembersInput,
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): List<InvitationView> {
        val targets = input.companies.map { InvitationTarget(companyScope.enter(principal, it.companyId), it.role) }
        val invitations = invitationService.invite(principal.userId, input.email, targets)
        val companies = targets.associate { it.access.companyId to it.access.company }
        return invitations.map {
            view(InvitationDetails(it, companies.getValue(it.companyId), principal.email))
        }
    }

    @QueryMapping
    fun invitations(
        @Argument companyId: String,
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): List<InvitationView> = invitationService.openFor(companyScope.enter(principal, companyId)).map(::view)

    @MutationMapping
    fun revokeInvitation(
        @Argument companyId: String,
        @Argument invitationId: String,
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): Boolean {
        invitationService.revoke(companyScope.enter(principal, companyId), invitationId(invitationId))
        return true
    }

    /** Only reachable through `me`: a user sees the invitations for their own email address */
    @SchemaMapping(typeName = "User", field = "invitations")
    fun myInvitations(user: UserView): List<InvitationView> = invitationService.openForUser(user.userId).map(::view)

    @MutationMapping
    fun acceptInvitation(
        @Argument invitationId: String,
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): UserCompanyView {
        val joined = invitationService.accept(principal.userId, invitationId(invitationId))
        return UserCompanyView(CompanyView.of(joined.company, countryRules), joined.role)
    }

    @MutationMapping
    fun declineInvitation(
        @Argument invitationId: String,
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): Boolean {
        invitationService.decline(principal.userId, invitationId(invitationId))
        return true
    }

    private fun view(details: InvitationDetails) =
        InvitationView(
            id = details.invitation.getPublicId(),
            email = details.invitation.email,
            role = details.invitation.role,
            company = CompanyView.of(details.company, countryRules),
            invitedBy = details.invitedByEmail,
            expiresAt = details.invitation.expiresAt.toString(),
        )

    private fun invitationId(publicId: String) =
        runCatching { TSIDCodec.decode(publicId) }.getOrNull() ?: throw InvitationNotFoundException()
}
