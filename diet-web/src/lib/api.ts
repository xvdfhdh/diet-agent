import type { ChatResponse, EvaluationReport, Meal, MealDraft, ModelConfig, ModelConfigDraft, Trace } from '../types'

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
  meals: (mode: 'personal' | 'public') => request<Meal[]>(`/meals/${mode}`),
  createMeal: (payload: MealDraft) => request<Meal>('/meals/personal', { method: 'POST', body: JSON.stringify(payload) }),
  updateMeal: (id: number, payload: MealDraft) => request<Meal>(`/meals/personal/${id}`, { method: 'PUT', body: JSON.stringify(payload) }),
  deleteMeal: (id: number) => request<void>(`/meals/personal/${id}`, { method: 'DELETE' }),
  slotOptions: () => request<Record<string, string[]>>('/slot-options'),
  feedback: (payload: { sessionId: string; itemId: number; action: string; rating?: number; reason?: string }) =>
    request<void>('/feedback', { method: 'POST', body: JSON.stringify(payload) }),
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
