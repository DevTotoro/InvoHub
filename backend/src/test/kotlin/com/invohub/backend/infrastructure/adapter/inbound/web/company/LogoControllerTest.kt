package com.invohub.backend.infrastructure.adapter.inbound.web.company

import com.invohub.backend.domain.company.Company
import com.invohub.backend.domain.company.CompanyMember
import com.invohub.backend.domain.company.CompanyMemberRepository
import com.invohub.backend.domain.company.CompanyRole
import com.invohub.backend.domain.company.CompanyService
import com.invohub.backend.domain.company.LogoService
import com.invohub.backend.domain.company.LogoServiceTest
import com.invohub.backend.domain.user.UserRepository
import com.invohub.backend.support.CapturingEmailPort
import com.invohub.backend.support.DatabaseCleaner
import com.invohub.backend.support.IntegrationTest
import com.invohub.backend.support.signIn
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpMethod
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart

@IntegrationTest
@AutoConfigureMockMvc
class LogoControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var emailPort: CapturingEmailPort

    @Autowired
    private lateinit var databaseCleaner: DatabaseCleaner

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    @Autowired
    private lateinit var companyService: CompanyService

    @Autowired
    private lateinit var memberRepository: CompanyMemberRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    private lateinit var owner: Cookie
    private lateinit var acme: Company
    private val url get() = "/api/companies/${acme.getPublicId()}/logo"

    @BeforeEach
    fun reset() {
        emailPort.clear()
        databaseCleaner.clean()
        redisTemplate.connectionFactory!!.connection.use { it.serverCommands().flushAll() }

        owner = mockMvc.signIn(emailPort, "owner@example.com")
        acme = companyService.createCompany(userRepository.findByEmail("owner@example.com")!!.id, "Acme", "GR")
    }

    @Test
    fun `the owner uploads a logo, the company sees it, and the owner removes it`() {
        upload(owner, LogoServiceTest.PNG).andExpect { status { isNoContent() } }

        mockMvc.get(url) { cookie(owner) }.andExpect {
            status { isOk() }
            content { contentType("image/png") }
            content { bytes(LogoServiceTest.PNG) }
            header { string("Cache-Control", "max-age=31536000, private") }
        }

        mockMvc
            .delete(url) {
                with(csrf())
                cookie(owner)
            }.andExpect { status { isNoContent() } }
        mockMvc.get(url) { cookie(owner) }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `members see the logo but can't change it, outsiders don't know the company`() {
        upload(owner, LogoServiceTest.PNG)
        val member = mockMvc.signIn(emailPort, "member@example.com")
        memberRepository.save(
            CompanyMember(acme.id, userRepository.findByEmail("member@example.com")!!.id, CompanyRole.MEMBER),
        )
        val outsider = mockMvc.signIn(emailPort, "outsider@example.com")

        mockMvc.get(url) { cookie(member) }.andExpect { status { isOk() } }
        upload(member, LogoServiceTest.JPEG).andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("forbidden") }
        }
        mockMvc.get(url) { cookie(outsider) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("not_found") }
        }
        upload(outsider, LogoServiceTest.JPEG).andExpect { status { isNotFound() } }
    }

    @Test
    fun `a file that isn't an image, or is too large, is refused with the reason`() {
        upload(owner, "<svg/>".toByteArray()).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("invalid_input") }
            jsonPath("$.field") { value("logo") }
            jsonPath("$.reason") { value("unsupported_type") }
        }
        upload(owner, LogoServiceTest.PNG + ByteArray(LogoService.MAX_BYTES)).andExpect {
            jsonPath("$.reason") { value("too_large") }
        }
    }

    @Test
    fun `uploading needs a session and the CSRF token`() {
        mockMvc
            .multipart(HttpMethod.PUT, url) {
                file("file", LogoServiceTest.PNG)
                cookie(owner)
            }.andExpect { status { isForbidden() } }
        mockMvc
            .multipart(HttpMethod.PUT, url) {
                file("file", LogoServiceTest.PNG)
                with(csrf())
            }.andExpect { status { isUnauthorized() } }
    }

    private fun upload(
        session: Cookie,
        bytes: ByteArray,
    ) = mockMvc.multipart(HttpMethod.PUT, url) {
        file("file", bytes)
        with(csrf())
        cookie(session)
    }
}
