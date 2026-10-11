package com.invohub.backend.domain.auth

import java.io.Serializable

/**
 * The principal stored in the session. Sessions live in Redis with Java serialization, so keep this small
 * and bump serialVersionUID when its shape changes (existing sessions then fail to load and users sign in again).
 */
data class AuthenticatedUser(
    val userId: Long,
    val email: String,
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
