package com.invohub.backend.domain.company

import com.invohub.backend.domain.audit.AuditEventRepository
import com.invohub.backend.domain.common.InvalidInputException
import com.invohub.backend.domain.common.NotAllowedException
import com.invohub.backend.domain.company.country.GreekRules
import com.invohub.backend.domain.user.Language
import com.invohub.backend.domain.user.UserService
import com.invohub.backend.support.AuditEvents
import com.invohub.backend.support.IntegrationTest
import io.hypersistence.tsid.TSID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@IntegrationTest
class CompanySettingsServiceTest {
    @Autowired
    private lateinit var settingsService: CompanySettingsService

    @Autowired
    private lateinit var accessService: CompanyAccessService

    @Autowired
    private lateinit var companyService: CompanyService

    @Autowired
    private lateinit var companyRepository: CompanyRepository

    @Autowired
    private lateinit var memberRepository: CompanyMemberRepository

    @Autowired
    private lateinit var userService: UserService

    @Autowired
    private lateinit var auditEventRepository: AuditEventRepository

    private val events by lazy { AuditEvents(auditEventRepository) }

    private val iban = "GR1601101250000000012300695"

    private inner class Setup {
        val owner = newUser()
        val company = companyService.createCompany(owner, "Acme", "GR")

        fun join(role: CompanyRole) = newUser().also { memberRepository.save(CompanyMember(company.id, it, role)) }

        fun access(userId: Long = owner) = accessService.resolve(userId, company.id)

        fun reload() = companyRepository.findById(company.id).get()
    }

    @Test
    fun `renames the company and records the change`() {
        val setup = Setup()

        settingsService.updateProfile(setup.access(), " Acme Ltd ", "GR")

        assertEquals("Acme Ltd", setup.reload().name)
        assertEquals(mapOf("name" to mapOf("from" to "Acme", "to" to "Acme Ltd")), lastDetails(setup.company))
    }

    @Test
    fun `moving to another country takes its currency and clears the tax ID and country details`() {
        val setup = Setup()
        settingsService.updateBusinessDetails(setup.access(), greekDetails())

        val moved = settingsService.updateProfile(setup.access(), "Acme", "gb")

        assertEquals("GB" to "GBP", moved.country to moved.currency)
        assertNull(moved.taxId)
        assertEquals(emptyMap(), moved.countryDetails)
        assertEquals("Acme ΙΚΕ", moved.legalName)
        val details = lastDetails(setup.company)
        assertEquals(mapOf("from" to "094014201", "to" to null), details["taxId"])
        assertEquals(mapOf("from" to "6201", "to" to null), details["countryDetails.mainActivity"])
    }

    @Test
    fun `saving the same profile records nothing`() {
        val setup = Setup()

        settingsService.updateProfile(setup.access(), "Acme", "GR")

        assertEquals(listOf("company.created"), events.forCompany(setup.company.id))
    }

    @Test
    fun `refuses a bad name or country`() {
        val setup = Setup()

        assertInvalid("name", "required") { settingsService.updateProfile(setup.access(), " ", "GR") }
        assertInvalid("country", "unknown") { settingsService.updateProfile(setup.access(), "Acme", "XX") }
    }

    @Test
    fun `saves the business details, cleaned up, and checks them by the country's rules`() {
        val setup = Setup()

        val saved =
            settingsService.updateBusinessDetails(
                setup.access(),
                greekDetails().copy(taxId = "EL 094 014 201", countryDetails = mapOf("mainActivity" to "62.01")),
            )

        assertEquals("094014201", saved.taxId)
        assertEquals(mapOf(GreekRules.MAIN_ACTIVITY to "6201"), saved.countryDetails)
        assertEquals("Ερμού 1", saved.addressStreet)
        assertInvalid("taxId", "check_digit") {
            settingsService.updateBusinessDetails(setup.access(), greekDetails().copy(taxId = "094014202"))
        }
        assertInvalid("taxId", "invalid") {
            settingsService.updateBusinessDetails(setup.access(), greekDetails().copy(taxId = "1234"))
        }
        assertInvalid("mainActivity", "invalid") {
            settingsService.updateBusinessDetails(
                setup.access(),
                greekDetails().copy(countryDetails = mapOf("mainActivity" to "6")),
            )
        }
        assertInvalid("postalCode", "too_long") {
            settingsService.updateBusinessDetails(setup.access(), greekDetails().copy(postalCode = "1".repeat(21)))
        }
    }

    @Test
    fun `blank clears a detail, and keys the country doesn't have are ignored`() {
        val setup = Setup()
        settingsService.updateBusinessDetails(setup.access(), greekDetails())

        val saved =
            settingsService.updateBusinessDetails(
                setup.access(),
                greekDetails().copy(street = " ", taxId = "", countryDetails = mapOf("vatOffice" to "x")),
            )

        assertNull(saved.addressStreet)
        assertNull(saved.taxId)
        assertEquals(emptyMap(), saved.countryDetails)
    }

