package com.invohub.backend.domain.user

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service

@Service
class UserService(
    private val userRepository: UserRepository,
) {
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
}
