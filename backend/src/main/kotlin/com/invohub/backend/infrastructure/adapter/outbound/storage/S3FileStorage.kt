package com.invohub.backend.infrastructure.adapter.outbound.storage

import com.invohub.backend.domain.storage.FileInfo
import com.invohub.backend.domain.storage.FileStorage
import com.invohub.backend.domain.storage.UploadTarget
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.ExpirationStatus
import software.amazon.awssdk.services.s3.model.MetadataDirective
import software.amazon.awssdk.services.s3.model.NoSuchBucketException
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI
import java.time.Duration

private val logger = KotlinLogging.logger {}

/** Files in an S3 bucket: AWS S3 in production, Silo (an S3-compatible server) locally and in tests. */
@Component
class S3FileStorage(
    private val properties: StorageProperties,
) : FileStorage {
    private val credentials: AwsCredentialsProvider =
        if (properties.accessKey != null && properties.secretKey != null) {
            StaticCredentialsProvider.create(AwsBasicCredentials.create(properties.accessKey, properties.secretKey))
        } else {
            DefaultCredentialsProvider.builder().build()
        }

    // Local servers serve buckets as paths, not as subdomains
    private val s3Configuration = S3Configuration.builder().pathStyleAccessEnabled(properties.endpoint != null).build()

    private val s3: S3Client =
        S3Client
            .builder()
            .region(Region.of(properties.region))
            .credentialsProvider(credentials)
            .serviceConfiguration(s3Configuration)
            .apply { properties.endpoint?.let(::endpointOverride) }
            .build()

    private val presigner: S3Presigner =
        S3Presigner
            .builder()
            .region(Region.of(properties.region))
            .credentialsProvider(credentials)
            .serviceConfiguration(s3Configuration)
            .apply { properties.endpoint?.let(::endpointOverride) }
            .build()

    @PostConstruct
    fun setUpBucketIfAsked() {
        if (!properties.setUpBucket) return
        val bucket = properties.bucket
        try {
            s3.headBucket { it.bucket(bucket) }
        } catch (_: NoSuchBucketException) {
            logger.info { "Creating bucket $bucket" }
            s3.createBucket { it.bucket(bucket) }
        }
        // Set every time, so a bucket made before these rules gets them too.
        // Like the CDN in production: anyone may read logos, nothing else.
        s3.putBucketPolicy {
            it.bucket(bucket).policy(
                """
                {"Version": "2012-10-17", "Statement": [{"Effect": "Allow", "Principal": {"AWS": ["*"]},
                  "Action": ["s3:GetObject"], "Resource": ["arn:aws:s3:::$bucket/logos/*"]}]}
                """.trimIndent(),
            )
        }
        s3.putBucketLifecycleConfiguration { request ->
            request.bucket(bucket).lifecycleConfiguration { config ->
                config.rules({ rule ->
                    rule
                        .id("expire-pending-uploads")
                        .status(ExpirationStatus.ENABLED)
                        .filter { it.prefix("pending/") }
                        .expiration { it.days(1) }
                })
            }
        }
    }

    override fun prepareUpload(
        key: String,
        contentType: String,
        size: Long,
        validFor: Duration,
    ): UploadTarget {
        val presigned =
            presigner.presignPutObject { request ->
                request.signatureDuration(validFor).putObjectRequest {
                    // Both are signed: the upload must have exactly this type and size
                    it
                        .bucket(properties.bucket)
                        .key(key)
                        .contentType(contentType)
                        .contentLength(size)
                }
            }
        return UploadTarget(
            url = presigned.url().toURI(),
            // Browsers set these two themselves, and refuse to be told
            headers =
                presigned
                    .signedHeaders()
                    .filterKeys { it.lowercase() !in setOf("host", "content-length") }
                    .mapValues { it.value.joinToString(",") },
            expiresAt = presigned.expiration(),
        )
    }

    override fun info(key: String): FileInfo? =
        ifExists {
            val head = s3.headObject { it.bucket(properties.bucket).key(key) }
            FileInfo(head.contentLength(), head.contentType())
        }

    override fun readStart(
        key: String,
        count: Int,
    ): ByteArray? =
        ifExists {
            s3.getObjectAsBytes { it.bucket(properties.bucket).key(key).range("bytes=0-${count - 1}") }.asByteArray()
        }

    override fun copy(
        from: String,
        to: String,
        contentType: String,
        cacheControl: String,
    ) {
        s3.copyObject {
            it
                .sourceBucket(properties.bucket)
                .sourceKey(from)
                .destinationBucket(properties.bucket)
                .destinationKey(to)
                .metadataDirective(MetadataDirective.REPLACE)
                .contentType(contentType)
                .cacheControl(cacheControl)
        }
    }

    override fun delete(key: String) {
        s3.deleteObject { it.bucket(properties.bucket).key(key) }
    }

    override fun publicUrl(key: String): URI = URI.create("${properties.publicUrl}/$key")

    /** Null for a missing file; a HEAD request's 404 has no body, so it comes as a plain S3Exception */
    private fun <T> ifExists(action: () -> T): T? =
        try {
            action()
        } catch (e: S3Exception) {
            if (e.statusCode() == 404) null else throw e
        }
}
