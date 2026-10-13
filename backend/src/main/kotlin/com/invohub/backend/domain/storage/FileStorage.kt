package com.invohub.backend.domain.storage

/** A stored file's contents */
class StoredFile(
    val bytes: ByteArray,
    val contentType: String,
)

/** Where files live (S3 in production, an S3-compatible server locally). Keys are paths, e.g. `logos/…` */
interface FileStorage {
    fun put(
        key: String,
        bytes: ByteArray,
        contentType: String,
    )

    /** Null if there is no such file */
    fun get(key: String): StoredFile?

    /** Removing a file that isn't there is fine */
    fun delete(key: String)
}
