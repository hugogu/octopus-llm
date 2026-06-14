package com.octopusllm.render

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.net.URI
import java.util.zip.Deflater

/**
 * Renders PlantUML source to SVG using the self-hosted PlantUML server (clarification Q1). The source
 * is PlantUML-encoded (raw DEFLATE + PlantUML's base64 variant) and fetched from `/svg/{encoded}` so
 * the call works against the stock plantuml-server image with no extra round trips.
 *
 * Only ever talks to the configured `server-url` — no client-supplied URL — so there is no SSRF surface.
 */
@Service
class PlantUmlRenderService(
    @Value("\${app.render.plantuml.server-url}") serverUrl: String,
) {
    private val baseUrl = serverUrl.trimEnd('/')
    private val webClient = WebClient.builder().build()

    fun renderSvg(source: String): Mono<String> {
        val encoded = encode(source)
        return webClient.get()
            .uri(URI.create("$baseUrl/svg/$encoded"))
            .accept(MediaType.valueOf("image/svg+xml"), MediaType.APPLICATION_XML, MediaType.TEXT_XML)
            .retrieve()
            .bodyToMono(String::class.java)
    }

    /** PlantUML text encoding: UTF-8 → raw DEFLATE (level 9) → PlantUML base64 variant. */
    private fun encode(text: String): String {
        val input = text.toByteArray(Charsets.UTF_8)
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        deflater.setInput(input)
        deflater.finish()
        val buffer = ByteArray(input.size + 64)
        val out = java.io.ByteArrayOutputStream()
        while (!deflater.finished()) {
            val n = deflater.deflate(buffer)
            out.write(buffer, 0, n)
        }
        deflater.end()
        return encode64(out.toByteArray())
    }

    private fun encode64(data: ByteArray): String {
        val sb = StringBuilder()
        var i = 0
        while (i < data.size) {
            val b1 = data[i].toInt() and 0xFF
            when (data.size - i) {
                1 -> append3(sb, b1, 0, 0)
                2 -> append3(sb, b1, data[i + 1].toInt() and 0xFF, 0)
                else -> append3(sb, b1, data[i + 1].toInt() and 0xFF, data[i + 2].toInt() and 0xFF)
            }
            i += 3
        }
        return sb.toString()
    }

    private fun append3(sb: StringBuilder, b1: Int, b2: Int, b3: Int) {
        val c1 = b1 shr 2
        val c2 = ((b1 and 0x3) shl 4) or (b2 shr 4)
        val c3 = ((b2 and 0xF) shl 2) or (b3 shr 6)
        val c4 = b3 and 0x3F
        sb.append(encode6(c1 and 0x3F))
        sb.append(encode6(c2 and 0x3F))
        sb.append(encode6(c3 and 0x3F))
        sb.append(encode6(c4 and 0x3F))
    }

    private fun encode6(value: Int): Char {
        var b = value
        if (b < 10) return ('0' + b)
        b -= 10
        if (b < 26) return ('A' + b)
        b -= 26
        if (b < 26) return ('a' + b)
        b -= 26
        return when (b) {
            0 -> '-'
            1 -> '_'
            else -> '?'
        }
    }
}
