package com.invohub.backend.support

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.GenericContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Throwaway Postgres and Redis containers, wired into the context through @ServiceConnection.
 * Images match compose.yaml so tests run against the same versions as local development.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {
    @Bean
    @ServiceConnection
    fun postgresContainer(): PostgreSQLContainer = PostgreSQLContainer(DockerImageName.parse("postgres:18"))

    @Bean
    @ServiceConnection(name = "redis")
    fun redisContainer(): GenericContainer<*> =
        GenericContainer(DockerImageName.parse("redis:8")).withExposedPorts(6379)
}
