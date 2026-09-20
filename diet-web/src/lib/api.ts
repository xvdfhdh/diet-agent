import type { AcquisitionMode, AuthResponse, AuthUser, ChatResponse, ChatStreamEvent, EvaluationReport, FavoriteMeal, Meal, MealBulkRequest, MealBulkResponse, MealDraft, MealPeriod, ModelConfig, ModelConfigDraft, PlanItem, RecommendationHistory, ShoppingItem, ShoppingList, Trace, UserMemory, UserPreferenceProfile, WeeklySummary } from '../types'
import { authToken, clearAuthToken } from './authStorage'

const API_BASE = (import.meta.env.VITE_API_BASE_URL || '/api/v1/diet').replace(/\/$/, '')

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = authToken()
  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...init.headers,
    },
  })
  if (!response.ok) {
    if (response.status === 401 && !path.startsWith('/auth/login') && !path.startsWith('/auth/register')) clearAuthToken()
    const body = await response.text()
    let message = body || `请求失败（${response.status}）`
    try {
      const parsed = JSON.parse(body)
      message = parsed.message || parsed.error || message
    } catch { /* response is not JSON */ }
    throw new Error(message)
  }
  if (response.status === 204 || response.headers.get('content-length') === '0') return undefined as T
  return response.json() as Promise<T>
}

