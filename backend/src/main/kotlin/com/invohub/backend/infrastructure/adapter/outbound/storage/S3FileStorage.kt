package com.invohub.backend.infrastructure.adapter.outbound.storage

import com.invohub.backend.domain.storage.FileStorage
import com.invohub.backend.domain.storage.StoredFile
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.NoSuchBucketException
import software.amazon.awssdk.services.s3.model.NoSuchKeyException

private val logger = KotlinLogging.logger {}

/** Files in an S3 bucket: AWS S3 in production, Silo (an S3-compatible server) locally and in tests. */
@Component
class S3FileStorage(
    private val properties: StorageProperties,
) : FileStorage {
    private val s3: S3Client =
        S3Client
            .builder()
            .region(Region.of(properties.region))
            .apply {
                properties.endpoint?.let {
                    endpointOverride(it)
                    // Local servers serve buckets as paths, not as subdomains
                    forcePathStyle(true)
                }
                if (properties.accessKey != null && properties.secretKey != null) {
                    credentialsProvider(
                        StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(properties.accessKey, properties.secretKey),
                        ),
                    )
                } else {
                    credentialsProvider(DefaultCredentialsProvider.builder().build())
                }
            }.build()

    @PostConstruct
    fun createBucketIfAsked() {
        if (!properties.createBucket) return
        try {
            s3.headBucket { it.bucket(properties.bucket) }
        } catch (_: NoSuchBucketException) {
            logger.info { "Creating bucket ${properties.bucket}" }
            s3.createBucket { it.bucket(properties.bucket) }
        }
    }

    override fun put(
        key: String,
        bytes: ByteArray,
        contentType: String,
    ) {
        s3.putObject({ it.bucket(properties.bucket).key(key).contentType(contentType) }, RequestBody.fromBytes(bytes))
    }

    override fun get(key: String): StoredFile? =
        try {
            val response = s3.getObjectAsBytes { it.bucket(properties.bucket).key(key) }
            StoredFile(response.asByteArray(), response.response().contentType())
        } catch (_: NoSuchKeyException) {
            null
        }

    override fun delete(key: String) {
        s3.deleteObject { it.bucket(properties.bucket).key(key) }
    }
}
