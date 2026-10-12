package com.invohub.backend.infrastructure.adapter.inbound.web.auth

import com.invohub.backend.domain.audit.AuditAction
import com.invohub.backend.domain.audit.AuditEvent
import com.invohub.backend.domain.company.CompanyService
import com.invohub.backend.domain.company.country.CountryRulesRegistry
import com.invohub.backend.domain.user.UserRepository
import com.invohub.backend.infrastructure.adapter.inbound.graphql.company.CompanyScope
import com.invohub.backend.infrastructure.adapter.inbound.web.filter.RequestIdFilter
import com.invohub.backend.infrastructure.adapter.outbound.audit.JpaAuditLog
import com.invohub.backend.support.CapturingEmailPort
import com.invohub.backend.support.DatabaseCleaner
import com.invohub.backend.support.IntegrationTest
import com.invohub.backend.support.graphql
import com.invohub.backend.support.signIn
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.any
import org.mockito.Mockito.doAnswer
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What the logging context (MDC) holds while the app works: the request ID and the signed-in user. */
@IntegrationTest
@AutoConfigureMockMvc
class LoggingContextTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var emailPort: CapturingEmailPort

    @Autowired
    private lateinit var databaseCleaner: DatabaseCleaner

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    @Autowired
    private lateinit var userRepository: UserRepository

    @MockitoSpyBean
    private lateinit var companyService: CompanyService

    @MockitoSpyBean
    private lateinit var auditLog: JpaAuditLog

    @MockitoSpyBean
    private lateinit var countryRules: CountryRulesRegistry

    /** The MDC at each recorded audit event, by action */
    private val atAuditEvent = CopyOnWriteArrayList<Pair<AuditAction, Map<String, String>>>()

    @BeforeEach
    fun reset() {
        emailPort.clear()
        databaseCleaner.clean()
        redisTemplate.connectionFactory!!.connection.use { it.serverCommands().flushAll() }
        doAnswer {
            atAuditEvent += it.getArgument<AuditEvent>(0).action to mdc()
            it.callRealMethod()
        }.`when`(auditLog).recordStandalone(any() ?: AuditEvent(AuditAction.USER_SIGNED_IN))
    }

    @Test
    fun `GraphQL work is logged with the request and the user`() {
        val session = mockMvc.signIn(emailPort, "jane@example.com")
        var inResolver: Map<String, String>? = null
        doAnswer {
            inResolver = mdc()
            it.callRealMethod()
        }.`when`(companyService).companiesOf(anyLong())

        mockMvc.graphql(session, "{ me { companies { role } } }")

        assertEquals(publicIdOf("jane@example.com"), inResolver?.get(UserLoggingContextFilter.MDC_KEY))
        assertNotNull(inResolver?.get(RequestIdFilter.MDC_KEY))
    }

    @Test
    fun `work in a company is logged with the company too`() {
        val session = mockMvc.signIn(emailPort, "jane@example.com")
        val company = companyService.createCompany(userRepository.findByEmail("jane@example.com")!!.id, "Acme", "GR")
        var inCompany: Map<String, String>? = null
        // Called while building the company's view, once its access has been checked
        doAnswer {
            inCompany = mdc()
            it.callRealMethod()
        }.`when`(countryRules).forCountry(anyString())

        mockMvc.graphql(
            session,
            "query(${'$'}id: ID!) { company(id: ${'$'}id) { role } }",
            mapOf("id" to company.getPublicId()),
        )

        assertEquals(company.getPublicId(), inCompany?.get(CompanyScope.MDC_KEY))
        assertEquals(publicIdOf("jane@example.com"), inCompany?.get(UserLoggingContextFilter.MDC_KEY))
        assertTrue(mdc().isEmpty(), "MDC still holds ${mdc()}")
    }

    @Test
    fun `REST work is logged with the request and the user`() {
        val session = mockMvc.signIn(emailPort, "jane@example.com")

        mockMvc.post("/api/auth/logout") {
            with(csrf())
            cookie(session)
        }

        val atSignOut = atAuditEvent.single { it.first == AuditAction.USER_SIGNED_OUT }.second
        assertEquals(publicIdOf("jane@example.com"), atSignOut[UserLoggingContextFilter.MDC_KEY])
        assertNotNull(atSignOut[RequestIdFilter.MDC_KEY])
    }

    @Test
    fun `nobody is in the context before signing in`() {
        mockMvc.signIn(emailPort, "jane@example.com")

        // The sign-in request itself starts without a session
        val atSignIn = atAuditEvent.single { it.first == AuditAction.USER_SIGNED_IN }.second
        assertNull(atSignIn[UserLoggingContextFilter.MDC_KEY])
        assertNotNull(atSignIn[RequestIdFilter.MDC_KEY])
    }

    @Test
    fun `nothing is left in the context after a request`() {
        mockMvc.graphql(mockMvc.signIn(emailPort, "jane@example.com"), "{ me { id } }")

        assertTrue(mdc().isEmpty(), "MDC still holds ${mdc()}")
    }

    private fun publicIdOf(email: String) = userRepository.findByEmail(email)!!.getPublicId()

    private fun mdc(): Map<String, String> = MDC.getCopyOfContextMap() ?: emptyMap()
}
