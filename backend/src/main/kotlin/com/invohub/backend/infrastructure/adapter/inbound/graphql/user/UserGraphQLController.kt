package com.invohub.backend.infrastructure.adapter.inbound.graphql.user

import com.invohub.backend.domain.auth.AuthenticatedUser
import com.invohub.backend.domain.user.User
import com.invohub.backend.domain.user.UserService
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller

data class UserView(
    val id: String,
    val email: String,
    /** The internal ID, for resolving the user's other fields; not part of the schema */
    val userId: Long,
) {
    companion object {
        fun from(user: User) = UserView(user.getPublicId(), user.email, user.id)
    }
}

@Controller
class UserGraphQLController(
    private val userService: UserService,
) {
    /** Null if the session outlived the user it belongs to. */
    @QueryMapping
    fun me(
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): UserView? = userService.findById(principal.userId)?.let(UserView::from)
}
