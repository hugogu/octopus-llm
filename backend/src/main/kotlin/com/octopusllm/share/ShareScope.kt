package com.octopusllm.share

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/**
 * Audience scope of a share (feature 008). Persisted as the lowercase wire value to match the
 * `session_shares.scope` CHECK constraint and the V032 backfill.
 */
enum class ShareScope(val wire: String) {
    /** Visible only to authenticated platform users (default for new shares). */
    AUTHENTICATED("authenticated"),

    /** Visible to anyone with the opaque token. */
    PUBLIC("public");

    companion object {
        fun fromWire(value: String): ShareScope =
            entries.firstOrNull { it.wire == value }
                ?: throw IllegalArgumentException("Unknown share scope: $value")
    }
}

@Converter
class ShareScopeConverter : AttributeConverter<ShareScope, String> {
    override fun convertToDatabaseColumn(attribute: ShareScope?): String? = attribute?.wire
    override fun convertToEntityAttribute(dbData: String?): ShareScope? = dbData?.let(ShareScope::fromWire)
}
