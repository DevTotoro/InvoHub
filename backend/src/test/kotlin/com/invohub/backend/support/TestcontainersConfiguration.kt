package com.invohub.backend.support

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.test.context.DynamicPropertyRegistrar
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
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

    /** S3-compatible storage, as locally (compose.yaml); the app creates its bucket on startup */
    @Bean
    fun storageContainer(): GenericContainer<*> =
        GenericContainer(DockerImageName.parse("pgsty/silo:RELEASE.2026-09-03T13-18-01Z"))
            .withExposedPorts(9000)
            .withEnv(mapOf("MINIO_ROOT_USER" to STORAGE_KEY, "MINIO_ROOT_PASSWORD" to STORAGE_SECRET))
            .withCommand("server", "/data")
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000))

    @Bean
    fun storageProperties(storageContainer: GenericContainer<*>) =
        DynamicPropertyRegistrar { registry ->
            val endpoint = "http://${storageContainer.host}:${storageContainer.getMappedPort(9000)}"
            registry.add("invohub.storage.bucket") { STORAGE_BUCKET }
            registry.add("invohub.storage.endpoint") { endpoint }
            registry.add("invohub.storage.public-url") { "$endpoint/$STORAGE_BUCKET" }
            registry.add("invohub.storage.access-key") { STORAGE_KEY }
            registry.add("invohub.storage.secret-key") { STORAGE_SECRET }
            registry.add("invohub.storage.set-up-bucket") { true }
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

        private const val STORAGE_BUCKET = "invohub-test"
        private const val STORAGE_KEY = "invohub"
        private const val STORAGE_SECRET = "invohub-test-secret"

        /** From "Restricted Valkey and Redis OSS commands" in the ElastiCache docs */
        private val SERVERLESS_UNAVAILABLE_COMMANDS = listOf("CONFIG", "PSUBSCRIBE", "KEYS")
    }
}
