package com.invohub.backend.infrastructure.adapter.inbound.graphql.member

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
import jakarta.servlet.http.Cookie
import org.hamcrest.Matchers.contains
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.web.servlet.MockMvc

@IntegrationTest
@AutoConfigureMockMvc
class MemberGraphQLTest {
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

    private val members = "query(${'$'}c: ID!) { members(companyId: ${'$'}c) { userId email role since } }"
    private val changeRole =
        """
        mutation(${'$'}c: ID!, ${'$'}u: ID!, ${'$'}r: CompanyRole!) {
            changeMemberRole(companyId: ${'$'}c, userId: ${'$'}u, role: ${'$'}r) { email role }
        }
        """.trimIndent()
    private val remove = "mutation(${'$'}c: ID!, ${'$'}u: ID!) { removeMember(companyId: ${'$'}c, userId: ${'$'}u) }"
    private val leave = "mutation(${'$'}c: ID!) { leaveCompany(companyId: ${'$'}c) }"

    private lateinit var companyId: String
    private lateinit var owner: Cookie
    private lateinit var admin: Cookie
    private lateinit var viewer: Cookie

    @BeforeEach
    fun reset() {
        emailPort.clear()
        databaseCleaner.clean()
        redisTemplate.connectionFactory!!.connection.use { it.serverCommands().flushAll() }

        owner = mockMvc.signIn(emailPort, "owner@example.com")
        admin = mockMvc.signIn(emailPort, "admin@example.com")
        viewer = mockMvc.signIn(emailPort, "viewer@example.com")
        val company = companyService.createCompany(idOf("owner@example.com"), "Acme", "GR")
        memberRepository.save(CompanyMember(company.id, idOf("admin@example.com"), CompanyRole.ADMIN))
        memberRepository.save(CompanyMember(company.id, idOf("viewer@example.com"), CompanyRole.VIEWER))
        companyId = company.getPublicId()
    }

    @Test
    fun `everyone in the company sees its people`() {
        mockMvc.graphql(viewer, members, mapOf("c" to companyId)).andExpect {
            jsonPath("$.data.members[*].email") {
                value(contains("owner@example.com", "admin@example.com", "viewer@example.com"))
            }
            jsonPath("$.data.members[0].role") { value("OWNER") }
            jsonPath("$.data.members[0].userId") { value(publicIdOf("owner@example.com")) }
            jsonPath("$.data.members[0].since") { isNotEmpty() }
        }
    }

    @Test
    fun `an admin changes a role`() {
        mockMvc.graphql(admin, changeRole, vars(publicIdOf("viewer@example.com"), "MEMBER")).andExpect {
            jsonPath("$.data.changeMemberRole.email") { value("viewer@example.com") }
            jsonPath("$.data.changeMemberRole.role") { value("MEMBER") }
        }
    }

    @Test
    fun `refusals say why`() {
        mockMvc.graphql(admin, changeRole, vars(publicIdOf("owner@example.com"), "VIEWER")).andExpect {
            jsonPath("$.errors[0].extensions.code") { value("not_allowed") }
            jsonPath("$.errors[0].extensions.reason") { value("owner") }
        }
        mockMvc.graphql(owner, changeRole, vars(publicIdOf("admin@example.com"), "OWNER")).andExpect {
            jsonPath("$.errors[0].extensions.code") { value("invalid_input") }
            jsonPath("$.errors[0].extensions.reason") { value("owner") }
        }
        mockMvc.graphql(viewer, remove, mapOf("c" to companyId, "u" to publicIdOf("admin@example.com"))).andExpect {
            jsonPath("$.errors[0].extensions.code") { value("forbidden") }
        }
        mockMvc.graphql(owner, remove, mapOf("c" to companyId, "u" to "not-an-id")).andExpect {
            jsonPath("$.errors[0].extensions.code") { value("member_not_found") }
        }
        mockMvc.graphql(owner, leave, mapOf("c" to companyId)).andExpect {
            jsonPath("$.errors[0].extensions.code") { value("not_allowed") }
            jsonPath("$.errors[0].extensions.reason") { value("owner_cannot_leave") }
        }
    }

    @Test
    fun `someone removed can't reach the company with their next request`() {
        mockMvc.graphql(admin, remove, mapOf("c" to companyId, "u" to publicIdOf("viewer@example.com"))).andExpect {
            jsonPath("$.data.removeMember") { value(true) }
        }

        mockMvc.graphql(viewer, members, mapOf("c" to companyId)).andExpect {
            jsonPath("$.errors[0].extensions.code") { value("not_found") }
        }
        mockMvc.graphql(viewer, "{ me { email companies { role } } }").andExpect {
            jsonPath("$.data.me.email") { value("viewer@example.com") }
            jsonPath("$.data.me.companies") { isEmpty() }
        }
    }

    @Test
    fun `a member leaves`() {
        mockMvc.graphql(viewer, leave, mapOf("c" to companyId)).andExpect {
            jsonPath("$.data.leaveCompany") { value(true) }
        }

        mockMvc.graphql(owner, members, mapOf("c" to companyId)).andExpect {
            jsonPath("$.data.members[*].email") { value(contains("owner@example.com", "admin@example.com")) }
        }
    }

    private fun vars(
        userId: String,
        role: String,
    ) = mapOf("c" to companyId, "u" to userId, "r" to role)

    private fun idOf(email: String) = userRepository.findByEmail(email)!!.id

    private fun publicIdOf(email: String) = userRepository.findByEmail(email)!!.getPublicId()
}
