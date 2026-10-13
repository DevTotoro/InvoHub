package com.invohub.backend.domain.audit

data class AuditEvent(
    val action: AuditAction,
    /** Who did it. Defaults to the signed-in user; with nobody signed in, it was InvoHub itself. */
    val actorUserId: Long? = null,
    /** The company it happened in, if any; sign-ins, for example, belong to no company */
    val companyId: Long? = null,
    /** What it happened to */
    val target: AuditTarget? = null,
    /**
     * Small facts about the event, e.g. before and after values. Serialized to JSON. Never IDs: the columns
     * above hold those, internal in Postgres and turned into public IDs on export, which can't reach inside
     * the JSON. Events of one request share its request ID, which links them.
     */
    val details: Map<String, Any?> = emptyMap(),
)

data class AuditTarget(
    val type: String,
    val id: Long,
) {
    companion object {
        fun user(id: Long) = AuditTarget("user", id)

        fun company(id: Long) = AuditTarget("company", id)

        fun invitation(id: Long) = AuditTarget("invitation", id)

        fun ownershipTransfer(id: Long) = AuditTarget("ownership_transfer", id)
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
    COMPANY_UPDATED("company.updated"),
    COMPANY_CLOSED("company.closed"),
    COMPANY_REOPENED("company.reopened"),
    COMPANY_OWNERSHIP_TRANSFERRED("company.ownership_transferred"),
    OWNERSHIP_OFFERED("ownership.offered"),
    OWNERSHIP_OFFER_CANCELLED("ownership.cancelled"),
    OWNERSHIP_OFFER_DECLINED("ownership.declined"),
    MEMBER_ADDED("member.added"),
    MEMBER_ROLE_CHANGED("member.role_changed"),
    MEMBER_REMOVED("member.removed"),
    MEMBER_LEFT("member.left"),
    INVITATION_SENT("invitation.sent"),
    INVITATION_REVOKED("invitation.revoked"),
    INVITATION_ACCEPTED("invitation.accepted"),
    INVITATION_DECLINED("invitation.declined"),
}
