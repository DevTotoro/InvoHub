package com.invohub.backend.domain.auth

import com.invohub.backend.domain.common.TSIDCodec
import com.invohub.backend.domain.user.User
import org.springframework.security.core.AuthenticatedPrincipal
import java.io.Serializable

/**
 * The principal stored in the session. Sessions live in Redis with Java serialization, so keep this small
 * and bump serialVersionUID when its shape changes (existing sessions then fail to load and users sign in again).
 *
 * It holds the user's public ID, like everything stored outside the app; the internal ID is derived from it
 * when needed. Its name (Spring Security's authentication name, e.g. in logs) is the public ID too.
 */
data class AuthenticatedUser(
    val publicId: String,
    val email: String,
) : AuthenticatedPrincipal,
    Serializable {
    /** The internal ID, for use inside the app only. Not stored: derived from [publicId]. */
    val userId: Long
        get() = TSIDCodec.decode(publicId)

    override fun getName(): String = publicId

    companion object {
        private const val serialVersionUID: Long = 2L

        fun of(user: User) = AuthenticatedUser(user.getPublicId(), user.email)
    }
}
