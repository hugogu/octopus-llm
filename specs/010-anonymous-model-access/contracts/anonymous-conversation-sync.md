# Contract: Authenticated Anonymous Conversation Synchronization

The route is called only after the registration flow has completed login and obtained an authenticated token. It is not public and does not run as part of the registration database transaction.

## Synchronize conversations

```http
POST /api/v2/anonymous/conversations/sync
Authorization: Bearer <token>
Content-Type: application/json
```

```json
{
  "conversations": [
    {
      "sourceConversationId": "browser-conversation-uuid",
      "sourceDigest": "sha256-hex-of-canonical-payload",
      "title": "first prompt-derived title",
      "createdAt": "2026-09-02T00:00:00Z",
      "updatedAt": "2026-09-02T00:05:00Z",
      "turns": [
        {
          "sourceTurnId": "browser-turn-uuid",
          "clientRequestId": "browser-request-uuid",
          "promptText": "Question",
          "createdAt": "2026-09-02T00:00:00Z",
          "responses": [
            {
              "configuredModelId": "configured-model-uuid",
              "modelId": "provider-model-id",
              "modelDisplayName": "Model snapshot",
              "protocol": "openai-compatible",
              "status": "COMPLETE",
              "responseText": "Answer",
              "reasoningText": null,
              "errorMessage": null
            }
          ]
        }
      ]
    }
  ]
}
```

The request has a bounded conversation count, total body size, turn count, and response text size. The backend authenticates the destination user, validates the envelope and digest, and imports only response states supported by the authenticated schema. It never calls a provider, revalidates a model for access, or grants the user access to the model. Imported response metadata is a historical snapshot; a deleted or no-longer-eligible model remains readable but is not automatically retryable.

The first release returns `skipped` for a conversation containing unsupported response states, attachments, tools, or malformed data. The local source remains intact, and the result contains a safe reason code. The implementation must not silently discard a partial response.

## Response

```json
{
  "items": [
    {
      "sourceConversationId": "browser-conversation-uuid",
      "status": "IMPORTED",
      "sessionId": "authenticated-session-uuid",
      "reasonCode": null,
      "message": "Conversation imported."
    },
    {
      "sourceConversationId": "another-browser-conversation-uuid",
      "status": "ALREADY_IMPORTED",
      "sessionId": "existing-session-uuid",
      "reasonCode": null,
      "message": "Conversation was already imported."
    },
    {
      "sourceConversationId": "unsupported-browser-conversation-uuid",
      "status": "SKIPPED",
      "sessionId": null,
      "reasonCode": "UNSUPPORTED_RESPONSE_STATE",
      "message": "This conversation needs a later migration path and remains on this device."
    }
  ]
}
```

Possible statuses are `IMPORTED`, `ALREADY_IMPORTED`, `SKIPPED`, and `FAILED`. A failed item does not remove local data. The service processes items independently, so one failure does not roll back other conversations.

## Idempotency and conflicts

The source identity is `(authenticatedUserId, sourceConversationId)` and the digest is compared with the stored digest:

| Condition | Result |
|---|---|
| No identity exists | Import once in a transaction; return `IMPORTED` and the session ID. |
| Identity exists with equal digest and successful session | Return `ALREADY_IMPORTED` and the original session ID; create nothing. |
| Identity exists with a different digest | Return item conflict (`409` at item or batch envelope) and retain both local and server data. |
| Two requests race for one identity | Database uniqueness decides the winner; the loser reads the committed result and returns `ALREADY_IMPORTED` or a retryable status. |

The frontend clears a local conversation only after receiving `IMPORTED` or `ALREADY_IMPORTED` with a session ID. Registration success and chat access are not blocked by a failed or skipped synchronization; the UI exposes a retry action. Normal login does not automatically merge local conversations into an existing account.

## Errors and security

- `400`: malformed or over-limit batch/envelope.
- `401`: missing or invalid authentication.
- `409`: source digest conflict.
- `413`: batch body exceeds the configured limit.

Messages and logs contain no raw token, prompt, provider key, endpoint, custom parameter, or response body beyond the destination authenticated chat tables. Imported conversations acquire the normal authenticated sharing behavior only after a successful import and session ID are returned.
