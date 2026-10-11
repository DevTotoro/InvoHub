package com.invohub.backend.domain.auth

/** The user proved ownership of the email, but InvoHub has suspended the account. */
class AccountSuspendedException : RuntimeException("Account is suspended")
