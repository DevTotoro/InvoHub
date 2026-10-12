package com.invohub.backend.domain.company

/**
 * A signed-in user's access to one company: their role in it and the company's status, as resolved by
 * [CompanyAccessService]. Company-scoped operations take one of these and [require] the permission they need,
 * so none can run without the access having been checked.
 */
class CompanyAccess internal constructor(
    val company: Company,
    val userId: Long,
    val role: CompanyRole,
) {
    val companyId: Long get() = company.id

    fun can(permission: CompanyPermission) = permission.grantedTo(role) && permission.allowedIn(company.status)

    /**
     * @throws CompanySuspendedException if the company is suspended
     * @throws CompanyReadOnlyException if the company is read-only and [permission] would change it
     * @throws CompanyPermissionDeniedException if the user's role doesn't have [permission]
     */
    fun require(permission: CompanyPermission): CompanyAccess {
        when {
            company.status == CompanyStatus.SUSPENDED -> throw CompanySuspendedException()
            !permission.allowedIn(company.status) -> throw CompanyReadOnlyException()
            !permission.grantedTo(role) -> throw CompanyPermissionDeniedException(permission)
        }
        return this
    }
}

/**
 * The company doesn't exist, or the user has no access to it. The two are deliberately not told apart, so
 * nobody can find out which companies exist.
 */
class CompanyNotFoundException : RuntimeException("Company not found")

class CompanyPermissionDeniedException(
    val permission: CompanyPermission,
) : RuntimeException("Missing permission $permission")

class CompanyReadOnlyException : RuntimeException("The company is read-only")

class CompanySuspendedException : RuntimeException("The company is suspended")
