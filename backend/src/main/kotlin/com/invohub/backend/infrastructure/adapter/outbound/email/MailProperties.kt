package com.invohub.backend.infrastructure.adapter.outbound.email

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue

@ConfigurationProperties("invohub.mail")
data class MailProperties(
    @DefaultValue("no-reply@invohub.com") val from: String,
)
