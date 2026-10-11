package com.invohub.backend.domain.user

import org.springframework.stereotype.Service

@Service
class UserService(
    private val userRepository: UserRepository,
) {
    fun getOrCreateUser(email: String) = userRepository.findByEmail(email) ?: userRepository.save(User(email))
}
