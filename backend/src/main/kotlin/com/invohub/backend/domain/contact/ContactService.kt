package com.invohub.backend.domain.contact

import com.invohub.backend.domain.audit.AuditAction
import com.invohub.backend.domain.audit.AuditEvent
import com.invohub.backend.domain.audit.AuditLog
import com.invohub.backend.domain.audit.AuditTarget
import com.invohub.backend.domain.common.InvalidInputException
import com.invohub.backend.domain.common.optionalText
import com.invohub.backend.domain.common.requiredText
import com.invohub.backend.domain.company.CompanyAccess
import com.invohub.backend.domain.company.CompanyPermission
import com.invohub.backend.domain.company.country.Countries
import com.invohub.backend.domain.company.country.CountryRulesRegistry
import com.invohub.backend.domain.company.country.checkedTaxId
import com.invohub.backend.domain.user.isEmailAddress
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** A contact as typed; blank means none */
data class ContactInput(
    val kind: ContactKind,
    val name: String?,
    /** ISO 3166-1 alpha-2 */
    val country: String?,
    val taxId: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val street: String? = null,
    val city: String? = null,
    val postalCode: String? = null,
    val notes: String? = null,
)

/** One page of a search */
data class ContactPage(
    val contacts: List<Contact>,
    /** All that match, on every page */
    val total: Long,
)

/**
 * A company's contacts. Everyone in the company sees them; those who can edit (owner, admins, members) add and
 * change them. Every change is recorded, the changed fields before and after.
 */
