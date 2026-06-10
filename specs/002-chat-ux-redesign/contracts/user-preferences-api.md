# API Contract: User Preferences

**Version**: v1  
**Base Path**: `/api/v1/user/preferences`

## Endpoints

### GET /api/v1/user/preferences

Retrieve the current user's preferences.

**Response** (200 OK):
```json
{
  "lastSelectedModelId": "gpt-4o",
  "themePreference": "system",
  "sidebarCollapsed": false
}
```

**Error Responses**:
- `401 Unauthorized`: User not authenticated
- `404 Not Found`: Preferences not yet created (return defaults)

---

### PUT /api/v1/user/preferences

Update the current user's preferences.

**Request Body**:
```json
{
  "lastSelectedModelId": "claude-3-opus",
  "themePreference": "dark",
  "sidebarCollapsed": true
}
```

**Validation**:
- `lastSelectedModelId`: optional, string, max 255 chars
- `themePreference`: optional, enum `["light", "dark", "system"]`
- `sidebarCollapsed`: optional, boolean

**Response** (200 OK):
```json
{
  "lastSelectedModelId": "claude-3-opus",
  "themePreference": "dark",
  "sidebarCollapsed": true
}
```

**Error Responses**:
- `400 Bad Request`: Invalid field values
- `401 Unauthorized`: User not authenticated

---

### PATCH /api/v1/user/preferences

Partial update of user preferences (individual fields).

**Request Body** (any subset of fields):
```json
{
  "lastSelectedModelId": "gpt-4o-mini"
}
```

**Response**: Same as PUT

**Notes**:
- This endpoint follows the same response schema as GET/PUT
- Only provided fields are updated; omitted fields retain current values

## Data Types

### UserPreferencesDto

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| lastSelectedModelId | string | No | Most recently selected model identifier |
| themePreference | string | No | UI theme: `light`, `dark`, or `system` |
| sidebarCollapsed | boolean | No | Whether chat sidebar is collapsed |

## Error Schema

All errors follow the standard platform error format:

```json
{
  "code": "INVALID_THEME",
  "message": "Theme preference must be one of: light, dark, system",
  "details": {}
}
```