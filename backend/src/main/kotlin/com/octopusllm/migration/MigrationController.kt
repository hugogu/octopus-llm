package com.octopusllm.migration

/**
 * Admin migration endpoints under `/api/v2/admin/migration` (feature 008).
 *
 * Skeleton — endpoints added in US1 (task T027): streamed `POST /export` and multipart `POST /import`,
 * both `ROLE_ADMIN`-only, resolving the artifact passphrase from the request or the optional
 * `MIGRATION_ARTIFACT_PASSPHRASE` config property, never binding/logging the passphrase.
 */
class MigrationController
