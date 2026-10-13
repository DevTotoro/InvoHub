package com.invohub.backend.domain.storage

import java.net.URI
import java.time.Duration
import java.time.Instant

/** What a stored file is, without its contents */
class FileInfo(
    val size: Long,
    val contentType: String?,
)

/** Where a browser uploads one file itself, so the file never passes through InvoHub */
class UploadTarget(
    /** To PUT the file to */
    val url: URI,
    /** Headers the upload must send, exactly as given */
    val headers: Map<String, String>,
    val expiresAt: Instant,
)

/**
 * Where files live (S3 in production, an S3-compatible server locally). Keys are paths:
 * - `pending/…`: browser uploads not yet checked; never readable, and removed after a day
 * - `logos/…`: public, served by the CDN
 */
interface FileStorage {
    /**
     * A signed URL a browser can upload exactly [size] bytes of [contentType] to, at [key], within [validFor].
     * Anything else is refused by the storage itself.
     */
    fun prepareUpload(
        key: String,
        contentType: String,
        size: Long,
        validFor: Duration,
    ): UploadTarget

    /** Null if there is no such file */
    fun info(key: String): FileInfo?

    /** The first [count] bytes (fewer if the file is shorter), e.g. to tell its type; null if there is no such file */
    fun readStart(
        key: String,
        count: Int,
    ): ByteArray?

    /** Copies a file within storage, giving the copy [contentType] and [cacheControl] */
    fun copy(
        from: String,
        to: String,
        contentType: String,
        cacheControl: String,
    )

    /** Removing a file that isn't there is fine */
    fun delete(key: String)

    /** Where anyone can read a public file (`logos/…`): the CDN in production */
    fun publicUrl(key: String): URI
}
