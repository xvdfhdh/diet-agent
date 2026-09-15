export type SourceMode = 'PERSONAL' | 'PUBLIC'

export interface Meal {
  id: number
  sourceType: SourceMode
  name: string
  imageUrl?: string
  mealTime: string[]
  mood: string[]
  scene: string[]
  healthGoal: string[]
  cuisine: string[]
  taste: string[]
  convenience: string[]
  matchScore: number
}

export interface SlotBundle {
  mealTime: string[]
  mood: string[]
  scene: string[]
  healthGoal: string[]
  cuisine: string[]
  taste: string[]
  convenience: string[]
}

export interface RecommendationHistory {
  id: number
  sessionId: string
  traceId: string
  sourceMode: SourceMode
  userInput: string
  slots: SlotBundle
  speechText: string
  meals: Meal[]
  createdAt: string
}

export interface UserMemory {
  id: number
  type: 'SLOT_PREFERENCE' | 'MEAL_PREFERENCE'
  key: string
  value: string
  strength: number
  evidenceCount: number
  source: string
  updatedAt: string
}

export interface FavoriteMeal {
  meal: Meal
  createdAt?: string
}

export interface UserPreferenceProfile {
  healthGoal: string[]
  cuisine: string[]
  taste: string[]
  convenience: string[]
}

export type MealDraft = Omit<Meal, 'id' | 'sourceType' | 'matchScore'>

export interface ChatResponse {
  sessionId: string
  traceId?: string
  responseType: 'ANSWER' | 'CLARIFY'
  speechText: string
  displayBlocks: Meal[]
  nextAction?: string
  clarifyQuestion?: string
  missingSlots: string[]
}

export interface ChatStreamEvent {
  type: 'status' | 'delta' | 'complete' | 'error'
  text?: string
  response?: ChatResponse
}

export interface Trace {
  id: number
  traceId: string
  sessionId: string
  status: string
  eventCount: number
  durationMs?: number
  errorMessage?: string
  traceJson: string
  expectedIntent?: string
  expectedSlots?: string
  expectedClarifyAction?: string
  labelNote?: string
  createdAt: string
}

export interface ProviderTemplate {
  id: string
  name: string
  baseUrl: string
  endpointPath: string
  mainModel: string
  lightModel: string
  helpUrl: string
}

export interface ModelConfig {
  providerId: string
  displayName: string
  baseUrl: string
  endpointPath: string
  mainModel: string
  lightModel: string
  apiKeyConfigured: boolean
  apiKeyMasked: string
  templates: ProviderTemplate[]
}

export interface ModelConfigDraft {
  providerId: string
  displayName: string
  baseUrl: string
  endpointPath: string
  mainModel: string
  lightModel: string
  apiKey: string
}

export interface EvaluationReport {
  startAt: string
  endAt: string
  totalTraces: number
  labeledTraces: number
  avgScore?: number
  metricAverages: Record<string, number>
  traceResults: Array<{ traceId: string; score?: number; metrics: Record<string, number> }>
}
