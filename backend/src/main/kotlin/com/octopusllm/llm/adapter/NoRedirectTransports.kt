package com.octopusllm.llm.adapter

import com.anthropic.backends.Backend
import com.anthropic.errors.AnthropicIoException
import com.openai.errors.OpenAIIoException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import java.io.IOException
import java.io.InputStream
import java.time.Duration
import java.util.concurrent.CompletableFuture

/**
 * Stainless' bundled OkHttp transports follow redirects by default. These
 * protocol-specific wrappers keep SDK serialization while making 3xx
 * responses visible to the SDK instead of following an unvalidated target.
 */
internal class NoRedirectOpenAiTransport(
    private val baseUrl: HttpUrl,
) : com.openai.core.http.HttpClient {
    private val client = noRedirectClient()

    override fun execute(
        request: com.openai.core.http.HttpRequest,
        requestOptions: com.openai.core.RequestOptions,
    ): com.openai.core.http.HttpResponse =
        try {
            client.newCall(request.toOkHttpRequest(baseUrl)).execute().toOpenAiResponse()
        } catch (exception: IOException) {
            throw OpenAIIoException("Request failed", exception)
        }

    override fun executeAsync(
        request: com.openai.core.http.HttpRequest,
        requestOptions: com.openai.core.RequestOptions,
    ): CompletableFuture<com.openai.core.http.HttpResponse> =
        CompletableFuture.supplyAsync { execute(request, requestOptions) }

    override fun close() = closeClient(client)
}

internal class NoRedirectAnthropicTransport(
    private val backend: Backend,
) : com.anthropic.core.http.HttpClient {
    private val client = noRedirectClient()

    override fun execute(
        request: com.anthropic.core.http.HttpRequest,
        requestOptions: com.anthropic.core.RequestOptions,
    ): com.anthropic.core.http.HttpResponse =
        try {
            val prepared = backend.authorizeRequest(
                backend.prepareRequest(request).resolveAnthropicUrl(backend),
            )
            backend.prepareResponse(client.newCall(prepared.toOkHttpRequest()).execute().toAnthropicResponse())
        } catch (exception: IOException) {
            throw AnthropicIoException("Request failed", exception)
        }

    override fun executeAsync(
        request: com.anthropic.core.http.HttpRequest,
        requestOptions: com.anthropic.core.RequestOptions,
    ): CompletableFuture<com.anthropic.core.http.HttpResponse> =
        CompletableFuture.supplyAsync { execute(request, requestOptions) }

    override fun close() {
        backend.close()
        closeClient(client)
    }
}

private fun noRedirectClient() = okhttp3.OkHttpClient.Builder()
    .followRedirects(false)
    .followSslRedirects(false)
    .connectTimeout(Duration.ofSeconds(15))
    .readTimeout(Duration.ofSeconds(120))
    .writeTimeout(Duration.ofSeconds(30))
    .callTimeout(Duration.ofMinutes(10))
    .build()

private fun closeClient(client: okhttp3.OkHttpClient) {
    client.dispatcher.executorService.shutdown()
    client.connectionPool.evictAll()
    client.cache?.close()
}

private fun com.openai.core.http.HttpRequest.toOkHttpRequest(baseUrl: HttpUrl): Request {
    val url = url ?: baseUrl.newBuilder().also { builder ->
        pathSegments.forEach(builder::addPathSegment)
        queryParams.keys().forEach { key ->
            queryParams.values(key).forEach { value -> builder.addQueryParameter(key, value) }
        }
    }.build().toString()
    return Request.Builder()
        .url(url)
        .method(method.name, body?.toOkHttpBody() ?: emptyBodyIfRequired(method.name))
        .also { builder ->
            headers.names().forEach { name ->
                headers.values(name).forEach { value -> builder.addHeader(name, value) }
            }
        }
        .build()
}

private fun com.anthropic.core.http.HttpRequest.resolveAnthropicUrl(
    backend: Backend,
): com.anthropic.core.http.HttpRequest {
    if (url != null) return this
    val resolved = backend.baseUrl().toHttpUrl().newBuilder().also { builder ->
        pathSegments.forEach(builder::addPathSegment)
        queryParams.keys().forEach { key ->
            queryParams.values(key).forEach { value -> builder.addQueryParameter(key, value) }
        }
    }.build().toString()
    return toBuilder().url(resolved).build()
}

private fun com.anthropic.core.http.HttpRequest.toOkHttpRequest(): Request =
    Request.Builder()
        .url(requireNotNull(url))
        .method(method.name, body?.toOkHttpBody() ?: emptyBodyIfRequired(method.name))
        .also { builder ->
            headers.names().forEach { name ->
                headers.values(name).forEach { value -> builder.addHeader(name, value) }
            }
        }
        .build()

private fun com.openai.core.http.HttpRequestBody.toOkHttpBody(): RequestBody {
    val source = this
    return object : RequestBody() {
        override fun contentType() = source.contentType()?.toMediaType()
        override fun contentLength() = source.contentLength()
        override fun isOneShot() = !source.repeatable()
        override fun writeTo(sink: BufferedSink) = source.writeTo(sink.outputStream())
    }
}

private fun com.anthropic.core.http.HttpRequestBody.toOkHttpBody(): RequestBody {
    val source = this
    return object : RequestBody() {
        override fun contentType() = source.contentType()?.toMediaType()
        override fun contentLength() = source.contentLength()
        override fun isOneShot() = !source.repeatable()
        override fun writeTo(sink: BufferedSink) = source.writeTo(sink.outputStream())
    }
}

private fun emptyBodyIfRequired(method: String): RequestBody? =
    if (method in setOf("POST", "PUT", "PATCH")) "".toRequestBody() else null

private fun Response.toOpenAiResponse(): com.openai.core.http.HttpResponse {
    val response = this
    val responseHeaders = com.openai.core.http.Headers.builder().also { builder ->
        headers.forEach { (name, value) -> builder.put(name, value) }
    }.build()
    return object : com.openai.core.http.HttpResponse {
        override fun statusCode() = response.code
        override fun headers() = responseHeaders
        override fun body(): InputStream = requireNotNull(response.body).byteStream()
        override fun close() = response.close()
    }
}

private fun Response.toAnthropicResponse(): com.anthropic.core.http.HttpResponse {
    val response = this
    val responseHeaders = com.anthropic.core.http.Headers.builder().also { builder ->
        headers.forEach { (name, value) -> builder.put(name, value) }
    }.build()
    return object : com.anthropic.core.http.HttpResponse {
        override fun statusCode() = response.code
        override fun headers() = responseHeaders
        override fun body(): InputStream = requireNotNull(response.body).byteStream()
        override fun close() = response.close()
    }
}
