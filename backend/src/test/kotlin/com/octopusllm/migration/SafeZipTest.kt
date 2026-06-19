package com.octopusllm.migration

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SafeZipTest {

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun `reads well-formed entries`() {
        val zip = zipOf("envelope.json" to "{}".toByteArray(), "quests/a.enc" to byteArrayOf(1, 2, 3))
        val result = SafeZip.readAll(ByteArrayInputStream(zip))
        assertEquals(setOf("envelope.json", "quests/a.enc"), result.keys)
        assertArrayEquals(byteArrayOf(1, 2, 3), result["quests/a.enc"])
    }

    @Test
    fun `rejects path traversal`() {
        val zip = zipOf("../evil.sh" to byteArrayOf(0))
        assertThrows(UnsafeArchiveException::class.java) { SafeZip.readAll(ByteArrayInputStream(zip)) }
    }

    @Test
    fun `rejects absolute path`() {
        val zip = zipOf("/etc/passwd" to byteArrayOf(0))
        assertThrows(UnsafeArchiveException::class.java) { SafeZip.readAll(ByteArrayInputStream(zip)) }
    }

    @Test
    fun `rejects too many entries`() {
        val zip = zipOf("a.enc" to byteArrayOf(1), "b.enc" to byteArrayOf(2))
        assertThrows(UnsafeArchiveException::class.java) {
            SafeZip.readAll(ByteArrayInputStream(zip), SafeZip.Limits(maxEntries = 1))
        }
    }

    @Test
    fun `rejects oversized entry`() {
        val zip = zipOf("big.enc" to ByteArray(100))
        assertThrows(UnsafeArchiveException::class.java) {
            SafeZip.readAll(ByteArrayInputStream(zip), SafeZip.Limits(maxEntryBytes = 10))
        }
    }

    @Test
    fun `rejects total expanded size over the limit`() {
        val zip = zipOf("a.enc" to ByteArray(8), "b.enc" to ByteArray(8))
        assertThrows(UnsafeArchiveException::class.java) {
            SafeZip.readAll(ByteArrayInputStream(zip), SafeZip.Limits(maxTotalBytes = 10))
        }
    }
}
