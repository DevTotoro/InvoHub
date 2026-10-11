package com.invohub.backend.domain.common

import io.hypersistence.tsid.TSID

object TSIDCodec {
    fun encode(id: Long): String = TSID.from(id).toString()

    fun decode(value: String): Long {
        if (value.isBlank()) throw IllegalArgumentException("TSID value cannot be blank")
        return TSID.from(value).toLong()
    }
}
