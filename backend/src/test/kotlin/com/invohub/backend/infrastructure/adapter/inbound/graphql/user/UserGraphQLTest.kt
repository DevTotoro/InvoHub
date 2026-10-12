package com.invohub.backend.infrastructure.adapter.inbound.graphql.user

import com.invohub.backend.support.CapturingEmailPort
import com.invohub.backend.support.DatabaseCleaner
import com.invohub.backend.support.IntegrationTest
import com.invohub.backend.support.graphql
import com.invohub.backend.support.signIn
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.web.servlet.MockMvc

@IntegrationTest
@AutoConfigureMockMvc
class UserGraphQLTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var emailPort: CapturingEmailPort

    @Autowired
    private lateinit var databaseCleaner: DatabaseCleaner

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    private val setLanguage =
        """
        mutation SetLanguage(${'$'}language: Language!) { setLanguage(language: ${'$'}language) { language } }
        """.trimIndent()

    @BeforeEach
    fun reset() {
        emailPort.clear()
        databaseCleaner.clean()
        redisTemplate.connectionFactory!!.connection.use { it.serverCommands().flushAll() }
    }

    @Test
    fun `me includes the user's language`() {
        val session = mockMvc.signIn(emailPort, "jane@example.com")

        mockMvc.graphql(session, "{ me { language } }").andExpect {
            jsonPath("$.data.me.language") { value("EN") }
        }
    }

    @Test
    fun `the user can change their language`() {
        val session = mockMvc.signIn(emailPort, "jane@example.com")

        mockMvc.graphql(session, setLanguage, mapOf("language" to "EL")).andExpect {
            jsonPath("$.data.setLanguage.language") { value("EL") }
        }

        mockMvc.graphql(session, "{ me { language } }").andExpect {
            jsonPath("$.data.me.language") { value("EL") }
        }
    }

    @Test
    fun `an unknown language is refused`() {
        val session = mockMvc.signIn(emailPort, "jane@example.com")

        mockMvc.graphql(session, setLanguage, mapOf("language" to "XX")).andExpect {
            jsonPath("$.errors") { isNotEmpty() }
            jsonPath("$.data") { doesNotExist() }
        }
    }
}
