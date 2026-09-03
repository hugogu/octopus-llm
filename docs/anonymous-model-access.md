# Anonymous model access

Administrators manage anonymous access from `/admin/models`. The page only lists built-in configured
models and supports search, filtering, pagination, page selection, select-all-matching, preview, and
the following independent bulk actions:

- Allow or revoke anonymous access (`is_anonymous_allowed`)
- Show or hide a model (`is_enabled`)
- Delete configured models while retaining immutable provider-response snapshots

Anonymous chat is available at `/chat` without an account. The public catalogue exposes only enabled,
built-in models that are explicitly allowed. Anonymous requests are text-only, have no server session,
tools, media, retries, or share links, and are rate/concurrency limited. Stream state is stored in the
browser under `octopus.anonymous-conversations.v1`; local-only conversations are not shareable.

After registration completes login, the browser attempts a bounded conversation synchronization. Only
confirmed `IMPORTED` or `ALREADY_IMPORTED` results remove local data. Failed, skipped, corrupt, or
conflicting conversations remain on the device and can be retried from the authenticated chat page.

## Configuration

Set `ANONYMOUS_VISITOR_HMAC_SECRET` to a stable secret in each environment. The following limits are
available as environment variables; conservative defaults are defined in the application profiles:

`ANONYMOUS_RATE_LIMIT`, `ANONYMOUS_RATE_WINDOW_SECONDS`, `ANONYMOUS_CONCURRENCY_LIMIT`,
`ANONYMOUS_PROMPT_MAX_BYTES`, `ANONYMOUS_HISTORY_MAX_BYTES`, `ANONYMOUS_HISTORY_MAX_TURNS`,
`ANONYMOUS_MODEL_MAX_COUNT`, `ANONYMOUS_EXECUTION_TIMEOUT_SECONDS`,
`ANONYMOUS_OPERATION_MAX_TARGETS`, `ANONYMOUS_SYNC_MAX_CONVERSATIONS`, and
`ANONYMOUS_SYNC_MAX_BODY_BYTES`.

The HMAC-derived client bucket and lease table contain no raw IP address. Provider credentials,
endpoints, custom parameters, prompts, and anonymous response bodies are not written to anonymous
request records or audit metadata. API responses use `Cache-Control: no-store`.

## Rollback and operations

To disable anonymous use immediately, revoke anonymous access for all models in the admin page. This
does not change normal authenticated visibility or enabled state. In-flight streams may finish under
their existing lease; newly submitted requests re-check the current policy.

The Flyway migration `V041__anonymous_model_access.sql` is forward-only. Rollback should be performed
by disabling the policy and deploying a compatible application version; do not drop the migration
tables or the policy column while historical synchronization or audit records may still be present.
