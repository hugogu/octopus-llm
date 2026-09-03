package com.octopusllm.config

import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.CacheControl
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/** Browser-facing API responses contain account and model state; never allow intermediary caching. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class ApiNoStoreWebFilter : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        if (exchange.request.path.value().startsWith("/api/")) {
            exchange.response.beforeCommit {
                exchange.response.headers.cacheControl = CacheControl.noStore().headerValue
                Mono.empty()
            }
        }
        return chain.filter(exchange)
    }
}
