// ---------------------------------------------------------------------------
// Auth API — contracts/auth-api.md
// ---------------------------------------------------------------------------

export interface RegisterRequest {
  email: string;
  password: string;
}

export interface RegisterResponse {
  message: string;
}

export interface VerifyEmailRequest {
  token: string;
}

export interface VerifyEmailResponse {
  message: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  expiresAt: string;
}

// ---------------------------------------------------------------------------
// Shared model capabilities
// ---------------------------------------------------------------------------

export interface CapabilityMatrix {
  input_modalities: string[];
  output_modalities: string[];
  context_length_tokens: number | null;
  supports_streaming: boolean;
  supports_function_calling: boolean;
  supports_system_prompt: boolean;
  supports_video_input: boolean;
  [key: string]: unknown;
}

export interface Attachment {
  type: string;
  data: string;
  mimeType: string;
}

/**
 * Storage-backed media reference (feature 007). Returned by the upload endpoint and embedded in a
 * turn's attachments / history / share payloads. Replaces inline base64 going forward; `order`
 * preserves user drag-reorder within a turn.
 */
export interface MediaReference {
  media_id: string;
  media_type: "image" | "video" | "audio";
  mime_type: string;
  size_bytes: number;
  url: string;
  original_filename?: string | null;
  order?: number;
}

// ---------------------------------------------------------------------------
// Error response
// ---------------------------------------------------------------------------

