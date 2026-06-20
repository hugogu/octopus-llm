package com.octopusllm.media

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * Local-filesystem media storage (feature 007). Writes opaque-named files under [localDir] and serves
 * them publicly (unauthenticated, non-enumerable) at [publicBaseUrl] via the media resource handler.
 * The opaque [id] is the only thing tying a URL to its bytes (Constitution VI, FR-022).
 */
@Component
class LocalMediaStorage(
    @Value("\${media.local.dir:./data/media}") private val localDir: String,
    @Value("\${media.local.public-base-url:http://localhost:8080/media}") private val publicBaseUrl: String,
) : MediaStorage {

    override val backend = "local"

    private val root: Path by lazy {
        Path.of(localDir).toAbsolutePath().normalize().also { Files.createDirectories(it) }
    }

    override fun store(id: UUID, bytes: ByteArray, mimeType: String, extension: String): StoredMedia {
        val filename = storageKey(id, extension)
        val target = root.resolve(filename).normalize()
        require(target.startsWith(root)) { "Resolved media path escapes storage root" }
        bytes.inputStream().use { input ->
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
        }
        return StoredMedia(
            storageKey = filename,
            publicUrl = "${publicBaseUrl.trimEnd('/')}/$filename",
            backend = backend,
        )
    }

    override fun read(storageKey: String): ByteArray? {
        val target = root.resolve(storageKey).normalize()
        if (!target.startsWith(root) || !Files.exists(target)) return null
        return Files.readAllBytes(target)
    }

    override fun delete(storageKey: String) {
        val target = root.resolve(storageKey).normalize()
        if (target.startsWith(root)) {
            Files.deleteIfExists(target)
        }
    }
}
