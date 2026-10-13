package com.invohub.backend.infrastructure.adapter.inbound.graphql.company

import com.invohub.backend.domain.company.Company
import com.invohub.backend.domain.company.CompanyMember
import com.invohub.backend.domain.company.CompanyMemberRepository
import com.invohub.backend.domain.company.CompanyRole
import com.invohub.backend.domain.company.CompanyService
import com.invohub.backend.domain.user.UserRepository
import com.invohub.backend.support.CapturingEmailPort
import com.invohub.backend.support.DatabaseCleaner
import com.invohub.backend.support.IntegrationTest
import com.invohub.backend.support.graphql
import com.invohub.backend.support.signIn
import com.jayway.jsonpath.JsonPath
import jakarta.servlet.http.Cookie
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.web.servlet.MockMvc
import java.util.UUID

@IntegrationTest
@AutoConfigureMockMvc
class CompanySettingsGraphQLTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var emailPort: CapturingEmailPort

    @Autowired
    private lateinit var databaseCleaner: DatabaseCleaner

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    @Autowired
    private lateinit var companyService: CompanyService

    @Autowired
    private lateinit var memberRepository: CompanyMemberRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    private val fields =
        """
        company { name country currency }
        legalName address { street city postalCode } taxId
        countryDetails { key value }
        bankAccounts { id bankName iban }
        logoUrl
        invoicingSetup { key done available }
        """.trimIndent()
    private val settings = "query(${'$'}c: ID!) { companySettings(companyId: ${'$'}c) { $fields } }"
    private val updateDetails =
        """
        mutation(${'$'}c: ID!, ${'$'}input: BusinessDetailsInput!) {
            updateBusinessDetails(companyId: ${'$'}c, input: ${'$'}input) { $fields }
        }
        """.trimIndent()
    private val addBankAccount =
        """
        mutation(${'$'}c: ID!, ${'$'}input: BankAccountInput!) {
            addBankAccount(companyId: ${'$'}c, input: ${'$'}input) { bankAccounts { id iban } }
        }
        """.trimIndent()

    private lateinit var owner: Cookie
    private lateinit var acme: Company

    @BeforeEach
    fun reset() {
        emailPort.clear()
        databaseCleaner.clean()
        redisTemplate.connectionFactory!!.connection.use { it.serverCommands().flushAll() }

        owner = mockMvc.signIn(emailPort, "owner@example.com")
        acme = companyService.createCompany(userRepository.findByEmail("owner@example.com")!!.id, "Acme", "GR")
    }

    @Test
    fun `a new company shows every setup step to do, and the Greek details empty`() {
        mockMvc.graphql(owner, settings, mapOf("c" to acme.getPublicId())).andExpect {
            jsonPath("$.data.companySettings.company.name") { value("Acme") }
            jsonPath("$.data.companySettings.taxId") { doesNotExist() }
            jsonPath("$.data.companySettings.countryDetails[*].key") { value(contains("taxOffice", "mainActivity")) }
            jsonPath("$.data.companySettings.invoicingSetup[*].key") {
                value(contains("tax_id", "business_details", "mydata"))
            }
            jsonPath("$.data.companySettings.invoicingSetup[*].available") { value(contains(true, true, false)) }
            jsonPath("$.data.companySettings.logoUrl") { doesNotExist() }
        }
    }

    @Test
    fun `filling in the business details completes those steps`() {
        val input =
            mapOf(
                "legalName" to "Acme ΙΚΕ",
                "street" to "Ερμού 1",
                "city" to "Αθήνα",
                "postalCode" to "10563",
                "taxId" to "EL094014201",
                "countryDetails" to
                    listOf(
                        mapOf("key" to "taxOffice", "value" to "Α' Αθηνών"),
                        mapOf("key" to "mainActivity", "value" to "62.01"),
                    ),
            )

        mockMvc.graphql(owner, updateDetails, mapOf("c" to acme.getPublicId(), "input" to input)).andExpect {
            jsonPath("$.data.updateBusinessDetails.taxId") { value("094014201") }
            jsonPath("$.data.updateBusinessDetails.address.city") { value("Αθήνα") }
            jsonPath("$.data.updateBusinessDetails.countryDetails[*].value") { value(contains("Α' Αθηνών", "6201")) }
            jsonPath("$.data.updateBusinessDetails.invoicingSetup[*].done") { value(contains(true, true, false)) }
        }
    }

    @Test
    fun `bad input names the field and the reason`() {
        mockMvc
            .graphql(
                owner,
                updateDetails,
                mapOf("c" to acme.getPublicId(), "input" to mapOf("taxId" to "094014202")),
            ).andExpect {
                jsonPath("$.errors[0].extensions.code") { value("invalid_input") }
                jsonPath("$.errors[0].extensions.field") { value("taxId") }
                jsonPath("$.errors[0].extensions.reason") { value("check_digit") }
            }
        mockMvc
            .graphql(
                owner,
                addBankAccount,
                mapOf("c" to acme.getPublicId(), "input" to mapOf("bankName" to "Eurobank", "iban" to "GR00")),
            ).andExpect {
                jsonPath("$.errors[0].extensions.field") { value("iban") }
                jsonPath("$.errors[0].extensions.reason") { value("invalid") }
            }
    }

    @Test
    fun `bank accounts are added and removed by their public ID`() {
        val body =
            mockMvc
                .graphql(
                    owner,
                    addBankAccount,
                    mapOf(
                        "c" to acme.getPublicId(),
                        "input" to mapOf("bankName" to "Eurobank", "iban" to "GR16 0110 1250 0000 0001 2300 695"),
                    ),
                ).andReturn()
                .response.contentAsString
        val id: String = JsonPath.read(body, "$.data.addBankAccount.bankAccounts[0].id")

        mockMvc
            .graphql(
                owner,
                "mutation(${'$'}c: ID!, ${'$'}b: ID!) { removeBankAccount(companyId: ${'$'}c, bankAccountId: ${'$'}b) { bankAccounts { id } } }",
                mapOf("c" to acme.getPublicId(), "b" to id),
            ).andExpect { jsonPath("$.data.removeBankAccount.bankAccounts") { isEmpty() } }
    }

    @Test
    fun `a logo upload gets a signed URL, and only an upload that happened can become the logo`() {
        mockMvc
            .graphql(
                owner,
                """
                mutation(${'$'}c: ID!) {
                    prepareLogoUpload(companyId: ${'$'}c, input: { contentType: "image/png", size: 2048 }) {
                        uploadId url headers { name value } expiresAt
                    }
                }
                """.trimIndent(),
                mapOf("c" to acme.getPublicId()),
            ).andExpect {
                jsonPath("$.data.prepareLogoUpload.url") { value(containsString("/pending/${acme.getPublicId()}/")) }
                jsonPath("$.data.prepareLogoUpload.headers[?(@.name == 'content-type')].value") {
                    value(contains("image/png"))
                }
                jsonPath("$.data.prepareLogoUpload.expiresAt") { isNotEmpty() }
            }
        mockMvc
            .graphql(
                owner,
                "mutation(${'$'}c: ID!) { prepareLogoUpload(companyId: ${'$'}c, input: { contentType: \"image/svg+xml\", size: 10 }) { uploadId } }",
                mapOf("c" to acme.getPublicId()),
            ).andExpect {
                jsonPath("$.errors[0].extensions.field") { value("logo") }
                jsonPath("$.errors[0].extensions.reason") { value("unsupported_type") }
            }
        mockMvc
            .graphql(
                owner,
                "mutation(${'$'}c: ID!, ${'$'}u: ID!) { changeLogo(companyId: ${'$'}c, uploadId: ${'$'}u) { logoUrl } }",
                mapOf("c" to acme.getPublicId(), "u" to UUID.randomUUID().toString()),
            ).andExpect { jsonPath("$.errors[0].extensions.reason") { value("upload_missing") } }
    }

    @Test
    fun `admins see settings, members and viewers don't`() {
        val admin = join("admin@example.com", CompanyRole.ADMIN)
        val member = join("member@example.com", CompanyRole.MEMBER)

        mockMvc.graphql(admin, settings, mapOf("c" to acme.getPublicId())).andExpect {
            jsonPath("$.data.companySettings.company.name") { value("Acme") }
        }
        mockMvc.graphql(member, settings, mapOf("c" to acme.getPublicId())).andExpect {
            jsonPath("$.errors[0].extensions.code") { value("forbidden") }
        }
        mockMvc
            .graphql(
                member,
                "mutation(${'$'}c: ID!) { updateCompanyProfile(companyId: ${'$'}c, input: { name: \"Mine\", country: \"GR\" }) { taxId } }",
                mapOf("c" to acme.getPublicId()),
            ).andExpect { jsonPath("$.errors[0].extensions.code") { value("forbidden") } }
    }

    private fun join(
        email: String,
        role: CompanyRole,
    ): Cookie {
        val session = mockMvc.signIn(emailPort, email)
        memberRepository.save(CompanyMember(acme.id, userRepository.findByEmail(email)!!.id, role))
        return session
    }
}
