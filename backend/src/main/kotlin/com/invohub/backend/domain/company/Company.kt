package com.invohub.backend.domain.company

import com.invohub.backend.domain.common.BaseEntity
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * A business using InvoHub. Created with just a name and a country; the details needed to issue invoices are
 * filled in later, through the invoicing setup checklist of the country's rules (see CountryRules).
 */
@Entity
@Table(
    name = "companies",
    indexes = [Index(name = "ix_companies_owner", columnList = "owner_user_id")],
)
class Company(
    name: String,
    /** ISO 3166-1 alpha-2, e.g. GR */
    country: String,
    /** ISO 4217, e.g. EUR. Follows from the country. */
    currency: String,
    ownerUserId: Long,
) : BaseEntity() {
    @Column(name = "name", nullable = false)
    var name: String = name
        protected set

    @Column(name = "country", nullable = false)
    var country: String = country
        protected set

    @Column(name = "currency", nullable = false)
    var currency: String = currency
        protected set

    /** Set by InvoHub (billing, staff); see CompanyPermission for what each allows */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: CompanyStatus = CompanyStatus.ACTIVE
        protected set

    /** Exactly one owner, with full rights; not listed in company_members */
    @Column(name = "owner_user_id", nullable = false)
    var ownerUserId: Long = ownerUserId
        protected set

    /** An InvoHub decision (billing, staff), never the company's own */
    fun changeStatus(status: CompanyStatus) {
        this.status = status
    }

    // Invoicing details: empty until the company works through its invoicing setup checklist

    /** Normalized by the country's rules. Not unique: nobody can block a business by claiming its tax ID. */
    @Column(name = "tax_id")
    var taxId: String? = null
        protected set

    @Column(name = "tax_id_verified_at")
    var taxIdVerifiedAt: Instant? = null
        protected set

    @Column(name = "legal_name")
    var legalName: String? = null
        protected set

    @Column(name = "address_street")
    var addressStreet: String? = null
        protected set

    @Column(name = "address_city")
    var addressCity: String? = null
        protected set

    @Column(name = "address_postal_code")
    var addressPostalCode: String? = null
        protected set

    /** Details only some countries need (Greece: tax office, main activity), checked by that country's rules */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "country_details", nullable = false)
    var countryDetails: Map<String, Any?> = emptyMap()
        protected set
}
