package com.invohub.backend.domain.contact

import com.invohub.backend.domain.audit.AuditEventRepository
import com.invohub.backend.domain.common.InvalidInputException
import com.invohub.backend.domain.company.CompanyAccessService
import com.invohub.backend.domain.company.CompanyClosedException
import com.invohub.backend.domain.company.CompanyMember
import com.invohub.backend.domain.company.CompanyMemberRepository
import com.invohub.backend.domain.company.CompanyPermissionDeniedException
import com.invohub.backend.domain.company.CompanyRole
import com.invohub.backend.domain.company.CompanyService
import com.invohub.backend.domain.company.OwnershipService
import com.invohub.backend.domain.user.Language
import com.invohub.backend.domain.user.UserService
import com.invohub.backend.support.IntegrationTest
import io.hypersistence.tsid.TSID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@IntegrationTest
class ContactServiceTest {
    @Autowired
    private lateinit var contactService: ContactService

    @Autowired
    private lateinit var accessService: CompanyAccessService

    @Autowired
    private lateinit var companyService: CompanyService

    @Autowired
    private lateinit var memberRepository: CompanyMemberRepository

    @Autowired
    private lateinit var ownershipService: OwnershipService

    @Autowired
    private lateinit var userService: UserService

    @Autowired
    private lateinit var auditEventRepository: AuditEventRepository

    private inner class Setup {
        val owner = newUser()
        val company = companyService.createCompany(owner, "Acme", "GR")

        fun join(role: CompanyRole) = newUser().also { memberRepository.save(CompanyMember(company.id, it, role)) }

        fun access(userId: Long = owner) = accessService.resolve(userId, company.id)
    }

    private val papadopoulos =
        ContactInput(
            kind = ContactKind.BUSINESS,
            name = "  Papadopoulos ΑΕ ",
            country = null,
            taxId = "EL 094 014 201",
            email = " info@papadopoulos.gr ",
            phone = "+30 210 123 4567",
            street = "Ερμού 1",
            city = "Αθήνα",
            postalCode = "10563",
        )

    @Test
    fun `adds a contact, cleaned up, in the company's country unless told otherwise`() {
        val setup = Setup()

        val contact = contactService.create(setup.access(), papadopoulos)

        assertEquals("Papadopoulos ΑΕ", contact.name)
        assertEquals("GR" to "094014201", contact.country to contact.taxId)
        assertEquals("info@papadopoulos.gr", contact.email)
        assertEquals(
            listOf("contact.created"),
            auditEventRepository
                .findByTargetTypeAndTargetIdOrderByOccurredAtAscIdAsc(
                    "contact",
                    contact.id,
                ).map { it.action },
        )

        val german =
            contactService.create(
                setup.access(),
                ContactInput(ContactKind.BUSINESS, "Globex GmbH", "de", taxId = "DE 123456789"),
            )
        assertEquals("DE" to "DE123456789", german.country to german.taxId)
    }

    @Test
    fun `an individual needs only a name`() {
        val setup = Setup()

        val maria = contactService.create(setup.access(), ContactInput(ContactKind.PERSON, "Maria", country = null))

        assertNull(maria.taxId)
        assertEquals(ContactKind.PERSON, maria.kind)
    }

    @Test
    fun `refuses what can't be right, naming the field`() {
        val setup = Setup()

        assertInvalid("name", "required") { contactService.create(setup.access(), papadopoulos.copy(name = " ")) }
        assertInvalid("country", "unknown") { contactService.create(setup.access(), papadopoulos.copy(country = "XX")) }
        assertInvalid(
            "taxId",
            "check_digit",
        ) { contactService.create(setup.access(), papadopoulos.copy(taxId = "094014202")) }
        assertInvalid(
            "email",
            "invalid",
        ) { contactService.create(setup.access(), papadopoulos.copy(email = "not an email")) }
        assertInvalid(
            "phone",
            "invalid",
        ) { contactService.create(setup.access(), papadopoulos.copy(phone = "call me")) }
        assertInvalid("notes", "too_long") {
            contactService.create(setup.access(), papadopoulos.copy(notes = "x".repeat(2001)))
        }
    }

