package com.invohub.backend.domain.company

import com.invohub.backend.domain.audit.AuditAction
import com.invohub.backend.domain.audit.AuditEvent
import com.invohub.backend.domain.audit.AuditLog
import com.invohub.backend.domain.audit.AuditTarget
import com.invohub.backend.domain.common.InvalidInputException
import com.invohub.backend.domain.storage.FileStorage
import com.invohub.backend.domain.storage.StoredFile
import io.github.oshai.kotlinlogging.KotlinLogging
import io.hypersistence.tsid.TSID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

/**
 * A company's logo: optional, and not part of invoicing setup. Only PNG, JPEG or WebP, recognized by their
 * contents rather than what the browser claims. SVG is refused: it can carry scripts.
 */
@Service
class LogoService(
    private val companyRepository: CompanyRepository,
    private val storage: FileStorage,
    private val auditLog: AuditLog,
) {
    companion object {
        const val MAX_BYTES = 1024 * 1024
    }

    /** @throws InvalidInputException "logo" with "empty", "too_large" or "unsupported_type" */
    @Transactional
    fun change(
        access: CompanyAccess,
        bytes: ByteArray,
    ) {
        access.require(CompanyPermission.MANAGE_SETTINGS)
        if (bytes.isEmpty()) throw InvalidInputException("logo", "empty")
        if (bytes.size > MAX_BYTES) throw InvalidInputException("logo", "too_large")
        val type = ImageType.of(bytes) ?: throw InvalidInputException("logo", "unsupported_type")

        val company = companyRepository.findById(access.companyId).orElseThrow()
        val previous = company.logoKey
        // A new key each time, so browsers never show a stale cached logo
        val key = "logos/${company.getPublicId()}/${TSID.fast()}.${type.extension}"
        storage.put(key, bytes, type.contentType)
        company.changeLogo(key)
        companyRepository.saveAndFlush(company)
        record(company.id, if (previous == null) "added" else "replaced")
        previous?.let(::deleteQuietly)
    }

    @Transactional
    fun remove(access: CompanyAccess) {
        access.require(CompanyPermission.MANAGE_SETTINGS)
        val company = companyRepository.findById(access.companyId).orElseThrow()
        val previous = company.logoKey ?: return
        company.changeLogo(null)
        companyRepository.saveAndFlush(company)
        record(company.id, "removed")
        deleteQuietly(previous)
    }

    /** The logo, for anyone in the company; null without one */
    @Transactional(readOnly = true)
    fun logo(access: CompanyAccess): StoredFile? {
        access.require(CompanyPermission.VIEW)
        return access.company.logoKey?.let(storage::get)
    }

    /** A leftover file harms nobody, so failing to delete one doesn't fail the change */
    private fun deleteQuietly(key: String) {
        runCatching { storage.delete(key) }.onFailure { logger.warn(it) { "Couldn't delete old logo $key" } }
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
