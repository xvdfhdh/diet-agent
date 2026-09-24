export type SourceMode = 'PERSONAL' | 'PUBLIC'
export type RecommendationMode = 'STANDARD' | 'AGENT'
export type SourceStrategy = 'SELECTED_ONLY' | 'UNIFIED'
export type AgentTaskType = 'RECOMMEND' | 'PLAN' | 'SHOPPING' | 'CHECKIN' | 'FAVORITE' | 'PREFERENCE' | 'GENERAL'

export interface AgentActivity {
  name: string
  status: 'COMPLETED' | 'STAGED' | 'NOT_COMMITTED' | string
  detail?: string
}

export interface ChatExecution {
  requestedMode: RecommendationMode
  actualMode: RecommendationMode
  fallbackOccurred: boolean
  fallbackCode?: string
  activities: AgentActivity[]
  mutationsCommitted: boolean
  taskType?: AgentTaskType
  sourceStrategy?: SourceStrategy
  repairCount?: number
}

export interface AgentActionChange {
  domain: string
  operation: string
  description: string
}

export interface AgentActionPreview {
  id: string
  summary: string
  changes: AgentActionChange[]
  expiresAt: string
  requiresConfirmation: boolean
}

export interface AgentActionResponse {
  id: string
  status: 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'EXPIRED'
  message: string
  affectedDomains: string[]
}

export interface AuthUser {
  id: number
  username: string
  role: 'USER' | 'ADMIN'
}

export interface AuthResponse {
  token: string
  user: AuthUser
}

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
  acquisitionMode?: 'COOK' | 'EAT_OUT' | 'BOTH'
  prepMinutes?: number
  difficulty?: string
  priceMin?: number
  priceMax?: number
  defaultServings?: number
  ingredients?: MealIngredient[]
  steps?: string[]
  dineOutTips?: string
  substitutes?: string[]
  nutrition?: NutritionSummary
  matchScore: number
}

export interface MealIngredient { name: string; category: string; quantity?: number; unit?: string }
export interface NutritionSummary { calories?: number; protein?: number; fat?: number; carbs?: number }
export type MealPeriod = 'BREAKFAST' | 'LUNCH' | 'DINNER' | 'SNACK'
export type AcquisitionMode = 'COOK' | 'EAT_OUT' | 'BOTH'

export interface MealCheckin {
  id: number
  actualMealId?: number
  actualMealName?: string
  rating?: number
  satiety?: number
  reasonCode?: string
  note?: string
  actualSpent?: number
  eatenAt: string
}

export interface PlanItem {
  id: number
  planDate: string
  mealPeriod: MealPeriod
  mealId: number
  meal: Meal
  acquisitionMode: Exclude<AcquisitionMode, 'BOTH'>
  servings: number
  status: 'PLANNED' | 'COMPLETED' | 'SKIPPED' | 'REPLACED'
  checkin?: MealCheckin
}

export interface WeeklySummary {
  weekStart: string
  plannedCount: number
  completedCount: number
  skippedCount: number
  completionRate: number
  cookCount: number
  eatOutCount: number
  topTastes: string[]
  topCuisines: string[]
  topHealthGoals: string[]
  mostCompletedMeals: string[]
  mostSkippedMeals: string[]
  estimatedCost: number
  actualCost: number
}

export interface ShoppingItem {
  id: number
  name: string
  category: string
  quantity?: number
  unit?: string
  sourceMealIds: number[]
  manual: boolean
  completed: boolean
}

export interface ShoppingList {
  id: number
  weekStart: string
  syncVersion: number
  status: string
  syncedAt?: string
  needsSync: boolean
  items: ShoppingItem[]
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
  sourceStrategy?: SourceStrategy
  userInput: string
  slots: SlotBundle
  speechText: string
  meals: Meal[]
  createdAt: string
}

export interface SessionSummary {
  id: string
  phase: string
  sourceMode: SourceMode
  preview: string
  messageCount: number
  createdAt: string
  updatedAt: string
}

export interface SessionMessage {
  id: number
  role: 'user' | 'assistant'
  content: string
  intent?: string
  traceId?: string
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

export interface MealBulkRequest {
  creates: MealDraft[]
  updates: Array<{ id: number; meal: MealDraft }>
  deleteIds: number[]
}

export interface MealBulkResponse {
  created: number
  updated: number
  deleted: number
}

export interface ChatResponse {
  sessionId: string
  traceId?: string
  responseType: 'ANSWER' | 'CLARIFY'
  speechText: string
  displayBlocks: Meal[]
  nextAction?: string
  clarifyQuestion?: string
  missingSlots: string[]
  execution?: ChatExecution
  actionPreview?: AgentActionPreview
}

export interface ChatStreamEvent {
  type: 'status' | 'activity' | 'delta' | 'complete' | 'error'
  text?: string
  response?: ChatResponse
  activity?: AgentActivity
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
  requestedMode?: RecommendationMode
  actualMode?: RecommendationMode
  fallbackCode?: string
  toolCallCount?: number
  agentTaskType?: AgentTaskType
  sourceStrategy?: SourceStrategy
  repairCount?: number
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
