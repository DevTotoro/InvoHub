package com.invohub.backend.domain.company

/** Whether a company can be worked in. Changed by InvoHub (billing, staff), never by the company itself. */
enum class CompanyStatus {
    ACTIVE,

    /** Unpaid past the grace period: its people can still look and export, and pay to make it active again */
    READ_ONLY,

    /** Fraud or abuse, decided by InvoHub staff: nobody can use it */
    SUSPENDED,
}
