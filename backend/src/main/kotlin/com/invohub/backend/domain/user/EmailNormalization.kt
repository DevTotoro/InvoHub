package com.invohub.backend.domain.user

/**
 * The canonical form emails are stored and looked up in. The database enforces it too
 * (ck_users_email_normalized), which is what makes uq_users_email effectively case-insensitive.
 */
fun normalizeEmail(email: String): String = email.trim().lowercase()

private val EMAIL_ADDRESS = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

/** Looks like an email address: something@something.something, no spaces. Whether it works is another matter. */
fun isEmailAddress(email: String): Boolean = EMAIL_ADDRESS.matches(email)
