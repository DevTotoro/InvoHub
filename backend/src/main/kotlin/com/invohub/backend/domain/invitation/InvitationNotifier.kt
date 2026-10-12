package com.invohub.backend.domain.invitation

import com.invohub.backend.domain.auth.AuthProperties
import com.invohub.backend.domain.auth.EmailPort
import com.invohub.backend.domain.auth.InvitationNotice
import com.invohub.backend.domain.company.CompanyRepository
import com.invohub.backend.domain.user.Language
import com.invohub.backend.domain.user.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Emails people about their invitations: one email lists every invitation they haven't been told about, and
 * nobody gets more than one every [InvitationProperties.notifyAtMostEvery].
 *
 * Right after inviting, the email goes out if the address is due. If it isn't (it was emailed recently), or
 * sending fails, the invitations wait and InvitationNotificationJob sends them later, so every invitation is
 * mentioned in an email eventually.
 */
@Component
class InvitationNotifier(
    private val invitationRepository: InvitationRepository,
    private val companyRepository: CompanyRepository,
    private val userRepository: UserRepository,
    private val emailPort: EmailPort,
    private val properties: InvitationProperties,
    private val authProperties: AuthProperties,
    transactionManager: PlatformTransactionManager,
) {
    // A transaction of its own: after the inviting transaction commits, its resources are still bound
    private val newTransaction =
        TransactionTemplate(transactionManager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }

    /** Never fails the invite: the invitations are saved, and the job retries the email */
    @TransactionalEventListener
    fun onInvitationsCreated(event: InvitationsCreated) {
        runCatching { notifyIfDue(event.email) }
            .onFailure { logger.warn(it) { "Invitation email failed; it will be retried" } }
    }

    /**
     * Sends the address one email about its invitations not yet mentioned, unless it got one too recently.
     * @return whether an email was sent
     * @throws com.invohub.backend.domain.auth.EmailDeliveryException if sending failed; nothing is marked sent
     */
    fun notifyIfDue(email: String): Boolean =
        newTransaction.execute {
            invitationRepository.lockEmail(email)
            val now = Instant.now()

            val last = invitationRepository.lastNotifiedAt(email)
            if (last != null && last.isAfter(now - properties.notifyAtMostEvery)) return@execute false
            val due = invitationRepository.findDue(email, now)
            if (due.isEmpty()) return@execute false

            val companies = companyRepository.findAllById(due.map { it.companyId }.toSet()).associateBy { it.id }
            val inviters = userRepository.findAllById(due.map { it.invitedByUserId }.toSet()).associateBy { it.id }
            val notices =
                due.map {
                    InvitationNotice(
                        companies.getValue(it.companyId).name,
                        inviters.getValue(it.invitedByUserId).email,
                        it.role,
                    )
                }
            // Their own language if they have an account; otherwise English, not the inviter's, which may not be theirs
            val language = userRepository.findByEmail(email)?.language ?: Language.DEFAULT

            emailPort.sendInvitations(email, notices, invitationsPage(), language)
            due.forEach { it.markNotified(now) }
            invitationRepository.saveAll(due)
            true
        }

    private fun invitationsPage() =
        UriComponentsBuilder
            .fromUri(authProperties.frontendUrl)
            .path("/invitations")
            .build()
            .toUri()
}