export interface ApiError {
  code: string;
  message: string;
  details?: Record<string, unknown>;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AnalyticsSummary {
  totalResponses: number;
  successRate: number;
  avgLatencyMs: number;
  totalInputTokens: number;
  totalOutputTokens: number;
  estimatedCostsByCurrency: Record<string, number>;
}

export interface ModelAnalytics {
  configuredModelId: string;
  modelId: string;
  modelDisplayName: string;
  responseCount: number;
  successRate: number;
  avgLatencyMs: number;
  p95LatencyMs: number;
  inputTokens: number;
  outputTokens: number;
  estimatedCostsByCurrency: Record<string, number>;
}

export interface SessionAnalytics {
  sessionId: string;
  title: string | null;
  responseCount: number;
  models: string[];
  avgLatencyMs: number;
  inputTokens: number;
  outputTokens: number;
  successRate: number;
  estimatedCostsByCurrency: Record<string, number>;
}

export interface ResponseAnalytics {
  responseId: string;
  userId: string;
  sessionId: string;
  createdAt: string;
  configuredModelId: string;
  modelId: string;
  modelDisplayName: string;
  protocol: string;
  connectionId: string | null;
  connectionLabel: string | null;
  status: "complete" | "error";
  latencyMs: number;
  inputTokens: number | null;
  outputTokens: number | null;
  estimatedCost: { amount: number; currency: string } | null;
  clientIp: string | null;
  namedLikeCount: number;
  anonymousLikeCount: number;
}

export interface AnalyticsTimePoint {
  bucket: string;
  responseCount: number;
  avgLatencyMs: number;
  successRate: number;
  inputTokens: number;
  outputTokens: number;
}

export interface PublicModelAnalytics {
  protocol: string;
  modelId: string;
  responseCount: number;
  successRate: number;
  avgLatencyMs: number;
  p95LatencyMs: number;
  inputTokens: number;
  outputTokens: number;
  namedLikeCount: number;
  anonymousLikeCount: number;
}

export interface ShareLink {
  token: string;
  shareUrl: string;
  scope: "authenticated" | "public";
  createdAt: string;
  revokedAt: string | null;
}

export interface SharedResponse {
  responseId: string;
  modelDisplayName: string;
  status: "complete" | "error";
  responseText: string | null;
  reasoningText: string | null;
  errorMessage: string | null;
  inputTokens: number | null;
  outputTokens: number | null;
  cacheReadTokens: number | null;
  cacheWriteTokens: number | null;
  latencyMs: number;
  namedLikeCount: number;
  likedByMe: boolean;
  anonymousLikeCount: number;
  likedByThisVisitor: boolean;
}

export interface SharedSession {
  title: string | null;
  scope: "authenticated" | "public";
  canImport: boolean;
  turns: Array<{
    sequenceNum: number;
    promptText: string;
    attachments?: MediaReference[];
    responses: SharedResponse[];
  }>;
}

// ---------------------------------------------------------------------------
// API v2 — admin control panel
// ---------------------------------------------------------------------------

export interface MeResponse {
  id: string;
  email: string;
  displayName: string | null;
  emailVerified: boolean;
  emailVerificationStatus: "verified" | "pending" | "unverified";
  isAdmin: boolean;
  isActive: boolean;
}

export interface PasswordChangeResponse {
  status: "password_updated";
  token: string;
  expiresAt: string;
}

export interface AdminUser {
  id: string;
  email: string;
  emailVerified: boolean;
  isActive: boolean;
  isDisabled: boolean;
  isAdmin: boolean;
  suspectedTest: boolean;
  createdAt: string;
}

export interface BuiltinConnection {
  id: string;
  protocol: string;
  label: string | null;
  baseUrl: string;
  hasKey: boolean;
  modelCount: number;
  allocatedUserCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface BuiltinModel {
  id: string;
  connectionId: string;
  modelId: string;
  displayName: string;
  isEnabled: boolean;
  sortOrder: number;
  inputPricePerMtok: number | null;
  outputPricePerMtok: number | null;
  priceCurrency: string | null;
  capabilityOverrides: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
}

export interface ConnectionAllocationView {
  userId: string;
  email: string;
  createdAt: string;
}

// ---------------------------------------------------------------------------
// API v2 — protocol, connection, configured model, and chat
// ---------------------------------------------------------------------------

export interface ProtocolDefinitionV2 {
  id: string;
  displayName: string;
  defaultBaseUrl: string | null;
  capabilities: CapabilityMatrix;
}

export interface CatalogueEntryV2 {
  protocol: string;
  providerLabel: string;
  modelId: string;
  displayName: string;
  suggestedBaseUrl: string;
  capabilityOverrides: Record<string, unknown>;
  customParams: Record<string, unknown>;
}

export interface ConnectionV2 {
  id: string;
  protocol: string;
  label: string | null;
  baseUrl: string;
  hasKey: boolean;
  modelCount: number;
  builtin: boolean;
  readOnly: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AddConnectionRequestV2 {
  protocol: string;
  label?: string;
  baseUrl: string;
  apiKey: string;
}

export interface PatchConnectionRequestV2 {
  label?: string;
  baseUrl?: string;
}

export interface ConfiguredModelV2 {
  id: string;
  connectionId: string;
  connectionLabel: string | null;
  protocol: string;
  baseUrl: string;
  modelId: string;
  displayName: string;
  builtin: boolean;
  capabilityOverrides: Record<string, unknown>;
  capabilityMatrix: CapabilityMatrix;
  customParams: Record<string, unknown>;
  isEnabled: boolean;
  sortOrder: number;
  inputPricePerMtok: number | null;
  outputPricePerMtok: number | null;
  priceCurrency: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AddConfiguredModelRequestV2 {
  connectionId: string;
  modelId: string;
  displayName: string;
  capabilityOverrides?: Record<string, unknown>;
  customParams?: Record<string, unknown>;
  isEnabled?: boolean;
  inputPricePerMtok?: number | null;
  outputPricePerMtok?: number | null;
  priceCurrency?: string | null;
}

export interface PatchConfiguredModelRequestV2 {
  displayName?: string;
  isEnabled?: boolean;
  capabilityOverrides?: Record<string, unknown>;
  customParams?: Record<string, unknown>;
  sortOrder?: number;
  inputPricePerMtok?: number | null;
  outputPricePerMtok?: number | null;
  priceCurrency?: string | null;
}

export interface UserPreferencesV2 {
  lastSelectedConfiguredModelId: string | null;
  themePreference: string;
  sidebarCollapsed: boolean;
}

export interface UpdatePreferencesRequestV2 {
  lastSelectedConfiguredModelId?: string | null;
  themePreference?: string;
  sidebarCollapsed?: boolean;
}

export interface ChatSessionV2 {
  id: string;
  title: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ProviderResponseV2 {
  responseId: string;
  configuredModelId: string;
  modelId: string;
  modelDisplayName: string;
  protocol: string;
  connectionLabel: string | null;
  status: "complete" | "error";
  responseText: string | null;
  reasoningText: string | null;
  errorMessage: string | null;
  inputTokens: number | null;
  outputTokens: number | null;
  cacheReadTokens: number | null;
  cacheWriteTokens: number | null;
  latencyMs: number;
  likeCount: number;
  likedByMe: boolean;
  anonymousLikeCount: number;
  toolCalls?: ToolCallV2[];
}

/** A persisted tool invocation on a historical response (feature 009). */
export interface ToolCallV2 {
  toolName: string;
  status: string;
  arguments: Record<string, unknown>;
  result: Record<string, unknown> | null;
  error: string | null;
}

export interface ChatTurnV2 {
  id: string;
  sequenceNum: number;
  promptText: string;
  selectedModelIds: string[];
  selectedConfiguredModelIds: string[];
  attachments?: MediaReference[];
  responses: ProviderResponseV2[];
  createdAt: string;
}

export interface GetSessionResponseV2 {
  id: string;
  title: string | null;
  turns: ChatTurnV2[];
}

/** A media reference attached on submit (feature 007): opaque id + display order, both strings. */
export interface SubmitAttachmentRef {
  media_id: string;
  order: string;
}

export interface SubmitTurnRequestV2 {
  promptText: string;
  selectedConfiguredModelIds: string[];
  clientRequestId?: string;
  attachments?: SubmitAttachmentRef[];
}

type ModelSseIdentity = {
  configuredModelId: string;
  modelId: string;
};

/** Lifecycle of a tool invocation surfaced to the UI (feature 009). Mirrors the backend status values. */
export type ToolCallStatus = "pending" | "running" | "success" | "failed" | "timeout";

/** Accumulated per-model view of one tool call across its tool_call/tool_status/tool_result events. */
export interface ToolCallState {
  callId: string;
  toolName: string;
  status: ToolCallStatus;
  arguments?: Record<string, unknown>;
  result?: Record<string, unknown> | null;
  error?: string | null;
}

export type SseEventV2 =
  | { event: "turn_created"; turnId: string; sequenceNum: number }
  | (ModelSseIdentity & { event: "capability_notice"; notice: string })
  | (ModelSseIdentity & { event: "token"; delta: string })
  | (ModelSseIdentity & { event: "reasoning"; delta: string })
  | (ModelSseIdentity & {
      event: "model_complete";
      inputTokens: number;
      outputTokens: number;
      cacheReadTokens: number | null;
      cacheWriteTokens: number | null;
      latencyMs: number;
      responseId: string;
    })
  | (ModelSseIdentity & { event: "model_error"; error: string; responseId: string })
  | (ModelSseIdentity & {
      event: "tool_call";
      callId: string;
      toolName: string;
      arguments: Record<string, unknown>;
    })
  | (ModelSseIdentity & { event: "tool_status"; callId: string; toolName: string; status: string })
  | (ModelSseIdentity & {
      event: "tool_result";
      callId: string;
      toolName: string;
      status: string;
      result: Record<string, unknown> | null;
      error: string | null;
    })
  | { event: "all_complete" };
