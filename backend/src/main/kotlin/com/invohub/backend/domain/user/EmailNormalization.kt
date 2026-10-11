package com.invohub.backend.domain.user

/**
 * The canonical form emails are stored and looked up in. The database enforces it too
 * (ck_users_email_normalized), which is what makes uq_users_email effectively case-insensitive.
 */
fun normalizeEmail(email: String): String = email.trim().lowercase()
