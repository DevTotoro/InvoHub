package com.invohub.backend.infrastructure.adapter.inbound.graphql

import com.invohub.backend.domain.company.CompanyService
import com.invohub.backend.domain.user.UserRepository
import com.invohub.backend.support.CapturingEmailPort
import com.invohub.backend.support.DatabaseCleaner
import com.invohub.backend.support.IntegrationTest
import com.invohub.backend.support.graphql
import com.invohub.backend.support.signIn
import graphql.schema.GraphQLObjectType
import io.hypersistence.tsid.TSID
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.graphql.execution.GraphQlSource
import org.springframework.test.web.servlet.MockMvc
import kotlin.test.assertEquals

/**
 * Nobody can reach another company's data. Every company-scoped root field (one taking `companyId`, or
 * `company(id)`) must have an operation in [operations]; the first test fails for a field that doesn't, so a
 * new field can't skip this check. Each operation is then run by someone outside the company.
 */
@IntegrationTest
@AutoConfigureMockMvc
class CompanyIsolationTest {
    /** A call to each company-scoped root field, given the company's public ID: the operation and its variables */
    private val operations: Map<String, (String) -> Pair<String, Map<String, Any?>>> =
        mapOf(
            "Query.company" to { id ->
                "query(${'$'}id: ID!) { company(id: ${'$'}id) { role company { id name } } }" to mapOf("id" to id)
            },
            "Query.members" to { id ->
                "query(${'$'}id: ID!) { members(companyId: ${'$'}id) { email } }" to mapOf("id" to id)
            },
            "Mutation.changeMemberRole" to { id ->
                """
                mutation(${'$'}id: ID!, ${'$'}user: ID!) {
                    changeMemberRole(companyId: ${'$'}id, userId: ${'$'}user, role: VIEWER) { role }
                }
                """.trimIndent() to mapOf("id" to id, "user" to TSID.fast().toString())
            },
            "Mutation.removeMember" to { id ->
                "mutation(${'$'}id: ID!, ${'$'}user: ID!) { removeMember(companyId: ${'$'}id, userId: ${'$'}user) }" to
                    mapOf("id" to id, "user" to TSID.fast().toString())
            },
            "Mutation.leaveCompany" to { id ->
                "mutation(${'$'}id: ID!) { leaveCompany(companyId: ${'$'}id) }" to mapOf("id" to id)
            },
        )

    @Autowired
    private lateinit var graphQlSource: GraphQlSource

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

    @BeforeEach
    fun reset() {
        emailPort.clear()
        databaseCleaner.clean()
        redisTemplate.connectionFactory!!.connection.use { it.serverCommands().flushAll() }
    }

    @Test
    fun `every company-scoped field is covered here`() {
        val schema = graphQlSource.schema()
        val scoped =
            listOfNotNull(schema.queryType, schema.mutationType)
                .flatMap { type -> companyScopedFields(type) }
                .toSet()

        assertEquals(scoped, operations.keys, "Add an operation for each company-scoped field to `operations`")
    }

    @Test
    fun `someone outside the company gets not_found from every company-scoped field`() {
        signIn("owner@example.com")
        val company = companyService.createCompany(userIdOf("owner@example.com"), "Acme", "GR")
        val outsider = signIn("outsider@example.com")

        operations.forEach { (field, operation) ->
            expectNotFound(outsider, field, operation(company.getPublicId()))
        }
    }

    @Test
    fun `an unknown or malformed company ID is not_found too`() {
        val user = signIn("jane@example.com")

        operations.forEach { (field, operation) ->
            expectNotFound(user, field, operation(TSID.fast().toString()))
            expectNotFound(user, field, operation("not-an-id"))
        }
    }

    private fun companyScopedFields(type: GraphQLObjectType) =
        type.fieldDefinitions
            .filter { field -> field.name == "company" || field.arguments.any { it.name == "companyId" } }
            .map { "${type.name}.${it.name}" }

    private fun expectNotFound(
        session: Cookie,
        field: String,
        operation: Pair<String, Map<String, Any?>>,
    ) {
        val name = field.substringAfter('.')
        mockMvc.graphql(session, operation.first, operation.second).andExpect {
            jsonPath("$.errors[0].extensions.code") { value("not_found") }
            jsonPath("$.data.$name") { doesNotExist() }
        }
    }

    private fun signIn(email: String) = mockMvc.signIn(emailPort, email)

    private fun userIdOf(email: String) = userRepository.findByEmail(email)!!.id
}
