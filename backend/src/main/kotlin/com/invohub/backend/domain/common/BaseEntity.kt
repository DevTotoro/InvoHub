package com.invohub.backend.domain.common

import io.hypersistence.tsid.TSID
import jakarta.persistence.*
import org.hibernate.Hibernate
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

/**
 * Base for all entities. Tables need `id`, `version`, `created_at` and `updated_at` columns.
 *
 * The ID is assigned at construction, so it never changes and equals/hashCode are stable before and
 * after persisting. Spring Data detects new entities by the null [version] (the ID is never null),
 * so save() inserts directly instead of merging, and updates get optimistic locking.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseEntity {
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    val id: Long = TSID.fast().toLong()

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

    fun getPublicId(): String = TSIDCodec.encode(id)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BaseEntity) return false
        // Compare real classes: a lazy proxy is a generated subclass, but equal to the entity it stands for
        if (Hibernate.getClassLazy(this) != Hibernate.getClassLazy(other)) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
