package com.octopusllm.connection

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

@Component
class ConnectionEndpointPolicy(
    @Value("\${app.connections.allow-local-http:false}") private val allowLocalHttp: Boolean,
) {
    fun normalizeAndValidate(rawUrl: String): String {
        val uri = try {
            URI(rawUrl.trim())
        } catch (_: Exception) {
            throw invalid("Base URL is invalid")
        }

        val scheme = uri.scheme?.lowercase() ?: throw invalid("Base URL must include a scheme")
        val host = uri.host ?: throw invalid("Base URL must include a valid host")
        if (uri.userInfo != null) throw invalid("Base URL must not include user information")
        if (uri.fragment != null) throw invalid("Base URL must not include a fragment")
        if (uri.query != null) throw invalid("Base URL must not include a query")
        if (scheme != "https" && !(allowLocalHttp && scheme == "http" && isLoopbackHost(host))) {
            throw invalid("Base URL must use HTTPS")
        }

        val addresses = try {
            InetAddress.getAllByName(host).toList()
        } catch (_: Exception) {
            throw invalid("Base URL host could not be resolved")
        }
        if (addresses.isEmpty()) throw invalid("Base URL host could not be resolved")
        if (!allowLocalHttp || !isLoopbackHost(host)) {
            if (addresses.any { !isPublicAddress(it) }) {
                throw invalid("Base URL must resolve only to public addresses")
            }
        }

        val normalizedPath = uri.path.orEmpty().trimEnd('/')
        return URI(
            scheme,
            null,
            host.lowercase(),
            uri.port,
            normalizedPath.ifEmpty { null },
            null,
            null,
        ).toASCIIString()
    }

    private fun isLoopbackHost(host: String): Boolean =
        host.equals("localhost", true) || runCatching { InetAddress.getByName(host).isLoopbackAddress }.getOrDefault(false)

    private fun isPublicAddress(address: InetAddress): Boolean {
        if (
            address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) return false

        val bytes = address.address.map { it.toInt() and 0xff }
        return when (address) {
            is Inet4Address -> {
                val first = bytes[0]
                val second = bytes[1]
                !(first == 0 ||
                    first == 10 ||
                    first == 100 && second in 64..127 ||
                    first == 127 ||
                    first == 169 && second == 254 ||
                    first == 172 && second in 16..31 ||
                    first == 192 && second == 0 ||
                    first == 192 && second == 168 ||
                    first == 198 && second in 18..19 ||
                    first >= 224)
            }
            is Inet6Address -> {
                val first = bytes[0]
                val second = bytes[1]
                val uniqueLocal = first and 0xfe == 0xfc
                val documentation = first == 0x20 && second == 0x01 && bytes[2] == 0x0d && bytes[3] == 0xb8
                !uniqueLocal && !documentation
            }
            else -> false
        }
    }

    private fun invalid(message: String) = ResponseStatusException(HttpStatus.BAD_REQUEST, message)
}
