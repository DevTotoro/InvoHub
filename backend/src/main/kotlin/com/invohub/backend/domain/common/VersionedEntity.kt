package com.invohub.backend.domain.common

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

/**
 * Version and timestamps, for every entity. Tables need `version`, `created_at` and `updated_at` columns.
 * Entities with a TSID extend [BaseEntity]; entities identified by a natural key (e.g. a membership, by
 * company and user) extend this directly.
 *
 * Spring Data detects new entities by the null [version], whatever their key, so save() inserts directly
 * instead of merging, and updates get optimistic locking.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class VersionedEntity {
    @Version
    @Column(name = "version", nullable = false)
    var version: Long? = null
        protected set

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: Instant
        protected set

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    lateinit var updatedAt: Instant
        protected set
}
