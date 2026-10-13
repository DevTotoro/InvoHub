package com.invohub.backend.infrastructure.adapter.outbound.storage

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue
import java.net.URI

@ConfigurationProperties("invohub.storage")
data class StorageProperties(
    val bucket: String,
    @DefaultValue("eu-central-1") val region: String,
    /**
     * Where public files (`logos/…`) are read from, without a trailing slash: the CDN in production, the bucket
     * itself locally
     */
    val publicUrl: URI,
    /**
     * Set locally, for an S3-compatible server (Silo); in production S3's own endpoint is used. Browsers upload
     * to it too, so it must be an address they can reach.
     */
    val endpoint: URI? = null,
    /** Credentials for the local server; in production the app's IAM role is used instead */
    val accessKey: String? = null,
    val secretKey: String? = null,
    /**
     * Create the bucket on startup if it's missing, set up like production's (public logos, pending uploads
     * removed after a day): locally only. In production that is the infrastructure's job.
     */
    @DefaultValue("false") val setUpBucket: Boolean,
)
