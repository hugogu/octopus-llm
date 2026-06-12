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
  capabilityOverrides: Record<string, unknown>;
  capabilityMatrix: CapabilityMatrix;
  customParams: Record<string, unknown>;
  isEnabled: boolean;
  sortOrder: number;
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
}

export interface PatchConfiguredModelRequestV2 {
  displayName?: string;
  isEnabled?: boolean;
  capabilityOverrides?: Record<string, unknown>;
  customParams?: Record<string, unknown>;
  sortOrder?: number;
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
  latencyMs: number;
}

export interface ChatTurnV2 {
  id: string;
  sequenceNum: number;
  promptText: string;
  selectedModelIds: string[];
  selectedConfiguredModelIds: string[];
  responses: ProviderResponseV2[];
  createdAt: string;
}

export interface GetSessionResponseV2 {
  id: string;
  title: string | null;
  turns: ChatTurnV2[];
}

export interface SubmitTurnRequestV2 {
  promptText: string;
  selectedConfiguredModelIds: string[];
  clientRequestId?: string;
  attachments?: Attachment[];
}

type ModelSseIdentity = {
  configuredModelId: string;
  modelId: string;
};

export type SseEventV2 =
  | { event: "turn_created"; turnId: string; sequenceNum: number }
  | (ModelSseIdentity & { event: "capability_notice"; notice: string })
  | (ModelSseIdentity & { event: "token"; delta: string })
  | (ModelSseIdentity & { event: "reasoning"; delta: string })
  | (ModelSseIdentity & {
      event: "model_complete";
      inputTokens: number;
      outputTokens: number;
      latencyMs: number;
    })
  | (ModelSseIdentity & { event: "model_error"; error: string })
  | { event: "all_complete" };
