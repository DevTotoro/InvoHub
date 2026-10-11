package com.invohub.backend.infrastructure.adapter.inbound.web.support

import com.invohub.backend.infrastructure.config.WebConfig
import io.hypersistence.tsid.TSID
import org.junit.jupiter.api.Test
import org.springframework.format.support.DefaultFormattingConversionService
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

class TSIDFormatterFactoryTest {
    @RestController
    class TestController {
        @GetMapping("/required/{id}")
        fun required(
            @TSIDParam @PathVariable id: Long,
        ) = id.toString()

        @GetMapping("/optional")
        fun optional(
            @TSIDParam @RequestParam(required = false) id: Long?,
        ) = id.toString()
    }

    private val mockMvc =
        MockMvcBuilders
            .standaloneSetup(TestController())
            .setConversionService(DefaultFormattingConversionService().also(WebConfig()::addFormatters))
            .build()

    private val tsid = TSID.fast()

    @Test
    fun `decodes a TSID into a non-null Long`() {
        mockMvc.get("/required/$tsid").andExpect {
            status { isOk() }
            content { string(tsid.toLong().toString()) }
        }
    }

    @Test
    fun `decodes a TSID into a nullable Long`() {
        mockMvc.get("/optional") { param("id", tsid.toString()) }.andExpect {
            status { isOk() }
            content { string(tsid.toLong().toString()) }
        }
    }

    @Test
    fun `leaves an absent optional parameter null`() {
        mockMvc.get("/optional").andExpect { content { string("null") } }
    }

    @Test
    fun `rejects a value that is not a TSID`() {
        mockMvc.get("/required/not-a-tsid").andExpect { status { isBadRequest() } }
    }
}
