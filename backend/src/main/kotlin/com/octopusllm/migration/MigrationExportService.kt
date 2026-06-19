package com.octopusllm.migration

/**
 * Streams the passphrase-encrypted migration artifact (feature 008).
 *
 * Skeleton — implemented in US1 (task T024): page through repositories, decrypt Connection keys only
 * in memory, exclude redacted Dialogs and their media, read included media one object at a time, and
 * stream `envelope.json` plus independently encrypted entries without buffering the whole artifact.
 */
class MigrationExportService
