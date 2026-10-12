package com.invohub.backend.domain.invitation

/**
 * Daily counts of invitations, per inviter and per recipient address (see InvitationProperties for the
 * limits). Counted when inviting, before the email goes out.
 */
interface InvitationLimits {
    /**
     * Counts [count] invitations from [inviterUserId] and one email to [email], unless that would go over a
     * limit, in which case nothing is counted.
     * @throws InvitationRefusedException "daily_limit" for the inviter, "recipient_daily_limit" for the address
     */
    fun reserve(
        inviterUserId: Long,
        email: String,
        count: Int,
    )

    /** Undoes a [reserve] whose invitations weren't sent after all */
    fun release(
        inviterUserId: Long,
        email: String,
        count: Int,
    )
}
