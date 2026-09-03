package com.octopusllm.admin

import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

/**
 * Admin-supplied site-info update. Null fields are left unchanged. Length caps are deliberately
 * generous so the admin can paste in long ICP block content without server-side truncation; the
 * public render only ever displays the value, never interprets it.
 */
data class SiteSettingsUpdate(
    @field:Size(max = 256) val siteName: String? = null,
    @field:Size(max = 2048) val footerText: String? = null,
    @field:Size(max = 256) val icpRecordNo: String? = null,
    @field:Size(max = 256) val policeRecordNo: String? = null,
    val chinaFilingEnabled: Boolean? = null,
)

/** Public, safe shape returned by the footer endpoint — no audit metadata, no secrets. */
data class SiteSettingsPublicView(
    val siteName: String?,
    val footerText: String?,
    val chinaFilingEnabled: Boolean,
    val icpRecordNo: String?,
    val policeRecordNo: String?,
)

/** Admin shape: full row including who/when. */
data class SiteSettingsAdminView(
    val siteName: String?,
    val footerText: String?,
    val chinaFilingEnabled: Boolean,
    val icpRecordNo: String?,
    val policeRecordNo: String?,
    val updatedAt: Instant,
    val updatedBy: UUID?,
)

internal fun SiteSettings.toPublicView() = SiteSettingsPublicView(
    siteName = siteName,
    footerText = footerText,
    chinaFilingEnabled = chinaFilingEnabled,
    icpRecordNo = icpRecordNo.takeIf { chinaFilingEnabled },
    policeRecordNo = policeRecordNo.takeIf { chinaFilingEnabled },
)

internal fun SiteSettings.toAdminView() = SiteSettingsAdminView(
    siteName = siteName,
    footerText = footerText,
    chinaFilingEnabled = chinaFilingEnabled,
    icpRecordNo = icpRecordNo,
    policeRecordNo = policeRecordNo,
    updatedAt = updatedAt,
    updatedBy = updatedBy,
)
