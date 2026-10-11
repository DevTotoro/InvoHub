package com.invohub.backend.domain.audit

/** The append-only record of what happened, who did it and when. */
interface AuditLog {
    /**
     * Records the event as part of the current transaction, so it commits or rolls back together with the
     * change it describes: no change without its record. Fails if there is no transaction.
     */
    fun record(event: AuditEvent)

    /** Records an event that isn't part of any other change (e.g. a sign-in), in a transaction of its own. */
    fun recordStandalone(event: AuditEvent)
}
