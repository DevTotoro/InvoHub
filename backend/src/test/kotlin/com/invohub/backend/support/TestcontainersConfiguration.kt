package com.invohub.backend.support

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.test.context.DynamicPropertyRegistrar
import org.testcontainers.containers.GenericContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Throwaway Postgres and Redis containers. Images match compose.yaml so tests run against the same versions
 * as local development.
 *
 * Postgres is wired up like production: Flyway migrates as the database owner, while the app connects as the
 * restricted invohub_app login (created by db/create-app-login.sql), so missing grants fail the tests.
 *
 * Redis refuses the commands ElastiCache Serverless doesn't allow and that are easy to start depending on by
 * accident (e.g. through Spring Session's indexed repository), so the tests fail instead of production.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {
    @Bean
    fun postgresContainer(): PostgreSQLContainer =
        PostgreSQLContainer(DockerImageName.parse("postgres:18")).withInitScript("db/create-app-login.sql")

    @Bean
    fun postgresProperties(postgres: PostgreSQLContainer) =
        DynamicPropertyRegistrar { registry ->
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username") { APP_LOGIN }
            registry.add("spring.datasource.password") { APP_LOGIN }
            registry.add("spring.flyway.user", postgres::getUsername)
            registry.add("spring.flyway.password", postgres::getPassword)
        }

    @Bean
    @ServiceConnection(name = "redis")
    fun redisContainer(): GenericContainer<*> =
        GenericContainer(DockerImageName.parse("redis:8"))
            .withExposedPorts(6379)
            .withCommand(
                "redis-server",
                *SERVERLESS_UNAVAILABLE_COMMANDS.flatMap { listOf("--rename-command", it, "") }.toTypedArray(),
            )

    companion object {
        /** Username and password of the app's login, see db/create-app-login.sql */
        const val APP_LOGIN = "invohub_app"

        /** From "Restricted Valkey and Redis OSS commands" in the ElastiCache docs */
        private val SERVERLESS_UNAVAILABLE_COMMANDS = listOf("CONFIG", "PSUBSCRIBE", "KEYS")
    }
}