    @Test
    fun `adds and removes bank accounts, recording each`() {
        val setup = Setup()

        val account = settingsService.addBankAccount(setup.access(), " Eurobank ", "gr16 0110 1250 0000 0001 2300 695")
        assertEquals("Eurobank" to iban, account.bankName to account.iban)
        assertEquals(listOf(account.id), settingsService.bankAccounts(setup.access()).map { it.id })
        assertEquals(
            mapOf("bankAccount" to mapOf("from" to null, "to" to "Eurobank $iban")),
            lastDetails(setup.company),
        )

        settingsService.removeBankAccount(setup.access(), account.id)
        assertEquals(emptyList(), settingsService.bankAccounts(setup.access()))
        assertEquals(
            mapOf("bankAccount" to mapOf("from" to "Eurobank $iban", "to" to null)),
            lastDetails(setup.company),
        )
    }

    @Test
    fun `refuses a bad or repeated IBAN, a missing bank name, and too many accounts`() {
        val setup = Setup()
        settingsService.addBankAccount(setup.access(), "Eurobank", iban)

        assertInvalid("iban", "invalid") {
            settingsService.addBankAccount(setup.access(), "Eurobank", "GR1601101250000000012300696")
        }
        assertInvalid("iban", "duplicate") { settingsService.addBankAccount(setup.access(), "Other", iban) }
        assertInvalid("bankName", "required") {
            settingsService.addBankAccount(setup.access(), " ", "DE89370400440532013000")
        }
        repeat(CompanySettingsService.MAX_BANK_ACCOUNTS - 1) {
            settingsService.addBankAccount(setup.access(), "Bank $it", validIban())
        }
        val e =
            assertFailsWith<NotAllowedException> {
                settingsService.addBankAccount(setup.access(), "One more", validIban())
            }
        assertEquals("too_many_bank_accounts", e.reason)
    }

    @Test
    fun `another company's bank account can't be removed`() {
        val setup = Setup()
        val other = Setup()
        val account = settingsService.addBankAccount(other.access(), "Eurobank", iban)

        assertFailsWith<BankAccountNotFoundException> { settingsService.removeBankAccount(setup.access(), account.id) }
        assertEquals(1, settingsService.bankAccounts(other.access()).size)
    }

    @Test
    fun `only the owner and admins change settings, members still see the bank accounts`() {
        val setup = Setup()
        val admin = setup.join(CompanyRole.ADMIN)
        val member = setup.join(CompanyRole.MEMBER)

        settingsService.addBankAccount(setup.access(admin), "Eurobank", iban)

        assertFailsWith<CompanyPermissionDeniedException> {
            settingsService.updateProfile(setup.access(member), "Mine", "GR")
        }
        assertFailsWith<CompanyPermissionDeniedException> {
            settingsService.updateBusinessDetails(setup.access(member), greekDetails())
        }
        assertEquals(1, settingsService.bankAccounts(setup.access(member)).size)
    }

    @Test
    fun `nothing changes while the company is read-only`() {
        val setup = Setup()
        companyRepository.save(setup.reload().apply { changeStatus(CompanyStatus.READ_ONLY) })

        assertFailsWith<CompanyReadOnlyException> { settingsService.updateProfile(setup.access(), "Acme 2", "GR") }
        assertFailsWith<CompanyReadOnlyException> { settingsService.addBankAccount(setup.access(), "Eurobank", iban) }
    }

    private fun greekDetails() =
        BusinessDetailsInput(
            legalName = "Acme ΙΚΕ",
            street = "Ερμού 1",
            city = "Αθήνα",
            postalCode = "10563",
            taxId = "094014201",
            countryDetails = mapOf(GreekRules.TAX_OFFICE to "Α' Αθηνών", GreekRules.MAIN_ACTIVITY to "6201"),
        )

    /** A random valid Greek IBAN: the account digits are random, the check digits computed */
    private fun validIban(): String {
        val bban = "0110125" + (1..20).map { (0..9).random() }.joinToString("")
        val numeric = (bban + "GR00").map { if (it.isDigit()) "$it" else "${it - 'A' + 10}" }.joinToString("")
        val check = 98 - numeric.toBigInteger().mod(97.toBigInteger()).toInt()
        return "GR%02d%s".format(check, bban)
    }

    private fun lastDetails(company: Company) =
        auditEventRepository.findByTargetTypeAndTargetIdOrderByOccurredAtAscIdAsc("company", company.id).last().details

    private fun newUser() = userService.getOrCreateUser("user-${TSID.fast()}@example.com", Language.EN).id

    private fun assertInvalid(
        field: String,
        reason: String,
        action: () -> Unit,
    ) {
        val e = assertFailsWith<InvalidInputException> { action() }
        assertEquals(field to reason, e.field to e.reason)
    }
}
