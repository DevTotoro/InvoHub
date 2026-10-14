package com.invohub.backend.support

import org.springframework.boot.test.context.TestComponent
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Empties the data tables between tests, children before parents so foreign keys don't object. The audit log
 * can't be emptied (it's append-only); look events up by something unique to the test instead.
 */
@TestComponent
class DatabaseCleaner(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun clean() {
        jdbcTemplate.execute("DELETE FROM contacts")
        jdbcTemplate.execute("DELETE FROM ownership_transfers")
        jdbcTemplate.execute("DELETE FROM invitations")
        jdbcTemplate.execute("DELETE FROM company_bank_accounts")
        jdbcTemplate.execute("DELETE FROM company_members")
        jdbcTemplate.execute("DELETE FROM companies")
        jdbcTemplate.execute("DELETE FROM users")
    }
}
