package com.invohub.backend.infrastructure.adapter.inbound.graphql.member

import com.invohub.backend.domain.auth.AuthenticatedUser
import com.invohub.backend.domain.common.TSIDCodec
import com.invohub.backend.domain.company.CompanyPerson
import com.invohub.backend.domain.company.CompanyRole
import com.invohub.backend.domain.company.MemberNotFoundException
import com.invohub.backend.domain.company.MemberService
import com.invohub.backend.infrastructure.adapter.inbound.graphql.company.CompanyScope
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller

data class MemberView(
    val userId: String,
    val email: String,
    val role: CompanyRole,
    /** ISO-8601 */
    val since: String,
) {
    companion object {
        fun from(person: CompanyPerson) =
            MemberView(TSIDCodec.encode(person.userId), person.email, person.role, person.since.toString())
    }
}

@Controller
class MemberGraphQLController(
    private val memberService: MemberService,
    private val companyScope: CompanyScope,
) {
    @QueryMapping
    fun members(
        @Argument companyId: String,
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): List<MemberView> = memberService.people(companyScope.enter(principal, companyId)).map(MemberView::from)

    @MutationMapping
    fun changeMemberRole(
        @Argument companyId: String,
        @Argument userId: String,
        @Argument role: CompanyRole,
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): MemberView {
        val access = companyScope.enter(principal, companyId)
        return MemberView.from(memberService.changeRole(access, memberId(userId), role))
    }

    @MutationMapping
    fun removeMember(
        @Argument companyId: String,
        @Argument userId: String,
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): Boolean {
        memberService.remove(companyScope.enter(principal, companyId), memberId(userId))
        return true
    }

    @MutationMapping
    fun leaveCompany(
        @Argument companyId: String,
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): Boolean {
        memberService.leave(companyScope.enter(principal, companyId))
        return true
    }

    private fun memberId(publicId: String) =
        runCatching { TSIDCodec.decode(publicId) }.getOrNull() ?: throw MemberNotFoundException()
}
