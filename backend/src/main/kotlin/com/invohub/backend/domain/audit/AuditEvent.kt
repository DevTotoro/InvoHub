package com.invohub.backend.domain.audit

data class AuditEvent(
    val action: AuditAction,
    /** Who did it. Defaults to the signed-in user; with nobody signed in, it was InvoHub itself. */
    val actorUserId: Long? = null,
    /** The company it happened in, if any; sign-ins, for example, belong to no company */
    val companyId: Long? = null,
    /** What it happened to */
    val target: AuditTarget? = null,
    /** Small facts about the event, e.g. before and after values. Serialized to JSON. */
    val details: Map<String, Any?> = emptyMap(),
)

data class AuditTarget(
    val type: String,
    val id: Long,
) {
    companion object {
        fun user(id: Long) = AuditTarget("user", id)

        fun company(id: Long) = AuditTarget("company", id)
    }
}

enum class AuditAction(
    /** Stored in the log; never rename one, or older events stop matching */
    val code: String,
) {
    USER_CREATED("user.created"),
    USER_SIGNED_IN("user.signed_in"),
    USER_SIGNED_OUT("user.signed_out"),
    USER_SIGN_IN_REFUSED("user.sign_in_refused"),
    USER_SUSPENDED("user.suspended"),
    USER_UNSUSPENDED("user.unsuspended"),
    COMPANY_CREATED("company.created"),
}
