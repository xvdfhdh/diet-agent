import type { ChatResponse, ChatStreamEvent, EvaluationReport, FavoriteMeal, Meal, MealDraft, ModelConfig, ModelConfigDraft, RecommendationHistory, Trace, UserMemory, UserPreferenceProfile } from '../types'

const API_BASE = (import.meta.env.VITE_API_BASE_URL || '/api/v1/diet').replace(/\/$/, '')

function userId() {
  return localStorage.getItem('diet.userId') || '1'
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      'X-User-Id': userId(),
      ...init.headers,
    },
  })
  if (!response.ok) {
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
  chat: (payload: { sessionId?: string; message: string; sourceMode: 'PERSONAL' | 'PUBLIC' }) =>
    request<ChatResponse>('/chat', { method: 'POST', body: JSON.stringify({ ...payload, context: {} }) }),
  chatStream: async (
    payload: { sessionId?: string; message: string; sourceMode: 'PERSONAL' | 'PUBLIC' },
    onEvent: (event: ChatStreamEvent) => void,
    signal?: AbortSignal,
  ) => {
    const response = await fetch(`${API_BASE}/chat/stream`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-User-Id': userId(), Accept: 'text/event-stream' },
      body: JSON.stringify({ ...payload, context: {} }),
      signal,
    })
    if (!response.ok) throw new Error((await response.text()) || `请求失败（${response.status}）`)
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
  createMeal: (payload: MealDraft) => request<Meal>('/meals/personal', { method: 'POST', body: JSON.stringify(payload) }),
  updateMeal: (id: number, payload: MealDraft) => request<Meal>(`/meals/personal/${id}`, { method: 'PUT', body: JSON.stringify(payload) }),
  deleteMeal: (id: number) => request<void>(`/meals/personal/${id}`, { method: 'DELETE' }),
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
}
