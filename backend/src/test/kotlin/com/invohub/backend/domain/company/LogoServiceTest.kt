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
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Against a real S3-compatible store (Silo, in a container) */
@IntegrationTest
class LogoServiceTest {
    @Autowired
    private lateinit var logoService: LogoService

    @Autowired
    private lateinit var accessService: CompanyAccessService

    @Autowired
    private lateinit var companyService: CompanyService

    @Autowired
    private lateinit var companyRepository: CompanyRepository

    @Autowired
    private lateinit var memberRepository: CompanyMemberRepository

    @Autowired
    private lateinit var storage: FileStorage

    @Autowired
    private lateinit var userService: UserService

    @Autowired
    private lateinit var auditEventRepository: AuditEventRepository

    @Test
    fun `stores a logo, replaces it, and removes it, deleting the old files`() {
        val (company, access) = newCompany()

        logoService.change(access(), PNG)
        val first = assertNotNull(keyOf(company))
        val stored = assertNotNull(logoService.logo(access()))
        assertEquals("image/png", stored.contentType)
        assertContentEquals(PNG, stored.bytes)

        logoService.change(access(), JPEG)
        val second = assertNotNull(keyOf(company))
        assertNotEquals(first, second)
        assertEquals("image/jpeg", logoService.logo(access())?.contentType)
        assertNull(storage.get(first))

        logoService.remove(access())
        assertNull(keyOf(company))
        assertNull(logoService.logo(access()))
        assertNull(storage.get(second))
        assertEquals(
            listOf("added", "replaced", "removed"),
            auditEventRepository
                .findByTargetTypeAndTargetIdOrderByOccurredAtAscIdAsc("company", company.id)
                .filter { it.action == "company.updated" }
                .map { it.details["logo"] },
        )
    }

    @Test
    fun `knows images by their contents, and refuses anything else`() {
        val (_, access) = newCompany()

        logoService.change(access(), WEBP)
        assertEquals("image/webp", logoService.logo(access())?.contentType)

        assertInvalid("unsupported_type") { logoService.change(access(), "<svg xmlns=\"…\"/>".toByteArray()) }
        assertInvalid("empty") { logoService.change(access(), ByteArray(0)) }
        assertInvalid("too_large") { logoService.change(access(), PNG + ByteArray(LogoService.MAX_BYTES)) }
    }

    @Test
    fun `members see the logo but can't change it`() {
        val (company, access) = newCompany()
        logoService.change(access(), PNG)
        val member = newUser().also { memberRepository.save(CompanyMember(company.id, it, CompanyRole.MEMBER)) }
        val memberAccess = accessService.resolve(member, company.id)

        assertNotNull(logoService.logo(memberAccess))
        assertFailsWith<CompanyPermissionDeniedException> { logoService.change(memberAccess, JPEG) }
        assertFailsWith<CompanyPermissionDeniedException> { logoService.remove(memberAccess) }
    }

    /** A new company, and a way to get its owner's access afresh (it carries the company as loaded) */
    private fun newCompany(): Pair<Company, () -> CompanyAccess> {
        val owner = newUser()
        val company = companyService.createCompany(owner, "Acme", "GR")
        return company to { accessService.resolve(owner, company.id) }
    }

    private fun keyOf(company: Company) = companyRepository.findById(company.id).get().logoKey

    private fun newUser() = userService.getOrCreateUser("user-${TSID.fast()}@example.com", Language.EN).id

    private fun assertInvalid(
        reason: String,
        action: () -> Unit,
    ) {
        val e = assertFailsWith<InvalidInputException> { action() }
        assertEquals("logo" to reason, e.field to e.reason)
    }

    companion object {
        val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)
        val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 4, 5, 6)
        val WEBP = "RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".toByteArray(Charsets.US_ASCII)
    }
}
