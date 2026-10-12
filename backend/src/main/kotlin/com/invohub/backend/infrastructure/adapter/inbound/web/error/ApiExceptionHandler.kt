package com.invohub.backend.infrastructure.adapter.inbound.web.error

import com.invohub.backend.domain.auth.AccountSuspendedException
import com.invohub.backend.domain.auth.EmailDeliveryException
import com.invohub.backend.infrastructure.adapter.inbound.web.auth.InvalidSignInLinkException
import com.invohub.backend.infrastructure.adapter.inbound.web.filter.RequestIdFilter
import io.github.oshai.kotlinlogging.KotlinLogging
import org.slf4j.MDC
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

// Not `logger`: ResponseEntityExceptionHandler already has a field by that name
private val log = KotlinLogging.logger {}

/**
 * Renders every REST error as an RFC 9457 problem detail (application/problem+json), tagged with the
 * request ID so a user-reported error can be found in the logs.
 *
 * Problems a client shows a message for carry a `code`; clients pick the message (in the user's language) by
 * it, and `detail` is English for developers.
 */
@RestControllerAdvice
class ApiExceptionHandler : ResponseEntityExceptionHandler() {
    @ExceptionHandler(EmailDeliveryException::class)
    fun handleEmailDelivery(
        e: EmailDeliveryException,
        request: WebRequest,
    ): ResponseEntity<Any>? =
        problem(
            e,
            HttpStatus.SERVICE_UNAVAILABLE,
            "We couldn't send the email right now. Please try again in a few minutes.",
            request,
            code = "email_delivery_failed",
        )

    // suspensionReason is deliberately not exposed: it is an internal note, not a message for the user
    @ExceptionHandler(AccountSuspendedException::class)
    fun handleAccountSuspended(
        e: AccountSuspendedException,
        request: WebRequest,
    ): ResponseEntity<Any>? =
        problem(e, HttpStatus.FORBIDDEN, "This account is suspended.", request, code = "account_suspended")

    @ExceptionHandler(InvalidSignInLinkException::class)
    fun handleInvalidSignInLink(
        e: InvalidSignInLinkException,
        request: WebRequest,
    ): ResponseEntity<Any>? =
        problem(
            e,
            HttpStatus.UNAUTHORIZED,
            "This sign-in link is invalid or has expired.",
            request,
            code = "invalid_link",
        )

    // Raised when a save is based on a stale version of the row (see BaseEntity.version)
    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun handleConcurrentModification(
        e: OptimisticLockingFailureException,
        request: WebRequest,
    ): ResponseEntity<Any>? =
        problem(
            e,
            HttpStatus.CONFLICT,
            "This was changed by someone else. Reload and try again.",
            request,
            code = "conflict",
        )

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(
        e: Exception,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        log.error(e) { "Unhandled exception" }
        // Never echo the exception message: it can carry internals such as SQL or hostnames
        return problem(
            e,
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Something went wrong on our side.",
            request,
            code = "internal_error",
        )
    }

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        ex.body.setProperty("code", "invalid_request")
        ex.body.setProperty(
            "errors",
            ex.bindingResult.fieldErrors.map { mapOf("field" to it.field, "message" to it.defaultMessage) },
        )
        return super.handleMethodArgumentNotValid(ex, headers, status, request)
    }

    // The last step for every handled exception; earlier hooks may not have built the ProblemDetail yet
    override fun createResponseEntity(
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any> {
        (body as? ProblemDetail)?.setProperty("requestId", MDC.get(RequestIdFilter.MDC_KEY))
        return super.createResponseEntity(body, headers, statusCode, request)
    }

    /** [code] is a stable, machine-readable identifier for clients that need to tell problems apart. */
    private fun problem(
        e: Exception,
        status: HttpStatus,
        detail: String,
        request: WebRequest,
        code: String? = null,
    ): ResponseEntity<Any>? {
        val body = ProblemDetail.forStatusAndDetail(status, detail)
        code?.let { body.setProperty("code", it) }
        return handleExceptionInternal(e, body, HttpHeaders(), status, request)
    }
}
