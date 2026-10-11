package com.invohub.backend.infrastructure.adapter.inbound.web.error

import com.invohub.backend.infrastructure.adapter.inbound.web.filter.RequestIdFilter
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

/**
 * Writes problem details from servlet filters (e.g. Spring Security), which run before MVC and so never
 * reach [ApiExceptionHandler]. Produces the same shape, including the request ID.
 */
@Component
class ProblemDetailWriter(
    private val jsonMapper: JsonMapper,
) {
    fun write(
        response: HttpServletResponse,
        status: HttpStatus,
        detail: String,
        /** Same meaning as in [ApiExceptionHandler]: a stable identifier clients can branch on */
        code: String? = null,
    ) {
        val problem =
            ProblemDetail.forStatusAndDetail(status, detail).apply {
                code?.let { setProperty("code", it) }
                setProperty("requestId", MDC.get(RequestIdFilter.MDC_KEY))
            }
        response.status = status.value()
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        jsonMapper.writeValue(response.outputStream, problem)
    }
}
