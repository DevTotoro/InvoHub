package com.invohub.backend.domain.invitation

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Sends the invitation emails that had to wait: for addresses emailed too recently, or whose email failed.
 * Safe on several servers at once: InvitationNotifier locks each address while it works on it.
 */
@Component
@ConditionalOnProperty("invohub.invitations.notification-job.enabled", havingValue = "true", matchIfMissing = true)
class InvitationNotificationJob(
    private val invitationRepository: InvitationRepository,
    private val notifier: InvitationNotifier,
) {
    @Scheduled(fixedDelayString = "\${invohub.invitations.notification-job.every:PT5M}")
    fun run() {
        invitationRepository.emailsWithDueNotifications(Instant.now()).forEach { email ->
            runCatching { notifier.notifyIfDue(email) }
                .onFailure { logger.warn(it) { "Invitation email failed; it will be retried" } }
        }
    }
}