    @Test
    fun `finds contacts by name, tax ID or email, ignoring case, by name`() {
        val setup = Setup()
        contactService.create(setup.access(), papadopoulos)
        contactService.create(
            setup.access(),
            ContactInput(ContactKind.PERSON, "alexiou", null, email = "nikos@example.com"),
        )
        contactService.create(setup.access(), ContactInput(ContactKind.BUSINESS, "100% Bio", null))

        fun names(query: String?) =
            contactService.search(setup.access(), query, archived = false, offset = 0, limit = 50).contacts.map {
                it.name
            }

        assertEquals(listOf("100% Bio", "alexiou", "Papadopoulos ΑΕ"), names(null))
        assertEquals(listOf("Papadopoulos ΑΕ"), names("PAPAD"))
        assertEquals(listOf("Papadopoulos ΑΕ"), names("094014"))
        assertEquals(listOf("alexiou"), names("nikos@"))
        // Typed % and _ are just characters
        assertEquals(listOf("100% Bio"), names("0%"))
        assertEquals(emptyList(), names("_"))
    }

    @Test
    fun `pages through the contacts, saying how many there are`() {
        val setup = Setup()
        repeat(5) { contactService.create(setup.access(), ContactInput(ContactKind.PERSON, "Person $it", null)) }

        val second = contactService.search(setup.access(), null, archived = false, offset = 2, limit = 2)

        assertEquals(listOf("Person 2", "Person 3"), second.contacts.map { it.name })
        assertEquals(5, second.total)
    }

    @Test
    fun `records only what changed`() {
        val setup = Setup()
        val contact = contactService.create(setup.access(), papadopoulos)

        contactService.update(setup.access(), contact.id, papadopoulos)
        contactService.update(setup.access(), contact.id, papadopoulos.copy(city = "Πάτρα", phone = ""))

        val events = auditEventRepository.findByTargetTypeAndTargetIdOrderByOccurredAtAscIdAsc("contact", contact.id)
        assertEquals(listOf("contact.created", "contact.updated"), events.map { it.action })
        assertEquals(
            mapOf(
                "city" to mapOf("from" to "Αθήνα", "to" to "Πάτρα"),
                "phone" to mapOf("from" to "+30 210 123 4567", "to" to null),
            ),
            events.last().details,
        )
    }

    @Test
    fun `archived contacts are listed apart, and can be restored`() {
        val setup = Setup()
        val contact = contactService.create(setup.access(), papadopoulos)

        assertNotNull(contactService.archive(setup.access(), contact.id).archivedAt)
        assertEquals(0, contactService.search(setup.access(), null, archived = false, offset = 0, limit = 50).total)
        assertEquals(1, contactService.search(setup.access(), null, archived = true, offset = 0, limit = 50).total)

        assertNull(contactService.restore(setup.access(), contact.id).archivedAt)
        assertEquals(1, contactService.search(setup.access(), null, archived = false, offset = 0, limit = 50).total)
    }

    @Test
    fun `another company's contact is not found`() {
        val setup = Setup()
        val other = Setup()
        val theirs = contactService.create(other.access(), papadopoulos)

        assertFailsWith<ContactNotFoundException> { contactService.get(setup.access(), theirs.id) }
        assertFailsWith<ContactNotFoundException> { contactService.update(setup.access(), theirs.id, papadopoulos) }
        assertEquals(0, contactService.search(setup.access(), null, archived = false, offset = 0, limit = 50).total)
    }

    @Test
    fun `members edit contacts, viewers only see them, and a closed company changes nothing`() {
        val setup = Setup()
        val member = setup.join(CompanyRole.MEMBER)
        val viewer = setup.join(CompanyRole.VIEWER)

        val contact = contactService.create(setup.access(member), papadopoulos)
        assertEquals(contact.id, contactService.get(setup.access(viewer), contact.id).id)
        assertFailsWith<CompanyPermissionDeniedException> { contactService.create(setup.access(viewer), papadopoulos) }

        ownershipService.close(setup.access())
        assertFailsWith<CompanyClosedException> { contactService.update(setup.access(), contact.id, papadopoulos) }
        assertTrue(
            contactService.search(setup.access(viewer), null, archived = false, offset = 0, limit = 50).total == 1L,
        )
    }

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
