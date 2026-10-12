package com.invohub.backend.domain.invitation

import com.invohub.backend.domain.auth.EmailDeliveryException
import com.invohub.backend.domain.company.Company
import com.invohub.backend.domain.company.CompanyAccessService
import com.invohub.backend.domain.company.CompanyRole
import com.invohub.backend.domain.company.CompanyService
import com.invohub.backend.domain.user.Language
import com.invohub.backend.domain.user.UserService
import com.invohub.backend.support.CapturingEmailPort
import com.invohub.backend.support.DatabaseCleaner
import com.invohub.backend.support.IntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** At most one invitation email an hour per address, and every invitation mentioned in one eventually. */
@IntegrationTest
class InvitationNotifierTest {
    @Autowired
    private lateinit var invitationService: InvitationService

    @Autowired
    private lateinit var invitationRepository: InvitationRepository

    @Autowired
    private lateinit var notifier: InvitationNotifier

    @Autowired
    private lateinit var accessService: CompanyAccessService

    @Autowired
    private lateinit var companyService: CompanyService

    @Autowired
    private lateinit var userService: UserService

    @Autowired
    private lateinit var emailPort: CapturingEmailPort

    @Autowired
    private lateinit var databaseCleaner: DatabaseCleaner

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var properties: InvitationProperties

    private val maria = "maria@example.com"
    private var owner = 0L
    private lateinit var acme: Company
    private lateinit var globex: Company
    private lateinit var initech: Company

    // Built here: the scheduled bean is off in tests
    private val job by lazy { InvitationNotificationJob(invitationRepository, notifier, properties) }

    @BeforeEach
    fun reset() {
        emailPort.clear()
        databaseCleaner.clean()
        owner = userService.getOrCreateUser("owner@example.com", Language.EN).id
        acme = companyService.createCompany(owner, "Acme", "GR")
        globex = companyService.createCompany(owner, "Globex", "GR")
        initech = companyService.createCompany(owner, "Initech", "GR")
    }

    @Test
    fun `a second invitation within the hour waits for the next email`() {
        invite(acme)
        invite(globex)

        assertEquals(listOf(listOf("Acme")), emailedCompanies())
        job.run()
        assertEquals(1, emailPort.invitationEmails.size, "Still within the hour")
    }

    @Test
    fun `once the hour is up, the job sends one email with everything that waited`() {
        invite(acme)
        invite(globex)
        invite(initech)
        anHourPasses()

        job.run()

        assertEquals(listOf(listOf("Acme"), listOf("Globex", "Initech")), emailedCompanies())
        job.run()
        assertEquals(2, emailPort.invitationEmails.size, "Nothing left to send")
    }

    @Test
    fun `invitations that closed while waiting are left out, and with none left nothing is sent`() {
        invite(acme)
        val waiting = invite(globex)
        invitationService.revoke(accessService.resolve(owner, globex.id), waiting.id)
        anHourPasses()

        job.run()

        assertEquals(listOf(listOf("Acme")), emailedCompanies())
    }

    @Test
    fun `a failed email doesn't fail the invite, and the job sends it later`() {
        emailPort.failure = EmailDeliveryException("SMTP down")
        val invitation = invite(acme)
        assertNull(invitationRepository.findById(invitation.id).get().notifiedAt)

        emailPort.failure = null
        job.run()

        assertEquals(listOf(listOf("Acme")), emailedCompanies())
        assertNotNull(invitationRepository.findById(invitation.id).get().notifiedAt)
    }

    @Test
    fun `servers sending at the same time send one email`() {
        // An invitation saved without its email, as if it is about to be sent
        emailPort.failure = EmailDeliveryException("not yet")
        invite(acme)
        emailPort.failure = null

        val start = CountDownLatch(1)
        val sent =
            Executors.newFixedThreadPool(8).use { pool ->
                val calls = List(8) { pool.submit<Boolean> { start.await().let { notifier.notifyIfDue(maria) } } }
                start.countDown()
                calls.map { it.get() }
            }

        assertEquals(1, sent.count { it })
        assertEquals(listOf(listOf("Acme")), emailedCompanies())
    }

    @Test
    fun `an email whose sender died before confirming is sent again later`() {
        val invitation = invite(acme)
        // As if the server died right after claiming, before sending
        jdbcTemplate.update(
            "UPDATE invitations SET notified_at = NULL, notification_claimed_at = now() - interval '11 minutes' WHERE id = ?",
            invitation.id,
        )
        emailPort.clear()
        anHourPasses()

        job.run()

        assertEquals(listOf(listOf("Acme")), emailedCompanies())
    }

    @Test
    fun `a claimed email still being sent isn't sent twice`() {
        val invitation = invite(acme)
        jdbcTemplate.update(
            "UPDATE invitations SET notified_at = NULL, notification_claimed_at = now() WHERE id = ?",
            invitation.id,
        )
        emailPort.clear()
        anHourPasses()

        job.run()

        assertEquals(listOf(), emailedCompanies())
    }

    @Test
    fun `each address has its own hour`() {
        invite(acme)
        invite(acme, "john@example.com")

        assertEquals(listOf(maria, "john@example.com"), emailPort.invitationEmails.map { it.email })
    }

    private fun invite(
        company: Company,
        email: String = maria,
    ) = invitationService
        .invite(owner, email, listOf(InvitationTarget(accessService.resolve(owner, company.id), CompanyRole.MEMBER)))
        .single()

    private fun emailedCompanies() = emailPort.invitationEmails.map { sent -> sent.invitations.map { it.companyName } }

    private fun anHourPasses() {
        jdbcTemplate.update(
            "UPDATE invitation_recipients SET last_notified_at = last_notified_at - interval '61 minutes'",
        )
    }
}
