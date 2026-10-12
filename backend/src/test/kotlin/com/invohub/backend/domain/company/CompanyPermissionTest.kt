package com.invohub.backend.domain.company

import com.invohub.backend.domain.company.CompanyPermission.EDIT
import com.invohub.backend.domain.company.CompanyPermission.MANAGE_BILLING
import com.invohub.backend.domain.company.CompanyPermission.MANAGE_MEMBERS
import com.invohub.backend.domain.company.CompanyPermission.MANAGE_OWNERSHIP
import com.invohub.backend.domain.company.CompanyPermission.MANAGE_SETTINGS
import com.invohub.backend.domain.company.CompanyPermission.VIEW
import com.invohub.backend.domain.company.CompanyRole.ADMIN
import com.invohub.backend.domain.company.CompanyRole.MEMBER
import com.invohub.backend.domain.company.CompanyRole.OWNER
import com.invohub.backend.domain.company.CompanyRole.VIEWER
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/** The roles table in docs/roadmap.md, and what each company status allows. */
class CompanyPermissionTest {
    private val expected =
        mapOf(
            OWNER to setOf(VIEW, EDIT, MANAGE_SETTINGS, MANAGE_MEMBERS, MANAGE_BILLING, MANAGE_OWNERSHIP),
            ADMIN to setOf(VIEW, EDIT, MANAGE_SETTINGS, MANAGE_MEMBERS),
            MEMBER to setOf(VIEW, EDIT),
            VIEWER to setOf(VIEW),
        )

    @Test
    fun `each role has exactly its permissions in an active company`() {
        CompanyRole.entries.forEach { role ->
            val granted = CompanyPermission.entries.filter { access(role).can(it) }.toSet()
            assertEquals(expected.getValue(role), granted, "$role")
        }
    }

    @Test
    fun `a read-only company can still be viewed, and its billing managed by the owner`() {
        val owner = access(OWNER, CompanyStatus.READ_ONLY)

        assertEquals(setOf(VIEW, MANAGE_BILLING), CompanyPermission.entries.filter(owner::can).toSet())
        assertEquals(setOf(VIEW), CompanyPermission.entries.filter(access(ADMIN, CompanyStatus.READ_ONLY)::can).toSet())
    }

    @Test
    fun `a suspended company allows nothing, not even the owner`() {
        assertEquals(emptyList(), CompanyPermission.entries.filter(access(OWNER, CompanyStatus.SUSPENDED)::can))
    }

    @Test
    fun `require says why it refuses`() {
        assertFailsWith<CompanyPermissionDeniedException> { access(VIEWER).require(EDIT) }
        assertFailsWith<CompanyReadOnlyException> { access(OWNER, CompanyStatus.READ_ONLY).require(EDIT) }
        // Suspension comes first: it applies whatever the role
        assertFailsWith<CompanySuspendedException> { access(VIEWER, CompanyStatus.SUSPENDED).require(EDIT) }
    }

    @Test
    fun `require hands back the access when allowed`() {
        val access = access(MEMBER)

        assertSame(access, access.require(EDIT))
    }

    private fun access(
        role: CompanyRole,
        status: CompanyStatus = CompanyStatus.ACTIVE,
    ) = CompanyAccess(
        Company("Acme", "GR", "EUR", ownerUserId = 1).apply { changeStatus(status) },
        userId = 1,
        role = role,
    )
}
