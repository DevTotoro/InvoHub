package com.invohub.backend.domain.auth

import org.springframework.security.core.AuthenticatedPrincipal
import java.io.Serializable

/**
 * The principal stored in the session. Sessions live in Redis with Java serialization, so keep this small
 * and bump serialVersionUID when its shape changes (existing sessions then fail to load and users sign in again).
 *
 * Its name (Spring Security's authentication name, e.g. in logs) is the user ID.
 */
data class AuthenticatedUser(
    val userId: Long,
    val email: String,
) : AuthenticatedPrincipal,
    Serializable {
    override fun getName(): String = userId.toString()

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
