package com.invohub.backend.infrastructure.adapter.inbound.graphql.invitation

import com.invohub.backend.domain.company.Company
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.web.servlet.MockMvc
import kotlin.test.assertEquals

@IntegrationTest
@AutoConfigureMockMvc
class InvitationGraphQLTest {
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
    private lateinit var userRepository: UserRepository

    private val invite =
        """
        mutation(${'$'}input: InviteMembersInput!) {
            inviteMembers(input: ${'$'}input) { id email role invitedBy expiresAt company { name } }
        }
        """.trimIndent()
    private val myInvitations = "{ me { invitations { id role invitedBy company { id name } } } }"
    private val accept =
        "mutation(${'$'}id: ID!) { acceptInvitation(invitationId: ${'$'}id) { role company { name } } }"
    private val decline = "mutation(${'$'}id: ID!) { declineInvitation(invitationId: ${'$'}id) }"

    private lateinit var owner: Cookie
    private lateinit var acme: Company
    private lateinit var globex: Company

    @BeforeEach
    fun reset() {
        emailPort.clear()
        databaseCleaner.clean()
        redisTemplate.connectionFactory!!.connection.use { it.serverCommands().flushAll() }

        owner = mockMvc.signIn(emailPort, "owner@example.com")
        val ownerId = userRepository.findByEmail("owner@example.com")!!.id
        acme = companyService.createCompany(ownerId, "Acme", "GR")
        globex = companyService.createCompany(ownerId, "Globex", "GR")
    }

    @Test
    fun `invite, then the person signs in and accepts`() {
        inviteMaria(acme to "ADMIN", globex to "VIEWER").andExpect {
            jsonPath("$.data.inviteMembers[*].company.name") { value(contains("Acme", "Globex")) }
            jsonPath("$.data.inviteMembers[0].invitedBy") { value("owner@example.com") }
            jsonPath("$.data.inviteMembers[0].expiresAt") { isNotEmpty() }
        }
        assertEquals("maria@example.com", emailPort.invitationEmails.single().email)

        val maria = mockMvc.signIn(emailPort, "maria@example.com")
        val acmeInvitation = invitationIdFor(maria, "Acme")
        mockMvc.graphql(maria, accept, mapOf("id" to acmeInvitation)).andExpect {
            jsonPath("$.data.acceptInvitation.role") { value("ADMIN") }
            jsonPath("$.data.acceptInvitation.company.name") { value("Acme") }
        }

        mockMvc
            .graphql(
                maria,
                "{ me { companies { role company { name } } invitations { company { name } } } }",
            ).andExpect {
                jsonPath("$.data.me.companies[0].company.name") { value("Acme") }
                jsonPath("$.data.me.invitations[*].company.name") { value(contains("Globex")) }
            }
    }

    @Test
    fun `declining, and nobody else can act on someone's invitation`() {
        inviteMaria(acme to "MEMBER")
        val maria = mockMvc.signIn(emailPort, "maria@example.com")
        val id = invitationIdFor(maria, "Acme")
        val john = mockMvc.signIn(emailPort, "john@example.com")

        mockMvc.graphql(john, accept, mapOf("id" to id)).andExpect {
            jsonPath("$.errors[0].extensions.code") { value("invitation_not_found") }
        }
        mockMvc.graphql(john, myInvitations).andExpect { jsonPath("$.data.me.invitations") { isEmpty() } }

        mockMvc
            .graphql(
                maria,
                decline,
                mapOf("id" to id),
            ).andExpect { jsonPath("$.data.declineInvitation") { value(true) } }
        mockMvc.graphql(maria, myInvitations).andExpect { jsonPath("$.data.me.invitations") { isEmpty() } }
    }

    @Test
    fun `the company lists and revokes its open invitations`() {
        val id: String =
            JsonPath.read(
                inviteMaria(acme to "MEMBER").andReturn().response.contentAsString,
                "$.data.inviteMembers[0].id",
            )
        val companyId = acme.getPublicId()

        mockMvc
            .graphql(
                owner,
                "query(${'$'}c: ID!) { invitations(companyId: ${'$'}c) { email role } }",
                mapOf(
                    "c" to companyId,
                ),
            ).andExpect {
                jsonPath("$.data.invitations[0].email") { value("maria@example.com") }
            }
        mockMvc
            .graphql(
                owner,
                "mutation(${'$'}c: ID!, ${'$'}i: ID!) { revokeInvitation(companyId: ${'$'}c, invitationId: ${'$'}i) }",
                mapOf("c" to companyId, "i" to id),
            ).andExpect { jsonPath("$.data.revokeInvitation") { value(true) } }

        val maria = mockMvc.signIn(emailPort, "maria@example.com")
        mockMvc.graphql(maria, myInvitations).andExpect { jsonPath("$.data.me.invitations") { isEmpty() } }
    }

    @Test
    fun `refusals name the reason and the company`() {
        inviteMaria(acme to "MEMBER")

        inviteMaria(globex to "MEMBER", acme to "ADMIN").andExpect {
            jsonPath("$.errors[0].extensions.code") { value("invitation_refused") }
            jsonPath("$.errors[0].extensions.reason") { value("already_invited") }
            jsonPath("$.errors[0].extensions.companyId") { value(acme.getPublicId()) }
            jsonPath("$.data") { doesNotExist() }
        }
        mockMvc.graphql(owner, invite, inviteVars("not-an-email", acme to "MEMBER")).andExpect {
            jsonPath("$.errors[0].extensions.code") { value("invalid_input") }
            jsonPath("$.errors[0].extensions.field") { value("email") }
        }
    }

    private fun inviteMaria(vararg companies: Pair<Company, String>) =
        mockMvc.graphql(owner, invite, inviteVars("maria@example.com", *companies))

    private fun inviteVars(
        email: String,
        vararg companies: Pair<Company, String>,
    ) = mapOf(
        "input" to
            mapOf(
                "email" to email,
                "companies" to
                    companies.map { (company, role) -> mapOf("companyId" to company.getPublicId(), "role" to role) },
            ),
    )

    private fun invitationIdFor(
        session: Cookie,
        companyName: String,
    ): String {
        val body =
            mockMvc
                .graphql(session, myInvitations)
                .andReturn()
                .response.contentAsString
        return JsonPath
            .read<List<String>>(
                body,
                "$.data.me.invitations[?(@.company.name == '$companyName')].id",
            ).single()
    }
}
