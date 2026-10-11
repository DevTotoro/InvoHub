package com.invohub.backend.domain.auth

/** The user proved ownership of the email, but the account is locked. */
class AccountLockedException : RuntimeException("Account is locked")