@Service
class ContactService(
    private val contactRepository: ContactRepository,
    private val countryRules: CountryRulesRegistry,
    private val auditLog: AuditLog,
) {
    companion object {
        const val MAX_PAGE_SIZE = 100
        private const val MAX_NAME = 200
        private const val MAX_EMAIL = 255
        private const val MAX_PHONE = 50
        private const val MAX_STREET = 255
        private const val MAX_CITY = 100
        private const val MAX_POSTAL_CODE = 20
        private const val MAX_NOTES = 2000

        // Digits, spaces and the usual separators, optionally starting with +
        private val PHONE = Regex("^\\+?[0-9 ()./-]+$")
    }

    /** By name. [query] matches anywhere in the name, tax ID or email, ignoring case. */
    @Transactional(readOnly = true)
    fun search(
        access: CompanyAccess,
        query: String?,
        archived: Boolean,
        offset: Int,
        limit: Int,
    ): ContactPage {
        access.require(CompanyPermission.VIEW)
        val size = limit.coerceIn(1, MAX_PAGE_SIZE)
        val pattern =
            query
                ?.trim()
                ?.lowercase()
                ?.ifEmpty { null }
                ?.let { "%${escapeLike(it)}%" } ?: "%"
        // Pages by offset, so the page size divides it; a client asks for multiples of its page size
        val page =
            contactRepository.search(
                access.companyId,
                archived,
                pattern,
                PageRequest.of(
                    offset.coerceAtLeast(0) / size,
                    size,
                ),
            )
        return ContactPage(page.content, page.totalElements)
    }

    /** @throws ContactNotFoundException unless it is one of this company's contacts */
    @Transactional(readOnly = true)
    fun get(
        access: CompanyAccess,
        contactId: Long,
    ): Contact {
        access.require(CompanyPermission.VIEW)
        return find(access, contactId)
    }

    /** @throws InvalidInputException with the field and the reason */
    @Transactional
    fun create(
        access: CompanyAccess,
        input: ContactInput,
    ): Contact {
        access.require(CompanyPermission.EDIT)
        val contact = contactRepository.saveAndFlush(Contact(access.companyId, checked(input, access.company.country)))
        record(access, contact, AuditAction.CONTACT_CREATED, mapOf("name" to contact.name))
        return contact
    }

    /**
     * Replaces every detail; records only what changed.
     * @throws InvalidInputException with the field and the reason
     * @throws ContactNotFoundException unless it is one of this company's contacts
     */
    @Transactional
    fun update(
        access: CompanyAccess,
        contactId: Long,
        input: ContactInput,
    ): Contact {
        access.require(CompanyPermission.EDIT)
        val contact = find(access, contactId)
        val before = contact.details
        val after = checked(input, access.company.country)
        if (before == after) return contact

        contact.update(after)
        val saved = contactRepository.saveAndFlush(contact)
        val from = fields(before)
        val to = fields(after)
        val changes =
            from.keys.filter { from[it] != to[it] }.associateWith {
                mapOf(
                    "from" to from[it],
                    "to" to to[it],
                )
            }
        record(access, saved, AuditAction.CONTACT_UPDATED, changes)
        return saved
    }

    /** Hidden from the list and from pickers, kept for the documents that name it. Archiving twice does nothing. */
    @Transactional
    fun archive(
        access: CompanyAccess,
        contactId: Long,
    ): Contact {
        access.require(CompanyPermission.EDIT)
        val contact = find(access, contactId)
        if (contact.archived) return contact
        contact.archive(Instant.now())
        val saved = contactRepository.saveAndFlush(contact)
        record(access, saved, AuditAction.CONTACT_ARCHIVED)
        return saved
    }

    @Transactional
    fun restore(
        access: CompanyAccess,
        contactId: Long,
    ): Contact {
        access.require(CompanyPermission.EDIT)
        val contact = find(access, contactId)
        if (!contact.archived) return contact
        contact.restore()
        val saved = contactRepository.saveAndFlush(contact)
        record(access, saved, AuditAction.CONTACT_RESTORED)
        return saved
    }

    /** Cleaned up and checked; the country defaults to the company's */
    private fun checked(
        input: ContactInput,
        companyCountry: String,
    ): ContactDetails {
        val name = requiredText(input.name, "name", MAX_NAME)
        val country =
            input.country
                ?.trim()
                ?.uppercase()
                ?.ifEmpty { null } ?: companyCountry
        if (Countries.currencyOf(country) == null) throw InvalidInputException("country", "unknown")
        val email = optionalText(input.email, "email", MAX_EMAIL)
        if (email != null && !isEmailAddress(email)) throw InvalidInputException("email", "invalid")
        val phone = optionalText(input.phone, "phone", MAX_PHONE)
        if (phone != null && !PHONE.matches(phone)) throw InvalidInputException("phone", "invalid")
        return ContactDetails(
            kind = input.kind,
            name = name,
            country = country,
            taxId = countryRules.forCountry(country).checkedTaxId(input.taxId),
            email = email,
            phone = phone,
            street = optionalText(input.street, "street", MAX_STREET),
            city = optionalText(input.city, "city", MAX_CITY),
            postalCode = optionalText(input.postalCode, "postalCode", MAX_POSTAL_CODE),
            notes = optionalText(input.notes, "notes", MAX_NOTES),
        )
    }

    private fun fields(details: ContactDetails): Map<String, Any?> =
        mapOf(
            "kind" to details.kind.name,
            "name" to details.name,
            "country" to details.country,
            "taxId" to details.taxId,
            "email" to details.email,
            "phone" to details.phone,
            "street" to details.street,
            "city" to details.city,
            "postalCode" to details.postalCode,
            "notes" to details.notes,
        )

    private fun find(
        access: CompanyAccess,
        contactId: Long,
    ): Contact =
        contactRepository.findById(contactId).orElse(null)?.takeIf { it.companyId == access.companyId }
            ?: throw ContactNotFoundException()

    private fun record(
        access: CompanyAccess,
        contact: Contact,
        action: AuditAction,
        details: Map<String, Any?> = emptyMap(),
    ) {
        auditLog.record(
            AuditEvent(
                action,
                companyId = access.companyId,
                target = AuditTarget.contact(contact.id),
                details = details,
            ),
        )
    }

    /** So that typed `%`, `_` and `!` match themselves */
    private fun escapeLike(text: String) = text.replace("!", "!!").replace("%", "!%").replace("_", "!_")
}
