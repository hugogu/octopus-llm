package com.octopusllm.migration

/**
 * Authenticated password-based encryption of migration artifact entries (feature 008).
 *
 * Skeleton — implemented in US1 (task T023) using Spring Security Crypto's password-based authenticated
 * encryption: one random salt per artifact, each structured/media entry encrypted independently, and
 * passphrase/key material held in memory only (never logged, returned, or audited).
 */
class MigrationArtifactCrypto
