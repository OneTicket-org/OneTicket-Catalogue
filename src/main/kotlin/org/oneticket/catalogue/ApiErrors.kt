package org.oneticket.catalogue

import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.CannotGetJdbcConnectionException
import org.springframework.transaction.CannotCreateTransactionException
import org.springframework.web.ErrorResponseException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import java.net.URI

/** One invalid field of a 422 body ([STY-20]). */
data class FieldError(val field: String, val code: String, val detail: String)

private fun problem(
    status: HttpStatus,
    slug: String,
    title: String,
    code: String,
    detail: String,
    extras: Map<String, Any> = emptyMap()
): ProblemDetail {
    val pd = ProblemDetail.forStatusAndDetail(status, detail)
    pd.type = URI.create("/problems/$slug")
    pd.title = title
    // The stable machine-readable code of the contract's Problem schema ([API-04], [STY-79]).
    pd.setProperty("code", code)
    extras.forEach { (name, value) -> pd.setProperty(name, value) }
    return pd
}

/**
 * Base class of the errors the API raises on purpose ([STY-29]: an `ErrorResponseException` carrying a
 * full `ProblemDetail`). Each route adds its own subclasses with a stable `code`, e.g.
 * `EventNotFoundException` (404 EVENT_NOT_FOUND), `SalesOpenException` (409 SALES_OPEN).
 */
open class ApiException(
    status: HttpStatus,
    slug: String,
    title: String,
    val code: String,
    detail: String,
    extras: Map<String, Any> = emptyMap()
) : ErrorResponseException(status, problem(status, slug, title, code, detail, extras), null)

class ValidationFailedException(errors: List<FieldError>) : ApiException(
    status = HttpStatus.UNPROCESSABLE_CONTENT,
    slug = "validation-failed",
    title = "Validation failed",
    code = "VALIDATION_FAILED",
    detail = "The request has invalid fields.",
    extras = mapOf("errors" to errors)
)

/** Maps a Jakarta constraint to the validation codes of the contract ([STY-20]). */
private fun validationCode(constraint: String?): String = when (constraint) {
    "NotNull", "NotBlank", "NotEmpty" -> "REQUIRED"
    "Size", "Min", "Max", "Positive", "PositiveOrZero", "Range", "DecimalMin", "DecimalMax" -> "OUT_OF_RANGE"
    else -> "INVALID_FORMAT"
}

/**
 * The single place that turns every failure into `application/problem+json` ([STY-29], [OPS-17]):
 * Spring's own exceptions through `ResponseEntityExceptionHandler`, the API's through `ApiException`.
 */
@RestControllerAdvice
class ApiExceptionHandler : ResponseEntityExceptionHandler() {

    private val log = LoggerFactory.getLogger(javaClass)

    // Bean Validation failures: 422 with {field, code, detail} ([STY-20]).
    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest
    ): ResponseEntity<Any> {
        val errors = ex.bindingResult.fieldErrors.map {
            FieldError(it.field, validationCode(it.code), it.defaultMessage ?: "Invalid value.")
        }
        return handleExceptionInternal(ex, ValidationFailedException(errors).body, headers, HttpStatus.UNPROCESSABLE_CONTENT, request)
    }

    // Every Spring exception ends here: give its problem document the stable `code` the contract requires.
    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest
    ): ResponseEntity<Any> {
        val response = super.handleExceptionInternal(ex, body, headers, statusCode, request)
            ?: return ResponseEntity.status(statusCode).headers(headers).build()
        val pd = response.body as? ProblemDetail
        if (pd != null) addCodeIfMissing(pd, statusCode)
        return response
    }

    private fun addCodeIfMissing(pd: ProblemDetail, status: HttpStatusCode) {
        if (pd.properties?.containsKey("code") == true) return
        val (slug, title, code) = when (status.value()) {
            400 -> Triple("malformed-request", "Malformed request", "MALFORMED_REQUEST")
            406 -> Triple("not-acceptable", "Not acceptable", "NOT_ACCEPTABLE")
            415 -> Triple("unsupported-media-type", "Unsupported media type", "UNSUPPORTED_MEDIA_TYPE")
            else -> Triple("http-error", pd.title ?: "HTTP ${status.value()}", "HTTP_${status.value()}")
        }
        // Spring leaves the type unset (null) or "about:blank" when nobody chose one.
        val current: URI? = pd.type
        if (current == null || current.toString() == "about:blank") pd.type = URI.create("/problems/$slug")
        pd.title = title
        pd.setProperty("code", code)
    }

    // Database down or pool exhausted: JPA surfaces it as CannotCreateTransactionException, plain JDBC as
    // CannotGetJdbcConnectionException; 503 carries Retry-After ([API-19], [API-46]).
    @ExceptionHandler(
        DataAccessResourceFailureException::class,
        CannotGetJdbcConnectionException::class,
        CannotCreateTransactionException::class
    )
    fun databaseUnavailable(ex: Exception, request: WebRequest): ResponseEntity<Any> {
        log.error("Database unavailable", ex)
        val headers = HttpHeaders()
        headers.set(HttpHeaders.RETRY_AFTER, "5")
        return handleExceptionInternal(
            ex,
            problem(
                HttpStatus.SERVICE_UNAVAILABLE, "service-unavailable", "Service unavailable",
                "SERVICE_UNAVAILABLE", "The service is unavailable; retry later."
            ),
            headers, HttpStatus.SERVICE_UNAVAILABLE, request
        )
    }

    // Details go to the log with the correlation id, never to the client ([OPS-17]).
    @ExceptionHandler(Exception::class)
    fun unexpected(ex: Exception, request: WebRequest): ResponseEntity<Any> {
        log.error("Unexpected error", ex)
        return handleExceptionInternal(
            ex,
            problem(
                HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Internal server error",
                "INTERNAL_ERROR", "An unexpected error occurred."
            ),
            HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request
        )
    }
}