package com.invohub.backend.domain.company

import com.invohub.backend.domain.common.BaseEntity
import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository

/** A bank account a company is paid into, shown on its invoices */
@Entity
@Table(
    name = "company_bank_accounts",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_company_bank_accounts_iban",
            columnNames = ["company_id", "iban"],
        ),
    ],
)
class BankAccount(
    companyId: Long,
    bankName: String,
    iban: String,
) : BaseEntity() {
    @Column(name = "company_id", nullable = false, updatable = false)
    val companyId: Long = companyId

    @Column(name = "bank_name", nullable = false, updatable = false)
    val bankName: String = bankName

    /** Normalized: no spaces, upper case */
    @Column(name = "iban", nullable = false, updatable = false)
    val iban: String = iban
}

interface BankAccountRepository : JpaRepository<BankAccount, Long> {
    fun findByCompanyIdOrderByCreatedAt(companyId: Long): List<BankAccount>

    fun countByCompanyId(companyId: Long): Long

    fun existsByCompanyIdAndIban(
        companyId: Long,
        iban: String,
    ): Boolean
}
