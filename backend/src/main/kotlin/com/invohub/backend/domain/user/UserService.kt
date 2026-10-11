package com.invohub.backend.domain.user

import com.invohub.backend.domain.auth.UserSessions
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(
    private val userRepository: UserRepository,
    private val userSessions: UserSessions,
) {
    fun findById(id: Long): User? = userRepository.findById(id).orElse(null)

    /**
     * Not @Transactional on purpose: the insert must run in its own transaction, so that losing a race
     * to a concurrent insert leaves nothing rolled back and the existing user can be read instead.
     */
    fun getOrCreateUser(email: String): User {
        val normalized = normalizeEmail(email)
        userRepository.findByEmail(normalized)?.let { return it }

        return try {
            userRepository.saveAndFlush(User(normalized))
        } catch (e: DataIntegrityViolationException) {
            // A concurrent request created the user between the lookup and the insert
            userRepository.findByEmail(normalized) ?: throw e
        }
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
        userSessions.endAll(id)
    }

    /** @throws NoSuchElementException if there is no such user */
    @Transactional
    fun unsuspendUser(id: Long) {
        userRepository.findById(id).orElseThrow().unsuspend()
    }
}
