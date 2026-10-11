package com.invohub.backend.infrastructure.adapter.inbound.web.filter

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.util.UUID

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestIdFilter : Filter {
    companion object {
        private const val REQUEST_ID_HEADER = "X-Request-ID"
        private const val MDC_KEY = "requestId"
    }

    override fun doFilter(
        request: ServletRequest,
        response: ServletResponse,
        chain: FilterChain,
    ) {
        val httpRequest = request as HttpServletRequest
        val httpResponse = response as HttpServletResponse

        val requestId = httpRequest.getHeader(REQUEST_ID_HEADER) ?: UUID.randomUUID().toString()

        MDC.put(MDC_KEY, requestId)

        httpResponse.setHeader(REQUEST_ID_HEADER, requestId)

        try {
            chain.doFilter(request, response)
        } finally {
            MDC.clear()
        }
    }
}
