package com.invohub.backend.domain.user

import com.invohub.backend.domain.audit.AuditAction
import com.invohub.backend.domain.audit.AuditEvent
import com.invohub.backend.domain.audit.AuditLog
import com.invohub.backend.domain.audit.AuditTarget
import com.invohub.backend.domain.auth.UserSessions
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

@Service
class UserService(
    private val userRepository: UserRepository,
    private val userSessions: UserSessions,
    private val auditLog: AuditLog,
    transactionManager: PlatformTransactionManager,
) {
    private val newTransaction =
        TransactionTemplate(transactionManager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }

    fun findById(id: Long): User? = userRepository.findById(id).orElse(null)

    /**
     * The user and its user.created event are inserted in a transaction of their own, so that losing a race
     * to a concurrent insert leaves nothing else rolled back and the existing user can be read instead.
     */
    fun getOrCreateUser(email: String): User {
        val normalized = normalizeEmail(email)
        userRepository.findByEmail(normalized)?.let { return it }

        return try {
            newTransaction.execute { createUser(normalized) }
        } catch (e: DataIntegrityViolationException) {
            // A concurrent request created the user between the lookup and the insert
            userRepository.findByEmail(normalized) ?: throw e
        }
    }

    private fun createUser(email: String): User {
        val user = userRepository.saveAndFlush(User(email))
        // The user signed themselves up
        auditLog.record(
            AuditEvent(
                AuditAction.USER_CREATED,
                actorUserId = user.id,
                target = AuditTarget.user(user.id),
                details = mapOf("email" to user.email),
            ),
        )
        return user
    }

    /**
     * Suspends the user and signs them out everywhere. Always suspend through here: setting `suspended`
     * directly in the database blocks new sign-ins, but leaves existing sessions running until they expire.
     *
     * The sessions are ended inside the transaction, so if that fails the suspension is rolled back and can
     * be retried, rather than leaving a suspended user signed in.
     *
     * @throws NoSuchElementException if there is no such user
     */
    @Transactional
    fun suspendUser(
        id: Long,
        reason: String,
    ) {
        val user = userRepository.findById(id).orElseThrow()
        user.suspend(reason)
        auditLog.record(
            AuditEvent(AuditAction.USER_SUSPENDED, target = AuditTarget.user(id), details = mapOf("reason" to reason)),
        )
        userSessions.endAll(id)
    }

    /** @throws NoSuchElementException if there is no such user */
    @Transactional
    fun unsuspendUser(id: Long) {
        userRepository.findById(id).orElseThrow().unsuspend()
        auditLog.record(AuditEvent(AuditAction.USER_UNSUSPENDED, target = AuditTarget.user(id)))
    }
}