export const api = {
  register: (payload: { username: string; password: string }) => request<AuthResponse>('/auth/register', { method: 'POST', body: JSON.stringify(payload) }),
  login: (payload: { username: string; password: string }) => request<AuthResponse>('/auth/login', { method: 'POST', body: JSON.stringify(payload) }),
  me: () => request<AuthUser>('/auth/me'),
  logout: () => request<void>('/auth/logout', { method: 'POST' }),
  chat: (payload: { sessionId?: string; message: string; sourceMode: 'PERSONAL' | 'PUBLIC' }) =>
    request<ChatResponse>('/chat', { method: 'POST', body: JSON.stringify({ ...payload, context: {} }) }),
  chatStream: async (
    payload: { sessionId?: string; message: string; sourceMode: 'PERSONAL' | 'PUBLIC' },
    onEvent: (event: ChatStreamEvent) => void,
    signal?: AbortSignal,
  ) => {
    const response = await fetch(`${API_BASE}/chat/stream`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...(authToken() ? { Authorization: `Bearer ${authToken()}` } : {}), Accept: 'text/event-stream' },
      body: JSON.stringify({ ...payload, context: {} }),
      signal,
    })
    if (!response.ok) {
      if (response.status === 401) clearAuthToken()
      throw new Error((await response.text()) || `请求失败（${response.status}）`)
    }
    if (!response.body) throw new Error('浏览器不支持流式响应')
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    while (true) {
      const { done, value } = await reader.read()
      buffer += decoder.decode(value, { stream: !done }).replace(/\r\n/g, '\n')
      const blocks = buffer.split('\n\n')
      buffer = blocks.pop() || ''
      for (const block of blocks) {
        const data = block.split('\n').filter((line) => line.startsWith('data:')).map((line) => line.slice(5).trimStart()).join('\n')
        if (data) onEvent(JSON.parse(data) as ChatStreamEvent)
      }
      if (done) break
    }
  },
  meals: (mode: 'personal' | 'public') => request<Meal[]>(`/meals/${mode}`),
  meal: (id: number) => request<Meal>(`/meals/${id}`),
  createMeal: (payload: MealDraft) => request<Meal>('/meals/personal', { method: 'POST', body: JSON.stringify(payload) }),
  updateMeal: (id: number, payload: MealDraft) => request<Meal>(`/meals/personal/${id}`, { method: 'PUT', body: JSON.stringify(payload) }),
  deleteMeal: (id: number) => request<void>(`/meals/personal/${id}`, { method: 'DELETE' }),
  completeMeal: (payload: MealDraft) => request<MealDraft>('/meals/ai/complete', { method: 'POST', body: JSON.stringify(payload) }),
  expandPersonalMeals: (payload: { preference?: string; count: number }) => request<Meal[]>('/meals/personal/ai-expand', { method: 'POST', body: JSON.stringify(payload) }),
  createPublicMeal: (payload: MealDraft) => request<Meal>('/meals/public', { method: 'POST', body: JSON.stringify(payload) }),
  updatePublicMeal: (id: number, payload: MealDraft) => request<Meal>(`/meals/public/${id}`, { method: 'PUT', body: JSON.stringify(payload) }),
  deletePublicMeal: (id: number) => request<void>(`/meals/public/${id}`, { method: 'DELETE' }),
  bulkPublicMeals: (payload: MealBulkRequest) => request<MealBulkResponse>('/meals/public/batch', { method: 'POST', body: JSON.stringify(payload) }),
  slotOptions: () => request<Record<string, string[]>>('/slot-options'),
  feedback: (payload: { sessionId: string; itemId: number; action: string; rating?: number; reason?: string }) =>
    request<void>('/feedback', { method: 'POST', body: JSON.stringify(payload) }),
  recommendationHistory: (limit = 20, scope: 'today' | 'all' = 'today') =>
    request<RecommendationHistory[]>(`/recommendations/history?limit=${limit}&scope=${scope}`),
  memories: (limit = 12) => request<UserMemory[]>(`/memories?limit=${limit}`),
  preferences: () => request<UserPreferenceProfile>('/memories/preferences'),
  savePreferences: (payload: UserPreferenceProfile) => request<UserPreferenceProfile>('/memories/preferences', { method: 'PUT', body: JSON.stringify(payload) }),
  favorites: (limit = 50) => request<FavoriteMeal[]>(`/favorites?limit=${limit}`),
  addFavorite: (mealId: number, sessionId?: string) => request<FavoriteMeal>(`/favorites/${mealId}`, { method: 'POST', body: JSON.stringify({ sessionId }) }),
  removeFavorite: (mealId: number) => request<void>(`/favorites/${mealId}`, { method: 'DELETE' }),
  traces: (days = 7, onlyUnlabeled = false) => {
    const end = new Date()
    const start = new Date(end.getTime() - days * 86_400_000)
    const params = new URLSearchParams({ startAt: start.toISOString().replace('Z', ''), endAt: end.toISOString().replace('Z', ''), onlyUnlabeled: String(onlyUnlabeled), limit: '100' })
    return request<Trace[]>(`/debug/traces?${params}`)
  },
  trace: (id: string) => request<Trace>(`/debug/traces/${id}`),
  labelTrace: (id: string, payload: { expectedIntent?: string; expectedClarifyAction?: string; labelNote?: string }) =>
    request<void>(`/debug/traces/${id}/label`, { method: 'PUT', body: JSON.stringify(payload) }),
  evaluate: (payload: { startAt: string; endAt: string; includeLlmJudge: boolean; limit: number }) =>
    request<EvaluationReport>('/evaluations', { method: 'POST', body: JSON.stringify(payload) }),
  modelConfig: () => request<ModelConfig>('/model-config'),
  saveModelConfig: (payload: ModelConfigDraft) => request<ModelConfig>('/model-config', { method: 'PUT', body: JSON.stringify(payload) }),
  testModelConfig: (payload: ModelConfigDraft) => request<{ success: boolean; latencyMs: number; message: string }>('/model-config/test', { method: 'POST', body: JSON.stringify(payload) }),
  plans: (weekStart: string) => request<PlanItem[]>(`/plans?weekStart=${weekStart}`),
  addPlanItem: (payload: { planDate: string; mealPeriod: MealPeriod; mealId: number; acquisitionMode?: AcquisitionMode; servings?: number }) =>
    request<PlanItem>('/plans/items', { method: 'POST', body: JSON.stringify(payload) }),
  updatePlanItem: (id: number, payload: Partial<{ planDate: string; mealPeriod: MealPeriod; mealId: number; acquisitionMode: AcquisitionMode; servings: number }>) =>
    request<PlanItem>(`/plans/items/${id}`, { method: 'PUT', body: JSON.stringify(payload) }),
  deletePlanItem: (id: number) => request<void>(`/plans/items/${id}`, { method: 'DELETE' }),
  generatePlan: (weekStart: string, preferredMode?: AcquisitionMode) => request<PlanItem[]>('/plans/generate', { method: 'POST', body: JSON.stringify({ weekStart, preferredMode }) }),
  replacePlanItem: (id: number) => request<PlanItem>(`/plans/items/${id}/replace`, { method: 'POST' }),
  checkInPlanItem: (id: number, payload: { skipped?: boolean; actualMealId?: number; actualMealName?: string; rating?: number; satiety?: number; reasonCode?: string; note?: string; actualSpent?: number }) =>
    request<PlanItem>(`/plans/items/${id}/check-in`, { method: 'POST', body: JSON.stringify(payload) }),
  weeklySummary: (weekStart: string) => request<WeeklySummary>(`/plans/weekly-summary?weekStart=${weekStart}`),
  shoppingList: (weekStart: string) => request<ShoppingList>(`/shopping-lists?weekStart=${weekStart}`),
  syncShoppingList: (weekStart: string) => request<ShoppingList>(`/shopping-lists/sync?weekStart=${weekStart}`, { method: 'POST' }),
  addShoppingItem: (weekStart: string, payload: { name: string; category?: string; quantity?: number; unit?: string; completed?: boolean }) =>
    request<ShoppingItem>(`/shopping-lists/items?weekStart=${weekStart}`, { method: 'POST', body: JSON.stringify(payload) }),
  updateShoppingItem: (id: number, payload: Partial<{ name: string; category: string; quantity: number; unit: string; completed: boolean }>) =>
    request<ShoppingItem>(`/shopping-lists/items/${id}`, { method: 'PUT', body: JSON.stringify(payload) }),
  deleteShoppingItem: (id: number) => request<void>(`/shopping-lists/items/${id}`, { method: 'DELETE' }),
}
