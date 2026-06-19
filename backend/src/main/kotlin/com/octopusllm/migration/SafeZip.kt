package com.octopusllm.migration

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Raised when an uploaded archive is structurally unsafe (→ 400 unsafe_archive). */
class UnsafeArchiveException(detail: String) :
    ResponseStatusException(HttpStatus.BAD_REQUEST, "unsafe_archive: $detail")

/**
 * Hardened ZIP reader for migration imports (feature 008, T016/T025). Defends against the classic
 * malicious-archive vectors before any bytes are trusted: path traversal, absolute paths, duplicate
 * entries, too many entries, oversized entries, and zip-bomb total expansion. Entries are read into
 * bounded in-memory buffers so a single huge entry cannot exhaust the heap.
 */
object SafeZip {
    data class Limits(
        val maxEntries: Int = 10_000,
        val maxEntryBytes: Long = 256L * 1024 * 1024,
        val maxTotalBytes: Long = 2L * 1024 * 1024 * 1024,
    )

    fun readAll(input: InputStream, limits: Limits = Limits()): Map<String, ByteArray> {
        val entries = LinkedHashMap<String, ByteArray>()
        var totalBytes = 0L
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    if (entries.size >= limits.maxEntries) throw UnsafeArchiveException("too many entries")
                    val name = entry.name
                    validateName(name)
                    if (entries.containsKey(name)) throw UnsafeArchiveException("duplicate entry: $name")
                    val bytes = readBounded(zip, limits.maxEntryBytes, name)
                    totalBytes += bytes.size
                    if (totalBytes > limits.maxTotalBytes) {
                        throw UnsafeArchiveException("expanded size exceeds limit")
                    }
                    entries[name] = bytes
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return entries
    }

    private fun validateName(name: String) {
        if (name.isBlank()) throw UnsafeArchiveException("blank entry name")
        if (name.startsWith("/") || name.startsWith("\\") || name.contains(":")) {
            throw UnsafeArchiveException("absolute path: $name")
        }
        val segments = name.replace('\\', '/').split('/')
        if (segments.any { it == ".." }) throw UnsafeArchiveException("path traversal: $name")
    }

    private fun readBounded(zip: ZipInputStream, maxBytes: Long, name: String): ByteArray {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        var read = zip.read(chunk)
        while (read >= 0) {
            buffer.write(chunk, 0, read)
            if (buffer.size() > maxBytes) throw UnsafeArchiveException("entry too large: $name")
            read = zip.read(chunk)
        }
        return buffer.toByteArray()
    }
}
