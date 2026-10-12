package com.invohub.backend.domain.company

import com.invohub.backend.domain.user.Language
import com.invohub.backend.domain.user.UserService
import com.invohub.backend.support.IntegrationTest
import io.hypersistence.tsid.TSID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@IntegrationTest
class CompanyAccessServiceTest {
    @Autowired
    private lateinit var accessService: CompanyAccessService

    @Autowired
    private lateinit var companyService: CompanyService

    @Autowired
    private lateinit var memberRepository: CompanyMemberRepository

    @Autowired
    private lateinit var userService: UserService

    @Test
    fun `the owner has the owner role`() {
        val owner = newUser()
        val company = companyService.createCompany(owner, "Acme", "GR")

        val access = accessService.resolve(owner, company.id)

        assertEquals(CompanyRole.OWNER, access.role)
        assertEquals(company.id, access.companyId)
    }

    @Test
    fun `a member has their membership's role`() {
        val company = companyService.createCompany(newUser(), "Acme", "GR")
        val viewer = newUser()
        memberRepository.save(CompanyMember(company.id, viewer, CompanyRole.VIEWER))

        assertEquals(CompanyRole.VIEWER, accessService.resolve(viewer, company.id).role)
    }

    @Test
    fun `someone without a membership is told the company doesn't exist`() {
        val company = companyService.createCompany(newUser(), "Acme", "GR")

        assertFailsWith<CompanyNotFoundException> { accessService.resolve(newUser(), company.id) }
    }

    @Test
    fun `a membership in one company gives nothing in another`() {
        val owner = newUser()
        val mine = companyService.createCompany(owner, "Acme", "GR")
        val other = companyService.createCompany(newUser(), "Globex", "GR")
        val member = newUser()
        memberRepository.save(CompanyMember(mine.id, member, CompanyRole.ADMIN))

        assertFailsWith<CompanyNotFoundException> { accessService.resolve(member, other.id) }
    }

    @Test
    fun `an unknown company is not found`() {
        assertFailsWith<CompanyNotFoundException> { accessService.resolve(newUser(), TSID.fast().toLong()) }
    }

    private fun newUser() = userService.getOrCreateUser("user-${TSID.fast()}@example.com", Language.EN).id
}
