package com.invohub.backend.domain.common

import io.hypersistence.tsid.TSID
import jakarta.persistence.*
import org.hibernate.Hibernate

/**
 * Base for entities identified by a TSID. Tables need an `id` column, plus those of [VersionedEntity].
 *
 * The ID is assigned at construction, so it never changes and equals/hashCode are stable before and
 * after persisting.
 */
@MappedSuperclass
abstract class BaseEntity : VersionedEntity() {
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    val id: Long = TSID.fast().toLong()

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
