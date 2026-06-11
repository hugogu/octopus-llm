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
// Models API — contracts/models-api.md
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

export interface ModelDefinition {
  id: string;
  providerId: string;
  displayName: string;
  capabilityMatrix: CapabilityMatrix;
  isActive: boolean;
  source: "CATALOGUE" | "DISCOVERED" | "CUSTOM";
}

export interface ListModelsResponse {
  models: ModelDefinition[];
}

// ---------------------------------------------------------------------------
// User Config API — contracts/user-config-api.md
// ---------------------------------------------------------------------------

export interface ApiKeyMeta {
  id: string;
  providerId: string;
  label: string | null;
  createdAt: string;
}

export interface AddApiKeyRequest {
  providerId: string;
  apiKey: string;
  label?: string;
}

export interface UserModelConfig {
  id: string;
  modelId: string;
  providerApiKeyId: string | null;
  isEnabled: boolean;
  customParams: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
}

export interface UpsertModelConfigRequest {
  modelId: string;
  providerApiKeyId: string;
  isEnabled?: boolean;
  customParams?: Record<string, unknown>;
}

export interface PatchModelConfigRequest {
  providerApiKeyId?: string;
  isEnabled?: boolean;
  customParams?: Record<string, unknown>;
}

export interface SyncProviderModelsRequest {
  providerId: string;
  providerApiKeyId?: string;
}

export interface CreateCustomModelRequest {
  providerId: string;
  modelId: string;
  displayName?: string;
  providerApiKeyId: string;
  isEnabled?: boolean;
  customParams?: Record<string, unknown>;
  capabilityMatrix?: CapabilityMatrix;
}

// ---------------------------------------------------------------------------
// Chat API — contracts/chat-api.md
// ---------------------------------------------------------------------------

export interface UserPreferences {
  lastSelectedModelId: string | null;
  themePreference: string;
  sidebarCollapsed: boolean;
}

export interface UpdatePreferencesRequest {
  lastSelectedModelId?: string;
  themePreference?: string;
  sidebarCollapsed?: boolean;
}

export interface ChatSession {
  id: string;
  title: string | null;
  selectedModelId: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateSessionRequest {
  title?: string;
  selectedModelId?: string;
}

export interface CreateSessionResponse {
  id: string;
  title: string | null;
  createdAt: string;
}

export interface ListSessionsResponse {
  sessions: ChatSession[];
  total: number;
}

export interface Attachment {
  type: string;
  data: string;
  mimeType: string;
}

export interface ProviderResponse {
  modelId: string;
  status: "complete" | "error";
  responseText: string | null;
  reasoningText: string | null;
  errorMessage: string | null;
  inputTokens: number | null;
  outputTokens: number | null;
  latencyMs: number;
}

export interface ChatTurn {
  id: string;
  sequenceNum: number;
  promptText: string;
  attachments: Attachment[];
  selectedModelIds: string[];
  responses: ProviderResponse[];
  createdAt: string;
}

export interface GetSessionResponse {
  id: string;
  title: string | null;
  turns: ChatTurn[];
}

export interface SubmitTurnRequest {
  promptText: string;
  selectedModelIds: string[];
  clientRequestId?: string;
  attachments?: Attachment[];
}

// ---------------------------------------------------------------------------
// SSE event types (chat streaming)
// ---------------------------------------------------------------------------

export type SseEvent =
  | { event: "turn_created"; turnId: string; sequenceNum: number }
  | { event: "capability_notice"; modelId: string; notice: string }
  | { event: "token"; modelId: string; delta: string }
  | { event: "reasoning"; modelId: string; delta: string }
  | { event: "model_complete"; modelId: string; inputTokens: number; outputTokens: number; latencyMs: number }
  | { event: "model_error"; modelId: string; error: string }
  | { event: "all_complete" };

// ---------------------------------------------------------------------------
// Error response
// ---------------------------------------------------------------------------

export interface ApiError {
  code: string;
  message: string;
  details?: Record<string, unknown>;
}
