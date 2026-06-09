package com.octopusllm.config

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.FieldError
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono

data class ApiErrorResponse(
    val code: String,
    val message: String,
    val details: Map<String, Any> = emptyMap(),
)

class DuplicateRequestException(val turnId: String) : RuntimeException("Duplicate client request ID")

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatus(ex: ResponseStatusException): Mono<ResponseEntity<ApiErrorResponse>> {
        val body = ApiErrorResponse(
            code = ex.statusCode.toString(),
            message = ex.reason ?: ex.message,
        )
        return Mono.just(ResponseEntity.status(ex.statusCode).body(body))
    }

    @ExceptionHandler(DuplicateRequestException::class)
    fun handleDuplicateRequest(ex: DuplicateRequestException): Mono<ResponseEntity<Map<String, String>>> {
        return Mono.just(
            ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("turnId" to ex.turnId)),
        )
    }

    @ExceptionHandler(org.springframework.web.bind.support.WebExchangeBindException::class)
    fun handleValidation(ex: org.springframework.web.bind.support.WebExchangeBindException): Mono<ResponseEntity<ApiErrorResponse>> {
        val fieldErrors = ex.bindingResult.allErrors.associate { error ->
            val field = if (error is FieldError) error.field else error.objectName
            field to (error.defaultMessage ?: "invalid")
        }
        val body = ApiErrorResponse(
            code = "VALIDATION_ERROR",
            message = "Request validation failed",
            details = fieldErrors,
        )
        return Mono.just(ResponseEntity.badRequest().body(body))
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception): Mono<ResponseEntity<ApiErrorResponse>> {
        val body = ApiErrorResponse(
            code = "INTERNAL_ERROR",
            message = "An unexpected error occurred",
        )
        return Mono.just(ResponseEntity.internalServerError().body(body))
    }
}
