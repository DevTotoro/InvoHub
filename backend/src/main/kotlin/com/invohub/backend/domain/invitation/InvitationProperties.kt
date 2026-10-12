package com.invohub.backend.domain.invitation

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue
import java.time.Duration

@ConfigurationProperties("invohub.invitations")
data class InvitationProperties(
    /** How long an invitation stays open */
    @DefaultValue("7d") val expiry: Duration,
    /**
     * Nobody gets more than one invitation email this often, however many companies invite them. Later
     * invitations wait, and the next email lists them all (see InvitationNotifier).
     */
    @DefaultValue("1h") val notifyAtMostEvery: Duration,
    /**
     * Limits that real use never reaches; they bound how much email one account can make InvoHub send before
     * someone notices. Counted over the last day.
     */
    @DefaultValue("100") val maxPerCompanyPerDay: Int,
    @DefaultValue("200") val maxPerInviterPerDay: Int,
)
