package no.nav.helse.flex.api

import jakarta.servlet.http.HttpServletRequest
import no.nav.helse.flex.logger
import no.nav.security.token.support.core.exceptions.JwtTokenInvalidClaimException
import no.nav.security.token.support.spring.validation.interceptor.JwtTokenUnauthorizedException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.HttpMediaTypeNotAcceptableException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

@ControllerAdvice
class GlobalExceptionHandler {

    private val log = logger()

    @ExceptionHandler(java.lang.Exception::class)
    fun handleException(ex: Exception, request: HttpServletRequest): ResponseEntity<Any> {
        return when (ex) {
            is AbstractApiError -> {
                when (ex.loglevel) {
                    LogLevel.WARN -> log.warn(ex.message, ex)
                    LogLevel.ERROR -> log.error(ex.message, ex)
                    LogLevel.OFF -> {
                    }
                }

                ResponseEntity(ApiError(ex.reason), ex.httpStatus)
            }
            is JwtTokenInvalidClaimException -> skapResponseEntity(HttpStatus.UNAUTHORIZED)
            is JwtTokenUnauthorizedException -> skapResponseEntity(HttpStatus.UNAUTHORIZED)
            is HttpMediaTypeNotAcceptableException -> skapResponseEntity(HttpStatus.NOT_ACCEPTABLE)
            else -> {
                log.error("Internal server error - ${ex.message} - ${request.method}: ${request.requestURI}", ex)
                skapResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR)
            }
        }
    }
}

private fun skapResponseEntity(status: HttpStatus): ResponseEntity<Any> =
    ResponseEntity(ApiError(status.reasonPhrase), status)

private data class ApiError(val reason: String)

abstract class AbstractApiError(
    message: String,
    val httpStatus: HttpStatus,
    val reason: String,
    val loglevel: LogLevel,
    grunn: Throwable? = null
) : RuntimeException(message, grunn)

enum class LogLevel {
    WARN, ERROR, OFF
}
