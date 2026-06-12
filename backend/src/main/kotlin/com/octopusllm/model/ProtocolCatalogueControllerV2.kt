package com.octopusllm.model

import com.octopusllm.api.v2.PageResponse
import com.octopusllm.api.v2.boundedPageRequest
import org.springframework.data.domain.Sort
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class ProtocolResponseV2(
    val id: String,
    val displayName: String,
    val defaultBaseUrl: String?,
    val capabilities: com.octopusllm.llm.CapabilityMatrix,
)

@RestController
class ProtocolCatalogueControllerV2 {
    @GetMapping("/api/v2/protocols")
    fun protocols(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
    ): PageResponse<ProtocolResponseV2> {
        boundedPageRequest(page, size, Sort.Order.asc("id"))
        return page(
            ProtocolDefinitions.all.sortedBy { it.id }.map {
                ProtocolResponseV2(it.id, it.displayName, it.defaultBaseUrl, it.baseline)
            },
            page,
            size,
        )
    }

    @GetMapping("/api/v2/catalogue")
    fun catalogue(
        @RequestParam(required = false) protocol: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
    ): PageResponse<CatalogueEntry> {
        boundedPageRequest(page, size, Sort.Order.asc("providerLabel"), Sort.Order.asc("displayName"))
        if (protocol != null) ProtocolDefinitions.require(protocol)
        val entries = ModelCatalogue.entries
            .asSequence()
            .filter { protocol == null || it.protocol == protocol }
            .sortedWith(compareBy(CatalogueEntry::providerLabel, CatalogueEntry::displayName, CatalogueEntry::modelId))
            .toList()
        return page(entries, page, size)
    }

    private fun <T> page(items: List<T>, page: Int, size: Int): PageResponse<T> {
        val start = (page * size).coerceAtMost(items.size)
        val end = (start + size).coerceAtMost(items.size)
        val totalPages = if (items.isEmpty()) 0 else (items.size + size - 1) / size
        return PageResponse(items.subList(start, end), page, size, items.size.toLong(), totalPages)
    }
}
