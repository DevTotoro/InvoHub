package com.invohub.backend.infrastructure.adapter.outbound.storage

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue
import java.net.URI

@ConfigurationProperties("invohub.storage")
data class StorageProperties(
    val bucket: String,
    @DefaultValue("eu-central-1") val region: String,
    /** Set locally, for an S3-compatible server (Silo); in production S3's own endpoint is used */
    val endpoint: URI? = null,
    /** Credentials for the local server; in production the app's IAM role is used instead */
    val accessKey: String? = null,
    val secretKey: String? = null,
    /** Create the bucket on startup if it's missing: locally only */
    @DefaultValue("false") val createBucket: Boolean,
)
