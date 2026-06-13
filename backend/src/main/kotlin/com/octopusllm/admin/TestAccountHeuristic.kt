package com.octopusllm.admin

/**
 * Identifies "garbage" accounts created by tests or local experiments, based on RFC-reserved
 * example/test email domains (real users never use these). Administrators are never flagged.
 *
 * The SQL in [com.octopusllm.auth.UserRepository.findSuspectedTestAccounts] MUST stay in sync with
 * [isTestEmail] so the per-row flag and the bulk purge agree on what counts as a test account.
 */
object TestAccountHeuristic {
    private val reservedDomains = setOf(
        "example.com", "example.org", "example.net", "localhost",
    )
    private val reservedSuffixes = listOf(".test", ".example", ".invalid", ".localhost")

    fun isTestEmail(email: String): Boolean {
        val normalized = email.lowercase()
        val domain = normalized.substringAfter('@', "")
        if (domain.isEmpty()) return false
        if (domain in reservedDomains) return true
        return reservedSuffixes.any { domain.endsWith(it) }
    }
}
