package com.invohub.backend.infrastructure.adapter.inbound.graphql.company

import com.invohub.backend.domain.common.TSIDCodec
import com.invohub.backend.domain.company.CompanyMember
import com.invohub.backend.domain.company.CompanyMemberRepository
import com.invohub.backend.domain.company.CompanyRepository
import com.invohub.backend.domain.company.CompanyRole
import com.invohub.backend.domain.company.CompanyStatus
import com.invohub.backend.domain.user.UserRepository
import com.invohub.backend.support.CapturingEmailPort
import com.invohub.backend.support.DatabaseCleaner
import com.invohub.backend.support.IntegrationTest
import com.invohub.backend.support.graphql
import com.invohub.backend.support.signIn
import com.jayway.jsonpath.JsonPath
import jakarta.servlet.http.Cookie
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.web.servlet.MockMvc

@IntegrationTest
@AutoConfigureMockMvc
class CompanyGraphQLTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var emailPort: CapturingEmailPort

    @Autowired
    private lateinit var databaseCleaner: DatabaseCleaner

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    @Autowired
    private lateinit var companyRepository: CompanyRepository

    @Autowired
    private lateinit var memberRepository: CompanyMemberRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    private val myCompanies = "{ me { companies { role company { id name country currency invoicingSupported } } } }"
    private val createCompany =
        """
        mutation Create(${'$'}input: CreateCompanyInput!) {
            createCompany(input: ${'$'}input) { id name country currency invoicingSupported }
        }
        """.trimIndent()

    @BeforeEach
    fun reset() {
        emailPort.clear()
        databaseCleaner.clean()
        redisTemplate.connectionFactory!!.connection.use { it.serverCommands().flushAll() }
    }

    @Test
    fun `a new user has no companies`() {
        val session = mockMvc.signIn(emailPort, "jane@example.com")

        mockMvc.graphql(session, myCompanies).andExpect {
            jsonPath("$.data.me.companies") { isEmpty() }
        }
    }

    @Test
    fun `creating a company makes the user its owner`() {
        val session = mockMvc.signIn(emailPort, "jane@example.com")

        create(session, "Acme", "GR").andExpect {
            jsonPath("$.data.createCompany.name") { value("Acme") }
            jsonPath("$.data.createCompany.currency") { value("EUR") }
            jsonPath("$.data.createCompany.invoicingSupported") { value(true) }
            jsonPath("$.data.createCompany.id") { isNotEmpty() }
        }

        mockMvc.graphql(session, myCompanies).andExpect {
            jsonPath("$.data.me.companies.length()") { value(1) }
            jsonPath("$.data.me.companies[0].role") { value("OWNER") }
            jsonPath("$.data.me.companies[0].company.name") { value("Acme") }
        }
    }

    @Test
    fun `a company in a country without invoicing support can still be created`() {
        val session = mockMvc.signIn(emailPort, "jane@example.com")

        create(session, "Acme GmbH", "DE").andExpect {
            jsonPath("$.data.createCompany.currency") { value("EUR") }
            jsonPath("$.data.createCompany.invoicingSupported") { value(false) }
        }
    }

    @Test
    fun `other people's companies are not listed`() {
        create(mockMvc.signIn(emailPort, "jane@example.com"), "Acme", "GR")

        mockMvc.graphql(mockMvc.signIn(emailPort, "john@example.com"), myCompanies).andExpect {
            jsonPath("$.data.me.companies") { isEmpty() }
        }
    }

    @Test
    fun `invalid input is reported with the field and the reason`() {
        val session = mockMvc.signIn(emailPort, "jane@example.com")

        create(session, "  ", "GR").andExpect {
            jsonPath("$.errors[0].extensions.code") { value("invalid_input") }
            jsonPath("$.errors[0].extensions.field") { value("name") }
            jsonPath("$.errors[0].extensions.reason") { value("required") }
            jsonPath("$.data") { doesNotExist() }
        }
        create(session, "Acme", "XX").andExpect {
            jsonPath("$.errors[0].extensions.field") { value("country") }
            jsonPath("$.errors[0].extensions.reason") { value("unknown") }
        }
    }

    @Test
    fun `countries come with their currency and whether invoicing is supported`() {
        val session = mockMvc.signIn(emailPort, "jane@example.com")

        mockMvc.graphql(session, "{ countries { code currency invoicingSupported } }").andExpect {
            jsonPath("$.data.countries[?(@.code == 'GR')].currency") { value(hasItem("EUR")) }
            jsonPath("$.data.countries[?(@.code == 'GR')].invoicingSupported") { value(hasItem(true)) }
            jsonPath("$.data.countries[?(@.code == 'DE')].invoicingSupported") { value(hasItem(false)) }
            jsonPath("$.data.countries[*].code") { value(not(hasItem("AQ"))) }
        }
    }

    @Test
    fun `creating a company needs a session`() {
        create(session = null, name = "Acme", country = "GR").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `the owner opens their company`() {
        val session = mockMvc.signIn(emailPort, "jane@example.com")
        val id = createdId(session, "Acme")

        company(session, id).andExpect {
            jsonPath("$.data.company.role") { value("OWNER") }
            jsonPath("$.data.company.company.name") { value("Acme") }
            jsonPath("$.data.company.company.status") { value("ACTIVE") }
        }
    }

    @Test
    fun `a member opens the company with their own role`() {
        val id = createdId(mockMvc.signIn(emailPort, "jane@example.com"), "Acme")
        val john = mockMvc.signIn(emailPort, "john@example.com")
        memberRepository.save(CompanyMember(TSIDCodec.decode(id), userIdOf("john@example.com"), CompanyRole.VIEWER))

        company(john, id).andExpect { jsonPath("$.data.company.role") { value("VIEWER") } }
    }

    @Test
    fun `a read-only company can still be opened`() {
        val session = mockMvc.signIn(emailPort, "jane@example.com")
        val id = createdId(session, "Acme")
        setStatus(id, CompanyStatus.READ_ONLY)

        company(session, id).andExpect { jsonPath("$.data.company.company.status") { value("READ_ONLY") } }
    }

    @Test
    fun `a suspended company can't be opened, even by its owner`() {
        val session = mockMvc.signIn(emailPort, "jane@example.com")
        val id = createdId(session, "Acme")
        setStatus(id, CompanyStatus.SUSPENDED)

        company(session, id).andExpect {
            jsonPath("$.errors[0].extensions.code") { value("company_suspended") }
            jsonPath("$.data.company") { doesNotExist() }
        }
    }

    private fun company(
        session: Cookie,
        id: String,
    ) = mockMvc.graphql(
        session,
        "query(${'$'}id: ID!) { company(id: ${'$'}id) { role company { name status } } }",
        mapOf("id" to id),
    )

    private fun createdId(
        session: Cookie,
        name: String,
    ): String =
        JsonPath.read(
            create(session, name, "GR").andReturn().response.contentAsString,
            "$.data.createCompany.id",
        )

    private fun setStatus(
        id: String,
        status: CompanyStatus,
    ) {
        val company = companyRepository.findById(TSIDCodec.decode(id)).get()
        company.changeStatus(status)
        companyRepository.save(company)
    }

    private fun userIdOf(email: String) = userRepository.findByEmail(email)!!.id

    private fun create(
        session: Cookie?,
        name: String,
        country: String,
    ) = mockMvc.graphql(session, createCompany, mapOf("input" to mapOf("name" to name, "country" to country)))
}
