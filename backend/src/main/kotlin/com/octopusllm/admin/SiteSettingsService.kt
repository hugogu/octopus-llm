package com.octopusllm.admin

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Site-info configuration. Read path seeds the singleton row on first access so the admin page
 * and the public footer always find a record. Update stamps updatedBy + updatedAt for audit.
 */
@Service
class SiteSettingsService(
    private val repository: SiteSettingsRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun get(): SiteSettings =
        repository.findById(SiteSettings.SINGLETON_ID).orElseGet {
            repository.save(SiteSettings())
        }

    @Transactional
    fun update(adminId: UUID, req: SiteSettingsUpdate): SiteSettings {
        val s = get()
        req.siteName?.let { s.siteName = it.normalize() }
        req.footerText?.let { s.footerText = it.normalize() }
        req.icpRecordNo?.let { s.icpRecordNo = it.normalize() }
        req.policeRecordNo?.let { s.policeRecordNo = it.normalize() }
        s.updatedBy = adminId
        s.updatedAt = Instant.now()
        val saved = repository.save(s)
        log.info(
            "site_settings_updated by={} siteName={} icpNo={} policeNo={}",
            adminId.toString().take(8),
            !saved.siteName.isNullOrBlank(),
            !saved.icpRecordNo.isNullOrBlank(),
            !saved.policeRecordNo.isNullOrBlank(),
        )
        return saved
    }

    /** Trim and collapse all-whitespace to `null` so the row never stores a meaningless value. */
    private fun String.normalize(): String? = trim().ifBlank { null }
}
