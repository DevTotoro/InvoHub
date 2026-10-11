package com.invohub.backend.infrastructure.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.auditing.DateTimeProvider
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import java.time.Instant
import java.util.Optional

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "instantDateTimeProvider")
class JpaAuditingConfig {
    @Bean
    fun instantDateTimeProvider(): DateTimeProvider = DateTimeProvider { Optional.of(Instant.now()) }
}
