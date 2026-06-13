package com.octopusllm.llm.adapter

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.HttpStatusCode
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.netty.http.HttpProtocol
import reactor.netty.http.client.HttpClient
import java.nio.charset.StandardCharsets
import java.net.URI

/**
 * Shared non-blocking HTTP client for provider streaming. Reactor-Netty runs the request and SSE read
 * on the event loop, so a model stream never holds a worker thread for its whole lifetime (unlike the
 * blocking OkHttp/SDK path). One slow or stalled provider therefore cannot starve the others.
 *
 * Negotiates HTTP/2 with HTTP/1.1 fallback (ALPN); redirects are not followed (SSRF safety — a 3xx
 * surfaces as an error instead of being chased to an unvalidated target).
 */
internal object StreamingWebClient {
    private val connector = ReactorClientHttpConnector(
        HttpClient.create()
            .protocol(HttpProtocol.H2, HttpProtocol.HTTP11)
            .followRedirect(false),
    )

    fun builder(baseUrl: String): WebClient.Builder =
        WebClient.builder().baseUrl(baseUrl).clientConnector(connector)
}

internal class ProviderHttpException(status: HttpStatusCode) :
    RuntimeException("Provider returned HTTP ${status.value()}")

internal fun providerEndpoint(baseUrl: String, path: String): URI {
    val base = baseUrl.trimEnd('/')
    val suffix = path.trimStart('/')
    return URI.create("$base/$suffix")
}

/** Reassembles a raw SSE byte stream into the JSON payload of each `data:` line across network chunks. */
internal object SseStreaming {
    fun dataPayloads(body: Flux<DataBuffer>): Flux<String> {
        var pending = ByteArray(0)
        return body.concatMap { buffer ->
            try {
                val bytes = ByteArray(buffer.readableByteCount())
                buffer.read(bytes)
                pending += bytes
                val payloads = mutableListOf<String>()
                var lineStart = 0
                var newline = pending.indexOf('\n'.code.toByte(), lineStart)
                while (newline >= 0) {
                    val line = String(
                        pending,
                        lineStart,
                        newline - lineStart,
                        StandardCharsets.UTF_8,
                    ).trim()
                    if (line.startsWith("data:")) {
                        val data = line.removePrefix("data:").trim()
                        if (data.isNotEmpty()) payloads.add(data)
                    }
                    lineStart = newline + 1
                    newline = pending.indexOf('\n'.code.toByte(), lineStart)
                }
                if (lineStart > 0) {
                    pending = pending.copyOfRange(lineStart, pending.size)
                }
                Flux.fromIterable(payloads)
            } finally {
                DataBufferUtils.release(buffer)
            }
        }
    }
}

private fun ByteArray.indexOf(value: Byte, startIndex: Int): Int {
    for (index in startIndex until size) {
        if (this[index] == value) return index
    }
    return -1
}

internal fun JsonNode.textOrNull(field: String): String? =
    path(field).takeIf { it.isTextual }?.asText()?.takeIf { it.isNotEmpty() }

internal fun JsonNode.intOrNull(field: String): Int? =
    path(field).takeIf { it.isIntegralNumber }?.asInt()
