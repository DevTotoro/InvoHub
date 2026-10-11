package com.invohub.backend.infrastructure.persistence

import com.invohub.backend.support.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The app connects with a login that can use the data but not change the schema. */
@IntegrationTest
class DatabasePrivilegesTest {
    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `the app connects as its own login, not the owner`() {
        assertEquals("invohub_app", jdbcTemplate.queryForObject("SELECT current_user", String::class.java))
    }

    @Test
    fun `the app cannot change the schema`() {
        assertFailsWith<DataAccessException> { jdbcTemplate.execute("CREATE TABLE intruder (id INT)") }
        assertFailsWith<DataAccessException> { jdbcTemplate.execute("ALTER TABLE users ADD COLUMN intruder INT") }
        assertFailsWith<DataAccessException> { jdbcTemplate.execute("DROP TABLE users") }
    }

    @Test
    fun `the app cannot touch the migration history`() {
        assertFailsWith<DataAccessException> { jdbcTemplate.update("DELETE FROM flyway_schema_history") }
    }
}
