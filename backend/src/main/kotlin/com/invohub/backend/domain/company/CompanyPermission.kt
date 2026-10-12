package com.invohub.backend.domain.company

/**
 * What someone may do in a company. Every company-scoped operation names the one it needs, and
 * [CompanyAccess.require] checks it against the person's role and the company's status.
 */
enum class CompanyPermission(
    /** The roles that have it */
    private val roles: Set<CompanyRole>,
    /** Still allowed while the company is read-only */
    private val allowedWhenReadOnly: Boolean = false,
) {
    /** See the company and everything in it, and export */
    VIEW(CompanyRole.entries.toSet(), allowedWhenReadOnly = true),

    /** Create and edit invoices, customers, … */
    EDIT(setOf(CompanyRole.OWNER, CompanyRole.ADMIN, CompanyRole.MEMBER)),

    /** The company's details, bank details, logo */
    MANAGE_SETTINGS(setOf(CompanyRole.OWNER, CompanyRole.ADMIN)),

    /** Invite, remove and change the roles of people (never the owner's) */
    MANAGE_MEMBERS(setOf(CompanyRole.OWNER, CompanyRole.ADMIN)),

    /** Paying is how a read-only company becomes active again, so it stays possible */
    MANAGE_BILLING(setOf(CompanyRole.OWNER), allowedWhenReadOnly = true),

    /** Transfer ownership, delete the company */
    MANAGE_OWNERSHIP(setOf(CompanyRole.OWNER)),
    ;

    fun grantedTo(role: CompanyRole) = role in roles

    fun allowedIn(status: CompanyStatus) =
        when (status) {
            CompanyStatus.ACTIVE -> true
            CompanyStatus.READ_ONLY -> allowedWhenReadOnly
            CompanyStatus.SUSPENDED -> false
        }
}
