package com.invohub.backend.domain.common

import io.hypersistence.tsid.TSID
import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.OffsetDateTime

@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseEntity {
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    var id: Long = 0L
        protected set

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    lateinit var createdAt: OffsetDateTime
        protected set

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    lateinit var updatedAt: OffsetDateTime
        protected set

    @PrePersist
    fun assignTSID() {
        if (id == 0L) {
            id = TSID.fast().toLong()
        }
    }

    fun getPublicId(): String = TSIDCodec.encode(id)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as BaseEntity
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
