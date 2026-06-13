package com.octopusllm.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import java.net.InetAddress

@Component
class TrustedClientIpResolver(
    @Value("\${app.network.trusted-proxies:}") trustedProxies: String,
) {
    private val trustedProxyAddresses = trustedProxies
        .split(",")
        .map(String::trim)
        .filter(String::isNotEmpty)
        .mapNotNull { runCatching { InetAddress.getByName(it).hostAddress }.getOrNull() }
        .toSet()

    fun resolve(exchange: ServerWebExchange): String? {
        val directPeer = exchange.request.remoteAddress?.address?.hostAddress ?: return null
        if (directPeer !in trustedProxyAddresses) return directPeer

        val forwarded = exchange.request.headers.getFirst("X-Forwarded-For")
            ?.split(",")
            ?.map(String::trim)
            ?.firstOrNull()
            ?.takeIf(String::isNotEmpty)
        return forwarded?.let { normalize(it) } ?: directPeer
    }

    private fun normalize(value: String): String? =
        runCatching { InetAddress.getByName(value).hostAddress }.getOrNull()
}
