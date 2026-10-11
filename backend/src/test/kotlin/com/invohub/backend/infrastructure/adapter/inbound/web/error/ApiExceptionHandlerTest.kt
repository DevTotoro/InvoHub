package com.invohub.backend.infrastructure.adapter.inbound.web.error

import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.web.context.request.ServletWebRequest
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ApiExceptionHandlerTest {
    @Test
    fun `unexpected errors are a generic 500 that does not leak the exception message`() {
        val response =
            ApiExceptionHandler().handleUnexpected(
                IllegalStateException("password authentication failed for user root"),
                ServletWebRequest(MockHttpServletRequest()),
            )!!

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        val problem = response.body as ProblemDetail
        assertFalse(problem.detail!!.contains("password"))
    }

    @Test
    fun `a stale update is a 409 conflict`() {
        val response =
            ApiExceptionHandler().handleConcurrentModification(
                ObjectOptimisticLockingFailureException(Any::class.java, 1L),
                ServletWebRequest(MockHttpServletRequest()),
            )!!

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }
}
