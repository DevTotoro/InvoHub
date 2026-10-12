package com.invohub.backend.domain.invitation

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue
import java.time.Duration

@ConfigurationProperties("invohub.invitations")
data class InvitationProperties(
    /** How long an invitation stays open */
    @DefaultValue("7d") val expiry: Duration,
    /**
     * Invitations one person can send in a day, across all their companies (so creating companies doesn't
     * help). Far above real use: it bounds how much email one account can make InvoHub send.
     */
    @DefaultValue("100") val maxPerInviterPerDay: Int,
    /** Invitation emails one address can get in a day, whoever sends them, so nobody is flooded */
    @DefaultValue("10") val maxPerRecipientPerDay: Int,
)
