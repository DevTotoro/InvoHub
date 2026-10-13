package com.invohub.backend.domain.company

import com.invohub.backend.domain.audit.AuditAction
import com.invohub.backend.domain.audit.AuditEvent
import com.invohub.backend.domain.audit.AuditLog
import com.invohub.backend.domain.audit.AuditTarget
import com.invohub.backend.domain.common.InvalidInputException
import com.invohub.backend.domain.storage.FileStorage
import com.invohub.backend.domain.storage.UploadTarget
import io.github.oshai.kotlinlogging.KotlinLogging
import io.hypersistence.tsid.TSID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.net.URI
import java.time.Duration
import java.util.UUID

private val logger = KotlinLogging.logger {}

/** A logo upload the browser can now send, and its ID for [LogoService.change] */
class LogoUpload(
    val uploadId: String,
    val target: UploadTarget,
)

/**
 * A company's logo: optional, and not part of invoicing setup. Logos are public (they're printed on every
 * invoice), served by the CDN.
 *
 * The browser uploads the file straight to storage, never through InvoHub: [prepareUpload] signs it a URL for
 * one file of the stated type and size, then [change] checks what actually arrived before it becomes the logo.
 * Only PNG, JPEG or WebP, recognized by their contents rather than what the browser claims. SVG is refused: it
 * can carry scripts.
 */
@Service
class LogoService(
    private val companyRepository: CompanyRepository,
    private val storage: FileStorage,
    private val auditLog: AuditLog,
) {
    companion object {
        const val MAX_BYTES = 1024 * 1024
        private val UPLOAD_VALID_FOR = Duration.ofMinutes(5)

        // Each logo has its own key, so its URL never shows anything else and can be cached for good
        private const val CACHE_CONTROL = "public, max-age=31536000, immutable"
    }

    /** @throws InvalidInputException "logo" with "empty", "too_large" or "unsupported_type" */
    fun prepareUpload(
        access: CompanyAccess,
        contentType: String,
        size: Long,
    ): LogoUpload {
        access.require(CompanyPermission.MANAGE_SETTINGS)
        if (size <= 0) throw InvalidInputException("logo", "empty")
        if (size > MAX_BYTES) throw InvalidInputException("logo", "too_large")
        val type = ImageType.entries.find { it.contentType == contentType }
        if (type == null) throw InvalidInputException("logo", "unsupported_type")

        val uploadId = UUID.randomUUID().toString()
        val target = storage.prepareUpload(pendingKey(access, uploadId), type.contentType, size, UPLOAD_VALID_FOR)
        return LogoUpload(uploadId, target)
    }

    /**
     * Makes the uploaded file the logo, once it is checked. Files that fail the check are deleted.
     * @throws InvalidInputException "logo" with "upload_missing" (never uploaded, expired, or not this company's),
     * "too_large" or "unsupported_type"
     */
    @Transactional
    fun change(
        access: CompanyAccess,
        uploadId: String,
    ): Company {
        access.require(CompanyPermission.MANAGE_SETTINGS)
        // Only this company's uploads can be named: the key is built here, from the company
        val id = runCatching { UUID.fromString(uploadId).toString() }.getOrNull() ?: throw missingUpload()
        val pending = pendingKey(access, id)
        val info = storage.info(pending) ?: throw missingUpload()
        val type = storage.readStart(pending, ImageType.BYTES_NEEDED)?.let(ImageType::of)
        val problem =
            when {
                info.size > MAX_BYTES -> "too_large"
                // The type it was uploaded as is the type it will be served as, so they must agree
                type == null || type.contentType != info.contentType -> "unsupported_type"
                else -> null
            }
        if (problem != null) {
            deleteQuietly(pending)
            throw InvalidInputException("logo", problem)
        }

        val company = companyRepository.findById(access.companyId).orElseThrow()
        val previous = company.logoKey
        val key = "logos/${company.getPublicId()}/${TSID.fast()}.${type!!.extension}"
        storage.copy(pending, key, type.contentType, CACHE_CONTROL)
        company.changeLogo(key)
        val saved = companyRepository.saveAndFlush(company)
        record(company.id, if (previous == null) "added" else "replaced")
        // Only once saved: until then the old logo is still the company's, and the upload can be tried again
        afterCommit {
            deleteQuietly(pending)
            previous?.let(::deleteQuietly)
        }
        return saved
    }

    @Transactional
    fun remove(access: CompanyAccess): Company {
        access.require(CompanyPermission.MANAGE_SETTINGS)
        val company = companyRepository.findById(access.companyId).orElseThrow()
        val previous = company.logoKey ?: return company
        company.changeLogo(null)
        val saved = companyRepository.saveAndFlush(company)
        record(company.id, "removed")
        afterCommit { deleteQuietly(previous) }
        return saved
    }

    /** Where anyone can see the company's logo; null without one */
    fun logoUrl(company: Company): URI? = company.logoKey?.let(storage::publicUrl)

    private fun pendingKey(
        access: CompanyAccess,
        uploadId: String,
    ) = "pending/${access.company.getPublicId()}/$uploadId"

    private fun missingUpload() = InvalidInputException("logo", "upload_missing")

    private fun afterCommit(action: () -> Unit) {
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() = action()
            },
        )
    }

    /** A leftover file harms nobody, so failing to delete one doesn't fail the change */
    private fun deleteQuietly(key: String) {
        runCatching { storage.delete(key) }.onFailure { logger.warn(it) { "Couldn't delete $key" } }
    }

    private fun record(
        companyId: Long,
        change: String,
    ) {
        auditLog.record(
            AuditEvent(
                AuditAction.COMPANY_UPDATED,
                companyId = companyId,
                target = AuditTarget.company(companyId),
                details = mapOf("logo" to change),
            ),
        )
    }

    private enum class ImageType(
        val contentType: String,
        val extension: String,
    ) {
        PNG("image/png", "png"),
        JPEG("image/jpeg", "jpg"),
        WEBP("image/webp", "webp"),
        ;

        companion object {
            /** Enough of the start of a file to tell its type */
            const val BYTES_NEEDED = 12

            /** By the file's first bytes, its "magic number" */
            fun of(bytes: ByteArray): ImageType? {
                fun startsWith(vararg prefix: Int) =
                    bytes.size >= prefix.size && prefix.indices.all { bytes[it] == prefix[it].toByte() }
                return when {
                    startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> PNG
                    startsWith(0xFF, 0xD8, 0xFF) -> JPEG
                    bytes.size >= 12 &&
                        String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                        String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> WEBP
                    else -> null
                }
            }
        }
    }
}
