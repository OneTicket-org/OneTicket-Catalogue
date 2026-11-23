package org.oneticket.catalogue

import org.springframework.core.MethodParameter
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.transaction.CannotCreateTransactionException
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.context.request.ServletWebRequest
import kotlin.test.Test
import kotlin.test.assertEquals

class EventNotFoundException(id: Long) : ApiException(
    status = HttpStatus.NOT_FOUND,
    slug = "event-not-found",
    title = "Event not found",
    code = "EVENT_NOT_FOUND",
    detail = "Event $id does not exist."
)

private class Dummy {
    @Suppress("unused")
    fun create(body: String) {
    }
}

// No Spring context, no container: the handler is a plain class ([STY-83]: one test per failure class).
class ApiExceptionHandlerTest {
    private val handler = ApiExceptionHandler()
    private val request = ServletWebRequest(MockHttpServletRequest("GET", "/api/v1/events"))

    private fun ProblemDetail.code(): Any? = properties?.get("code")

    @Test
    fun `a 400 raised by Spring is the contract's MALFORMED_REQUEST`() {
        val r = handler.handleException(MissingServletRequestParameterException("city", "String"), request)
        val pd = r!!.body as ProblemDetail
        assertEquals(400, r.statusCode.value())
        assertEquals("MALFORMED_REQUEST", pd.code())
        assertEquals("/problems/malformed-request", pd.type.toString())
    }

    @Test
    fun `a route error keeps its own status and stable code`() {
        val r = handler.handleException(EventNotFoundException(413), request)
        val pd = r!!.body as ProblemDetail
        assertEquals(404, r.statusCode.value())
        assertEquals("EVENT_NOT_FOUND", pd.code())
        assertEquals("/problems/event-not-found", pd.type.toString())
        assertEquals("Event 413 does not exist.", pd.detail)
    }

    @Test
    fun `bean validation failures are a 422 listing field, code and detail`() {
        val binding = BeanPropertyBindingResult(Any(), "eventInput")
        binding.addError(
            org.springframework.validation.FieldError(
                "eventInput", "title", "", false,
                arrayOf("NotBlank.eventInput.title", "NotBlank"), null, "must not be blank"
            )
        )
        val method = MethodParameter(Dummy::class.java.getDeclaredMethod("create", String::class.java), 0)
        val r = handler.handleException(MethodArgumentNotValidException(method, binding), request)
        val pd = r!!.body as ProblemDetail
        assertEquals(422, r.statusCode.value())
        assertEquals("VALIDATION_FAILED", pd.code())
        assertEquals(listOf(FieldError("title", "REQUIRED", "must not be blank")), pd.properties?.get("errors"))
    }

    @Test
    fun `database down is a 503 with Retry-After`() {
        val r = handler.databaseUnavailable(CannotCreateTransactionException("no connection"), request)
        val pd = r.body as ProblemDetail
        assertEquals(503, r.statusCode.value())
        assertEquals("SERVICE_UNAVAILABLE", pd.code())
        assertEquals("5", r.headers.getFirst("Retry-After"))
    }

    @Test
    fun `an unexpected exception is a 500 that does not leak its message`() {
        val r = handler.unexpected(IllegalStateException("secret internals"), request)
        val pd = r.body as ProblemDetail
        assertEquals(500, r.statusCode.value())
        assertEquals("INTERNAL_ERROR", pd.code())
        assertEquals("An unexpected error occurred.", pd.detail)
    }
}
