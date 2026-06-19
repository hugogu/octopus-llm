package com.octopusllm.migration

/**
 * Validates and imports a migration artifact under the calling admin (feature 008).
 *
 * Skeleton — implemented in US1 (tasks T025/T026): stream the upload to bounded temp storage; enforce
 * ZIP limits; authenticate/decrypt; validate schema/version/checksums/references and endpoints; then
 * stage media (via the staging ledger) and commit the whole artifact in one DB transaction, re-encrypting
 * provider keys with the target master key. Uses [MigrationOperationService] for idempotency and
 * [MigrationStagedMediaCleanupService] for compensation.
 */
class MigrationImportService
