package com.invohub.backend.domain.invitation

import com.invohub.backend.domain.auth.AuthProperties
import com.invohub.backend.domain.auth.EmailDeliveryException
import com.invohub.backend.domain.auth.EmailPort
import com.invohub.backend.domain.auth.InvitationNotice
import com.invohub.backend.domain.company.CompanyRepository
import com.invohub.backend.domain.user.Language
import com.invohub.backend.domain.user.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.dao.ConcurrencyFailureException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Emails people about their invitations: one email lists every invitation they haven't been told about, and
 * nobody gets more than one every [InvitationProperties.notifyAtMostEvery].
 *
 * Right after inviting, the email goes out if the address is due. If it isn't (it was emailed recently), or
 * sending fails, the invitations wait and InvitationNotificationJob sends them later, so every invitation is
 * mentioned in an email eventually.
 *
 * Sending is claim, send, confirm, each step its own short transaction, with no database locks (Aurora DSQL
 * has none, and none is held while the mail server is slow):
 * 1. Claim: check the address's InvitationRecipient row, mark the due invitations claimed and update that row.
 *    Two servers claiming at once both update it, so one fails (a version conflict, or a commit conflict on
 *    DSQL) and backs off.
 * 2. Send the email.
 * 3. Confirm the invitations as notified, or, if sending failed, undo the claim so the job retries.
 * A server dying between 2 and 3 leaves the claim unconfirmed; after [InvitationProperties.notificationClaimTimeout]
 * it is sent again.
 */
@Component
class InvitationNotifier(
    private val invitationRepository: InvitationRepository,
    private val recipientRepository: InvitationRecipientRepository,
    private val companyRepository: CompanyRepository,
    private val userRepository: UserRepository,
    private val emailPort: EmailPort,
    private val properties: InvitationProperties,
    private val authProperties: AuthProperties,
    transactionManager: PlatformTransactionManager,
) {
    // Transactions of their own: after the inviting transaction commits, its resources are still bound
    private val newTransaction =
        TransactionTemplate(transactionManager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }

    private data class Claim(
        val email: String,
        val at: Instant,
        val invitationIds: List<Long>,
        /** To restore if sending fails */
        val previouslyNotifiedAt: Instant?,
        val notices: List<InvitationNotice>,
        val language: Language,
    )

    /** Never fails the invite: the invitations are saved, and the job retries the email */
    @TransactionalEventListener
    fun onInvitationsCreated(event: InvitationsCreated) {
        runCatching { notifyIfDue(event.email) }
            .onFailure { logger.warn(it) { "Invitation email failed; it will be retried" } }
    }

    /**
     * Sends the address one email about its invitations not yet mentioned, unless it got one too recently or
     * another server is sending it right now.
     * @return whether this call sent an email
     * @throws EmailDeliveryException if sending failed; the claim is undone, so it is retried
     */
    fun notifyIfDue(email: String): Boolean {
        val claim = claim(email) ?: return false
        try {
            emailPort.sendInvitations(claim.email, claim.notices, invitationsPage(), claim.language)
        } catch (e: EmailDeliveryException) {
            release(claim)
            throw e
        }
        confirm(claim)
        return true
    }

    private fun claim(email: String): Claim? =
        try {
            newTransaction.execute { claimInTransaction(email) }
        } catch (_: ConcurrencyFailureException) {
            // Another server claimed this address at the same time and won; it sends the email
            null
        } catch (_: DataIntegrityViolationException) {
            // Both created the address's first InvitationRecipient row; the other one won
            null
        }

    private fun claimInTransaction(email: String): Claim? {
        val now = Instant.now()
        val recipient = recipientRepository.findById(email).orElse(null) ?: InvitationRecipient(email)
        val previous = recipient.lastNotifiedAt
        if (previous != null && previous.isAfter(now - properties.notifyAtMostEvery)) return null

        val due = invitationRepository.findDue(email, now, staleBefore = now - properties.notificationClaimTimeout)
        if (due.isEmpty()) return null

        // The update every concurrent claim makes, so only one can commit
        recipient.notified(now)
        recipientRepository.saveAndFlush(recipient)
        due.forEach { it.claimNotification(now) }
        invitationRepository.saveAllAndFlush(due)

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
        return Claim(email, now, due.map { it.id }, previous, notices, language)
    }

    private fun confirm(claim: Claim) {
        newTransaction.executeWithoutResult {
            val claimed =
                invitationRepository.findAllById(claim.invitationIds).filter {
                    it.notificationClaimedAt ==
                        claim.at
                }
            claimed.forEach { it.markNotified(claim.at) }
            invitationRepository.saveAll(claimed)
        }
    }

    private fun release(claim: Claim) {
        newTransaction.executeWithoutResult {
            val claimed =
                invitationRepository.findAllById(claim.invitationIds).filter {
                    it.notificationClaimedAt ==
                        claim.at
                }
            claimed.forEach { it.releaseNotification() }
            invitationRepository.saveAll(claimed)
            recipientRepository.findById(claim.email).ifPresent {
                // Nothing went out, so the address isn't made to wait an hour for the retry
                if (it.lastNotifiedAt == claim.at) {
                    it.notified(claim.previouslyNotifiedAt)
                    recipientRepository.save(it)
                }
            }
        }
    }

    private fun invitationsPage(): URI =
        UriComponentsBuilder
            .fromUri(authProperties.frontendUrl)
            .path("/invitations")
            .build()
            .toUri()
}
