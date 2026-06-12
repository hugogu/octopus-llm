package com.octopusllm.api.v2

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

data class PageResponse<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)

data class ApiErrorResponse(
    val code: String,
    val message: String,
    val details: Map<String, Any> = emptyMap(),
)

fun <T, R> Page<T>.toPageResponse(mapper: (T) -> R): PageResponse<R> =
    PageResponse(
        items = content.map(mapper),
        page = number,
        size = size,
        totalElements = totalElements,
        totalPages = totalPages,
    )

fun boundedPageRequest(
    page: Int,
    size: Int,
    vararg orders: Sort.Order,
): PageRequest {
    if (page < 0) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be at least 0")
    }
    if (size !in 1..100) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be between 1 and 100")
    }
    return PageRequest.of(page, size, Sort.by(orders.toList()))
}
