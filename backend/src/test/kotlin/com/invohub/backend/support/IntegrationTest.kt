package com.invohub.backend.support

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

/**
 * Boots the full application against Testcontainers-backed infrastructure.
 * Tests sharing this annotation share one cached context and one set of containers.
 * Emails are captured by [CapturingEmailPort] instead of being sent.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class, CapturingEmailPort::class)
annotation class IntegrationTest
