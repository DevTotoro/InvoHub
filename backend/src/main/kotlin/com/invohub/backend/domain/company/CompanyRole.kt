package com.invohub.backend.domain.company

/** A user's role in a company. The owner is stored on the company itself; everyone else is a member. */
enum class CompanyRole {
    OWNER,
    ADMIN,
    MEMBER,
    VIEWER,
}
