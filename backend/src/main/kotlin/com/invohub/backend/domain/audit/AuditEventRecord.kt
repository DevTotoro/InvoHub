package com.invohub.backend.domain.audit

import io.hypersistence.tsid.TSID
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.Generated
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * A stored audit event (audit.events). Never changed after insert: no column is updatable, the repository has
 * no save or delete, the app's database login may only insert and read the table, and a trigger refuses
 * changes from every login. (Not Hibernate's @Immutable: it can't be combined with the database-generated
 * timestamp.)
 *
 * Not a BaseEntity: an event has no version and is never updated. Record events through [AuditLog].
 */
@Entity
@Table(
    name = "events",
    schema = "audit",
    // Also partial indexes on (company_id, occurred_at) and (actor_user_id, occurred_at) WHERE … IS NOT NULL,
    // which @Index can't express: see V1__initial_setup.sql
    indexes = [Index(name = "ix_audit_events_target", columnList = "target_type, target_id, occurred_at")],
)
class AuditEventRecord(
    @Column(name = "action", nullable = false, updatable = false)
    val action: String,
    @Column(name = "actor_user_id", updatable = false)
    val actorUserId: Long?,
    @Column(name = "company_id", updatable = false)
    val companyId: Long?,
    @Column(name = "target_type", updatable = false)
    val targetType: String?,
    @Column(name = "target_id", updatable = false)
    val targetId: Long?,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", nullable = false, updatable = false)
    val details: Map<String, Any?>,
    @Column(name = "request_id", updatable = false)
    val requestId: String?,
) {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: Long = TSID.fast().toLong()

    /** Set by the database when the event is inserted */
    @Generated
    @Column(name = "occurred_at", nullable = false, insertable = false, updatable = false)
    lateinit var occurredAt: Instant
        protected set
}
