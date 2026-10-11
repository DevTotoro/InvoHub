package com.invohub.backend.domain.auth

/** The signed-in sessions of a user, so the domain can end them, e.g. when the user is suspended. */
interface UserSessions {
    /** Records that the session belongs to the user. Call once per sign-in. */
    fun register(
        userId: Long,
        sessionId: String,
    )

    /** Ends every session of the user, on every device. Their next request is treated as signed out. */
    fun endAll(userId: Long)
}
