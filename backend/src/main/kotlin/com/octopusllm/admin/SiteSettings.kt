package com.octopusllm.admin

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Platform-wide site-info shown in the public footer (site name, free-form footer text, ICP record
 * number for MIIT filing, public-security record number). Single mutable row (id = 1): editable
 * from the admin panel by `/api/v2/admin/site-settings`; the public read endpoint
 * `/api/v2/site-settings` returns only the non-secret fields needed for the footer.
 */
@Entity
@Table(name = "site_settings")
class SiteSettings(
    @Id
    @Column(name = "id")
    val id: Short = 1,

    @Column(name = "site_name", columnDefinition = "TEXT")
    var siteName: String? = null,

    @Column(name = "footer_text", columnDefinition = "TEXT")
    var footerText: String? = null,

    @Column(name = "icp_record_no", columnDefinition = "TEXT")
    var icpRecordNo: String? = null,

    @Column(name = "police_record_no", columnDefinition = "TEXT")
    var policeRecordNo: String? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Column(name = "updated_by", columnDefinition = "UUID")
    var updatedBy: UUID? = null,
) {
    companion object {
        const val SINGLETON_ID: Short = 1
    }
}
