package com.invohub.backend.infrastructure.adapter.inbound.graphql.company

import com.invohub.backend.domain.common.TSIDCodec
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.web.servlet.MockMvc

@IntegrationTest
@AutoConfigureMockMvc
class OwnershipGraphQLTest {
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

    private val offer =
        "mutation(${'$'}c: ID!, ${'$'}u: ID!) { offerOwnership(companyId: ${'$'}c, userId: ${'$'}u) { id to expiresAt } }"
    private val pending = "query(${'$'}c: ID!) { ownershipTransfer(companyId: ${'$'}c) { to } }"
    private val accept = "mutation(${'$'}t: ID!) { acceptOwnership(transferId: ${'$'}t) { role company { name } } }"
    private val cancel = "mutation(${'$'}c: ID!) { cancelOwnershipTransfer(companyId: ${'$'}c) }"
    private val close = "mutation(${'$'}c: ID!) { closeCompany(companyId: ${'$'}c) { closedAt } }"
    private val reopen = "mutation(${'$'}c: ID!) { reopenCompany(companyId: ${'$'}c) { closedAt } }"
    private val myOffers = "{ me { ownershipOffers { id from to company { name } } } }"
    private val myCompanies = "{ me { companies { role company { name closedAt } } } }"

    private lateinit var owner: Cookie
    private lateinit var maria: Cookie
    private lateinit var acme: Company
    private var mariaId = 0L

    @BeforeEach
    fun reset() {
        emailPort.clear()
        databaseCleaner.clean()
        redisTemplate.connectionFactory!!.connection.use { it.serverCommands().flushAll() }

        owner = mockMvc.signIn(emailPort, "owner@example.com")
        acme = companyService.createCompany(userRepository.findByEmail("owner@example.com")!!.id, "Acme", "GR")
        maria = mockMvc.signIn(emailPort, "maria@example.com")
        mariaId = userRepository.findByEmail("maria@example.com")!!.id
        memberRepository.save(CompanyMember(acme.id, mariaId, CompanyRole.ADMIN))
    }

    @Test
    fun `the owner offers, the member sees it and accepts, and the old owner becomes an admin`() {
        mockMvc
            .graphql(
                owner,
                offer,
                mapOf("c" to acme.getPublicId(), "u" to TSIDCodec.encode(mariaId)),
            ).andExpect { jsonPath("$.data.offerOwnership.to") { value("maria@example.com") } }
        mockMvc
            .graphql(
                owner,
                pending,
                mapOf(
                    "c" to acme.getPublicId(),
                ),
            ).andExpect { jsonPath("$.data.ownershipTransfer.to") { value("maria@example.com") } }

        val offers =
            mockMvc
                .graphql(maria, myOffers)
                .andReturn()
                .response.contentAsString
        val transferId: String = JsonPath.read(offers, "$.data.me.ownershipOffers[0].id")
        mockMvc.graphql(maria, myOffers).andExpect {
            jsonPath("$.data.me.ownershipOffers[0].from") { value("owner@example.com") }
            jsonPath("$.data.me.ownershipOffers[0].company.name") { value("Acme") }
        }

        mockMvc
            .graphql(
                maria,
                accept,
                mapOf("t" to transferId),
            ).andExpect { jsonPath("$.data.acceptOwnership.role") { value("OWNER") } }
        mockMvc.graphql(owner, myCompanies).andExpect { jsonPath("$.data.me.companies[0].role") { value("ADMIN") } }
    }

    @Test
    fun `nobody else can accept, and a member can't offer`() {
        val john = mockMvc.signIn(emailPort, "john@example.com")
        val transferId: String =
            JsonPath.read(
                mockMvc
                    .graphql(
                        owner,
                        offer,
                        mapOf("c" to acme.getPublicId(), "u" to TSIDCodec.encode(mariaId)),
                    ).andReturn()
                    .response.contentAsString,
                "$.data.offerOwnership.id",
            )

        mockMvc
            .graphql(
                john,
                accept,
                mapOf(
                    "t" to transferId,
                ),
            ).andExpect { jsonPath("$.errors[0].extensions.code") { value("ownership_transfer_not_found") } }
        mockMvc
            .graphql(
                maria,
                cancel,
                mapOf("c" to acme.getPublicId()),
            ).andExpect { jsonPath("$.errors[0].extensions.code") { value("forbidden") } }
    }

    @Test
    fun `a closed company shows as closed, refuses changes, and reopens`() {
        mockMvc
            .graphql(
                owner,
                close,
                mapOf(
                    "c" to acme.getPublicId(),
                ),
            ).andExpect { jsonPath("$.data.closeCompany.closedAt") { isNotEmpty() } }
        mockMvc
            .graphql(
                maria,
                myCompanies,
            ).andExpect { jsonPath("$.data.me.companies[0].company.closedAt") { isNotEmpty() } }
        mockMvc
            .graphql(
                maria,
                """
                mutation(${'$'}c: ID!) { updateBusinessDetails(companyId: ${'$'}c, input: { legalName: "X" }) { legalName } }
                """.trimIndent(),
                mapOf("c" to acme.getPublicId()),
            ).andExpect { jsonPath("$.errors[0].extensions.code") { value("company_closed") } }

        mockMvc
            .graphql(
                owner,
                reopen,
                mapOf(
                    "c" to acme.getPublicId(),
                ),
            ).andExpect { jsonPath("$.data.reopenCompany.closedAt") { doesNotExist() } }
    }
}
