package com.invohub.backend.domain.contact

import com.invohub.backend.domain.common.BaseEntity
import jakarta.persistence.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant

enum class ContactKind {
    BUSINESS,

    /** An individual, e.g. a retail customer or a freelancer */
    PERSON,
}

/**
 * Someone a company sells to or buys from: one list for both, since what a contact is follows from the documents
 * it appears on. Archived rather than deleted, so documents can always point to it.
 */
@Entity
@Table(name = "contacts")
class Contact(
    companyId: Long,
    details: ContactDetails,
) : BaseEntity() {
    @Column(name = "company_id", nullable = false, updatable = false)
    val companyId: Long = companyId

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    var kind: ContactKind = details.kind
        protected set

    @Column(name = "name", nullable = false)
    var name: String = details.name
        protected set

    /** ISO 3166-1 alpha-2; its rules check the tax ID */
    @Column(name = "country", nullable = false)
    var country: String = details.country
        protected set

    /** Normalized by the country's rules */
    @Column(name = "tax_id")
    var taxId: String? = details.taxId
        protected set

    @Column(name = "email")
    var email: String? = details.email
        protected set

    @Column(name = "phone")
    var phone: String? = details.phone
        protected set

    @Column(name = "address_street")
    var street: String? = details.street
        protected set

    @Column(name = "address_city")
    var city: String? = details.city
        protected set

    @Column(name = "address_postal_code")
    var postalCode: String? = details.postalCode
        protected set

    @Column(name = "notes")
    var notes: String? = details.notes
        protected set

    @Column(name = "archived_at")
    var archivedAt: Instant? = null
        protected set

    val archived: Boolean get() = archivedAt != null

    val details: ContactDetails
        get() = ContactDetails(kind, name, country, taxId, email, phone, street, city, postalCode, notes)

    /** Replaces every detail; values come checked by ContactService */
    fun update(details: ContactDetails) {
        kind = details.kind
        name = details.name
        country = details.country
        taxId = details.taxId
        email = details.email
        phone = details.phone
        street = details.street
        city = details.city
        postalCode = details.postalCode
        notes = details.notes
    }

    fun archive(at: Instant) {
        archivedAt = at
    }

    fun restore() {
        archivedAt = null
    }
}

/** Everything about a contact that can be edited; blank optional values are null */
data class ContactDetails(
    val kind: ContactKind,
    val name: String,
    val country: String,
    val taxId: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val street: String? = null,
    val city: String? = null,
    val postalCode: String? = null,
    val notes: String? = null,
)

interface ContactRepository : JpaRepository<Contact, Long> {
    /**
     * A company's contacts, archived or not, by name. [pattern] is a lower-case LIKE pattern, in which `!`
     * escapes `%`, `_` and itself, matched against the name, tax ID and email; `%` for all.
     */
    @Query(
        """
        SELECT c FROM Contact c
        WHERE c.companyId = :companyId
          AND ((:archived = true AND c.archivedAt IS NOT NULL) OR (:archived = false AND c.archivedAt IS NULL))
          AND (lower(c.name) LIKE :pattern ESCAPE '!'
               OR lower(c.taxId) LIKE :pattern ESCAPE '!'
               OR lower(c.email) LIKE :pattern ESCAPE '!')
        ORDER BY lower(c.name), c.id
        """,
    )
    fun search(
        companyId: Long,
        archived: Boolean,
        pattern: String,
        pageable: Pageable,
    ): Page<Contact>
}

/** No such contact in this company */
class ContactNotFoundException : RuntimeException("Contact not found")
