package com.invohub.backend.domain.company

import com.invohub.backend.domain.audit.AuditEventRepository
import com.invohub.backend.domain.common.InvalidInputException
import com.invohub.backend.domain.storage.FileStorage
import com.invohub.backend.domain.user.Language
import com.invohub.backend.domain.user.UserService
import com.invohub.backend.support.IntegrationTest
import io.hypersistence.tsid.TSID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Against a real S3-compatible store (Silo, in a container), uploading the way a browser does */
@IntegrationTest
class LogoServiceTest {
    @Autowired
    private lateinit var logoService: LogoService

    @Autowired
    private lateinit var accessService: CompanyAccessService

    @Autowired
    private lateinit var companyService: CompanyService

    @Autowired
    private lateinit var memberRepository: CompanyMemberRepository

    @Autowired
    private lateinit var storage: FileStorage

    @Autowired
    private lateinit var userService: UserService

    @Autowired
    private lateinit var auditEventRepository: AuditEventRepository

    private val http = HttpClient.newHttpClient()

    @Test
    fun `an uploaded image becomes the public logo, replacing and removing delete the old files`() {
        val (company, access) = newCompany()

        val first = upload(access(), PNG)
        val firstUrl = assertNotNull(logoService.logoUrl(logoService.change(access(), first.uploadId)))
        // Public, without credentials, and cacheable for good
        val served = get(firstUrl)
        assertEquals(200, served.statusCode())
        assertContentEquals(PNG, served.body())
        assertEquals("image/png", served.headers().firstValue("Content-Type").get())
        assertEquals("public, max-age=31536000, immutable", served.headers().firstValue("Cache-Control").get())
        assertNull(storage.info(pendingKey(company, first.uploadId)))

        val secondUrl =
            assertNotNull(logoService.logoUrl(logoService.change(access(), upload(access(), JPEG).uploadId)))
        assertNotEquals(firstUrl, secondUrl)
        assertEquals("image/jpeg", get(secondUrl).headers().firstValue("Content-Type").get())
        assertEquals(404, get(firstUrl).statusCode())

        assertNull(logoService.logoUrl(logoService.remove(access())))
        assertEquals(404, get(secondUrl).statusCode())
        assertEquals(
            listOf("added", "replaced", "removed"),
            auditEventRepository
                .findByTargetTypeAndTargetIdOrderByOccurredAtAscIdAsc("company", company.id)
                .filter { it.action == "company.updated" }
                .map { it.details["logo"] },
        )
    }

    @Test
    fun `the signed upload takes only the size and type it was made for`() {
        val (_, access) = newCompany()
        val upload = logoService.prepareUpload(access(), "image/png", PNG.size.toLong())

        assertEquals(403, put(upload, PNG + byteArrayOf(0)).statusCode())
        assertEquals(403, put(upload, PNG, headers = mapOf("Content-Type" to "image/jpeg")).statusCode())
        assertInvalid("upload_missing") { logoService.change(access(), upload.uploadId) }
    }

    @Test
    fun `a file that isn't what it claims is refused and deleted`() {
        val (company, access) = newCompany()
        val svg = "<svg onload='alert(1)'/>".toByteArray()
        val upload = logoService.prepareUpload(access(), "image/png", svg.size.toLong())
        assertEquals(200, put(upload, svg).statusCode())

        assertInvalid("unsupported_type") { logoService.change(access(), upload.uploadId) }
        assertNull(storage.info(pendingKey(company, upload.uploadId)))

        // A real JPEG, uploaded as a PNG, would be served as one
        val mislabelled = logoService.prepareUpload(access(), "image/png", JPEG.size.toLong())
        put(mislabelled, JPEG)
        assertInvalid("unsupported_type") { logoService.change(access(), mislabelled.uploadId) }
    }

    @Test
    fun `only this company's own uploads can become its logo`() {
        val (_, access) = newCompany()
        val (_, otherAccess) = newCompany()
        val othersUpload = upload(otherAccess(), PNG)

        assertInvalid("upload_missing") { logoService.change(access(), othersUpload.uploadId) }
        assertInvalid("upload_missing") { logoService.change(access(), UUID.randomUUID().toString()) }
        assertInvalid("upload_missing") { logoService.change(access(), "../${othersUpload.uploadId}") }
    }

    @Test
    fun `refuses unsupported types and sizes before any upload`() {
        val (_, access) = newCompany()

        assertInvalid("unsupported_type") { logoService.prepareUpload(access(), "image/svg+xml", 100) }
        assertInvalid("empty") { logoService.prepareUpload(access(), "image/png", 0) }
        assertInvalid("too_large") { logoService.prepareUpload(access(), "image/png", LogoService.MAX_BYTES + 1L) }
        // A WebP is fine
        logoService.change(access(), upload(access(), WEBP, "image/webp").uploadId)
    }

    @Test
    fun `members can't change the logo`() {
        val (company, access) = newCompany()
        val member = newUser().also { memberRepository.save(CompanyMember(company.id, it, CompanyRole.MEMBER)) }
        val memberAccess = accessService.resolve(member, company.id)
        val upload = upload(access(), PNG)

        assertFailsWith<CompanyPermissionDeniedException> { logoService.prepareUpload(memberAccess, "image/png", 10) }
        assertFailsWith<CompanyPermissionDeniedException> { logoService.change(memberAccess, upload.uploadId) }
        assertFailsWith<CompanyPermissionDeniedException> { logoService.remove(memberAccess) }
    }

    /** Uploads the way the browser does: to the signed URL, with the headers it came with */
    private fun upload(
        access: CompanyAccess,
        bytes: ByteArray,
        contentType: String = if (bytes.contentEquals(JPEG)) "image/jpeg" else "image/png",
    ) = logoService.prepareUpload(access, contentType, bytes.size.toLong()).also {
        assertEquals(200, put(it, bytes).statusCode())
    }

    private fun put(
        upload: LogoUpload,
        bytes: ByteArray,
        headers: Map<String, String> = upload.target.headers,
    ): HttpResponse<ByteArray> {
        val request = HttpRequest.newBuilder(upload.target.url).PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
        headers.forEach { (name, value) -> request.header(name, value) }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray())
    }

    private fun get(url: URI) =
        http.send(HttpRequest.newBuilder(url).GET().build(), HttpResponse.BodyHandlers.ofByteArray())

    /** A new company, and a way to get its owner's access afresh (it carries the company as loaded) */
    private fun newCompany(): Pair<Company, () -> CompanyAccess> {
        val owner = newUser()
        val company = companyService.createCompany(owner, "Acme", "GR")
        return company to { accessService.resolve(owner, company.id) }
    }

    private fun pendingKey(
        company: Company,
        uploadId: String,
    ) = "pending/${company.getPublicId()}/$uploadId"

    private fun newUser() = userService.getOrCreateUser("user-${TSID.fast()}@example.com", Language.EN).id

    private fun assertInvalid(
        reason: String,
        action: () -> Unit,
    ) {
        val e = assertFailsWith<InvalidInputException> { action() }
        assertEquals("logo" to reason, e.field to e.reason)
    }

    companion object {
        val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 1, 2, 3)
        val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 16, 4, 5, 6, 7, 8, 9)
        val WEBP = "RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".toByteArray(Charsets.US_ASCII)
    }
}
