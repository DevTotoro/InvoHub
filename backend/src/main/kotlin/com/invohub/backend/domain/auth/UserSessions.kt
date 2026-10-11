package com.invohub.backend.domain.auth

/** The signed-in sessions of a user, so the domain can end them, e.g. when the account is locked. */
interface UserSessions {
    /** Ends every session of the user, on every device. Their next request is treated as signed out. */
    fun endAll(userId: Long)
}
