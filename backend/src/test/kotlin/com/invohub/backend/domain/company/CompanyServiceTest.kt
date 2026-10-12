package com.invohub.backend.domain.company

import com.invohub.backend.domain.audit.AuditEventRepository
import com.invohub.backend.domain.common.InvalidInputException
import com.invohub.backend.domain.user.UserService
import com.invohub.backend.support.AuditEvents
import com.invohub.backend.support.IntegrationTest
import io.hypersistence.tsid.TSID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@IntegrationTest
class CompanyServiceTest {
    @Autowired
    private lateinit var companyService: CompanyService

    @Autowired
    private lateinit var memberRepository: CompanyMemberRepository

    @Autowired
    private lateinit var userService: UserService

    @Autowired
    private lateinit var auditEventRepository: AuditEventRepository

    private val events by lazy { AuditEvents(auditEventRepository) }

    @Test
    fun `creates a company owned by the user, with the currency of its country`() {
        val owner = newUser()

        val company = companyService.createCompany(owner, "  Acme  ", "gr")

        assertEquals("Acme", company.name)
        assertEquals("GR", company.country)
        assertEquals("EUR", company.currency)
        assertEquals(owner, company.ownerUserId)
    }

    @Test
    fun `allows countries InvoHub doesn't support invoicing in yet`() {
        val company = companyService.createCompany(newUser(), "Acme GmbH", "DE")

        assertEquals("EUR", company.currency)
    }

    @Test
    fun `records company created`() {
        val company = companyService.createCompany(newUser(), "Acme", "GR")

        assertEquals(listOf("company.created"), events.forCompany(company.id))
    }

    @Test
    fun `refuses a blank or too long name and an unknown country`() {
        val owner = newUser()

        assertInvalid("name", "required") { companyService.createCompany(owner, "   ", "GR") }
        assertInvalid("name", "too_long") { companyService.createCompany(owner, "x".repeat(201), "GR") }
        assertInvalid("country", "unknown") { companyService.createCompany(owner, "Acme", "XX") }
        assertInvalid("country", "unknown") { companyService.createCompany(owner, "Acme", "AQ") }
    }

    @Test
    fun `lists the user's companies by name, with their role in each`() {
        val user = newUser()
        val someoneElse = newUser()
        val owned = companyService.createCompany(user, "Zeta", "GR")
        val joined = companyService.createCompany(someoneElse, "alpha", "GR")
        memberRepository.save(CompanyMember(joined.id, user, CompanyRole.VIEWER))
        companyService.createCompany(someoneElse, "Not mine", "GR")

        val companies = companyService.companiesOf(user)

        assertEquals(
            listOf(joined.id to CompanyRole.VIEWER, owned.id to CompanyRole.OWNER),
            companies.map {
                it.company.id to
                    it.role
            },
        )
    }

    private fun newUser() = userService.getOrCreateUser("user-${TSID.fast()}@example.com").id

    private fun assertInvalid(
        field: String,
        reason: String,
        action: () -> Unit,
    ) {
        val e = assertFailsWith<InvalidInputException> { action() }
        assertEquals(field to reason, e.field to e.reason)
    }
}
