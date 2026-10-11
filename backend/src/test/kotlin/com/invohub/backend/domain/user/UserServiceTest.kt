package com.invohub.backend.domain.user

import com.invohub.backend.support.IntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@IntegrationTest
class UserServiceTest {
    @Autowired
    private lateinit var userService: UserService

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun clean() = userRepository.deleteAll()

    @Test
    fun `creates a user with a normalized email`() {
        val user = userService.getOrCreateUser(" Jane@Example.com ")

        assertEquals("jane@example.com", user.email)
    }

    @Test
    fun `returns the existing user for any case variant`() {
        val created = userService.getOrCreateUser("jane@example.com")

        val found = userService.getOrCreateUser("JANE@example.com")

        assertEquals(created.id, found.id)
        assertEquals(1, userRepository.count())
    }

    @Test
    fun `the database rejects non-normalized emails`() {
        assertFailsWith<DataIntegrityViolationException> {
            jdbcTemplate.update("INSERT INTO users (id, email) VALUES (1, 'Jane@Example.com')")
        }
    }
}
