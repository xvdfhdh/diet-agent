import { createContext, useContext, useEffect, useRef, useState, type PropsWithChildren } from 'react'
import { api } from './api'
import { useAuth } from './AuthContext'
import type { AgentActionPreview, AgentActivity, ChatExecution, ChatResponse, Meal, RecommendationMode, SessionMessage, SourceMode } from '../types'

export type ChatMessage = {
  id: string
  role: 'user' | 'assistant'
  text: string
  meals?: Meal[]
  traceId?: string
  status?: string
  streaming?: boolean
  recommendationMode?: RecommendationMode
  activities?: AgentActivity[]
  execution?: ChatExecution
  actionPreview?: AgentActionPreview
  actionStatus?: 'CONFIRMED' | 'CANCELLED'
}

type ChatNotice = { message: string; tone: 'success' | 'error' }
type ChatState = {
  mode: SourceMode
  smartMode: boolean
  sessionId?: string
  messages: ChatMessage[]
  loading: boolean
  notice: ChatNotice
  dataRevision: number
  mutationRevision: number
  conversationRevision: number
}
type ChatSessionValue = ChatState & {
  setMode: (mode: SourceMode) => void
  toggleSmartMode: () => void
  send: (message: string) => void
  clear: () => void
  restore: (sessionId: string, sourceMode: SourceMode, messages: SessionMessage[]) => void
  confirmAction: (messageId: string, actionId: string) => Promise<void>
  cancelAction: (messageId: string, actionId: string) => Promise<void>
  clearNotice: () => void
}

const EMPTY_NOTICE: ChatNotice = { message: '', tone: 'success' }
const initialState: ChatState = { mode: 'PUBLIC', smartMode: false, messages: [], loading: false, notice: EMPTY_NOTICE, dataRevision: 0, mutationRevision: 0, conversationRevision: 0 }
const ChatSessionContext = createContext<ChatSessionValue | null>(null)

export function ChatSessionProvider({ children }: PropsWithChildren) {
  const { user } = useAuth()
  const [state, setState] = useState<ChatState>(initialState)
  const stateRef = useRef(state)
  const ownerRef = useRef<number | undefined>(undefined)
  const controllerRef = useRef<AbortController | undefined>(undefined)
  const timerRef = useRef<number | undefined>(undefined)
  stateRef.current = state

  useEffect(() => {
    controllerRef.current?.abort()
    if (!user) {
      ownerRef.current = undefined
      setState(initialState)
      return
    }
    ownerRef.current = user.id
    setState(loadState(user.id))
  }, [user?.id])

  useEffect(() => {
    if (!user || ownerRef.current !== user.id) return
    window.clearTimeout(timerRef.current)
    timerRef.current = window.setTimeout(() => saveState(user.id, state), 120)
    return () => window.clearTimeout(timerRef.current)
  }, [user?.id, state])

  useEffect(() => () => controllerRef.current?.abort(), [])

  function setMode(mode: SourceMode) {
    if (!stateRef.current.loading) setState((current) => ({ ...current, mode }))
  }

  function toggleSmartMode() {
    if (!stateRef.current.loading) setState((current) => ({ ...current, smartMode: !current.smartMode }))
  }

  function clear() {
    controllerRef.current?.abort()
    setState((current) => ({ ...initialState, mode: current.mode, smartMode: current.smartMode }))
  }

  function restore(sessionId: string, sourceMode: SourceMode, messages: SessionMessage[]) {
    if (stateRef.current.loading) return
    setState((current) => ({ ...current, sessionId, mode: sourceMode, notice: EMPTY_NOTICE,
      messages: messages.map((message) => ({ id: `history-${message.id}`, role: message.role,
        text: message.content, traceId: message.traceId, streaming: false })) }))
  }

  function send(rawMessage: string) {
    const text = rawMessage.trim()
    const snapshot = stateRef.current
    if (!text || snapshot.loading) return
    const requestId = crypto.randomUUID()
    const assistantId = `${requestId}-assistant`
    setState((current) => ({ ...current, loading: true, notice: EMPTY_NOTICE, messages: [...current.messages,
      { id: requestId, role: 'user', text },
      { id: assistantId, role: 'assistant', text: '', status: '正在连接推荐服务…', streaming: true,
        recommendationMode: snapshot.smartMode ? 'AGENT' : 'STANDARD', activities: [] },
    ] }))
    controllerRef.current?.abort()
    const controller = new AbortController()
    controllerRef.current = controller
    void runStream(snapshot, text, assistantId, controller)
  }

  async function runStream(snapshot: ChatState, text: string, assistantId: string, controller: AbortController) {
    let pendingText = ''
    let pumpTimer: number | undefined
    let completed: ChatResponse | undefined
    let receivedComplete = false

    const patchAssistant = (patch: (message: ChatMessage) => ChatMessage) => setState((current) => ({ ...current,
      messages: current.messages.map((message) => message.id === assistantId ? patch(message) : message) }))

    const finish = () => {
      if (!completed || pendingText.length > 0 || controller.signal.aborted) return
      const response = completed
      patchAssistant((message) => ({ ...message, text: response.clarifyQuestion || response.speechText,
        meals: response.displayBlocks, traceId: response.traceId, status: undefined, streaming: false,
        execution: response.execution, actionPreview: response.actionPreview,
        activities: response.execution?.activities || message.activities }))
      const writeSkipped = response.execution?.activities.some((activity) => activity.status === 'NOT_COMMITTED')
      setState((current) => ({ ...current, loading: false, sessionId: response.sessionId,
        conversationRevision: current.conversationRevision + 1,
        dataRevision: current.dataRevision + (response.displayBlocks.length > 0 ? 1 : 0),
        mutationRevision: current.mutationRevision + (response.execution?.mutationsCommitted ? 1 : 0),
        notice: response.execution?.fallbackOccurred ? {
          message: writeSkipped
            ? '智能模式暂不可用，已用稳定模式完成推荐；本次计划或清单操作未执行。'
            : '智能模式暂不可用，已使用稳定模式完成。',
          tone: writeSkipped ? 'error' : 'success',
        } : current.notice }))
    }

    const pump = () => {
      if (controller.signal.aborted) return
      if (pendingText.length > 0) {
        const count = Math.min(3, pendingText.length)
        const next = pendingText.slice(0, count)
        pendingText = pendingText.slice(count)
        patchAssistant((message) => ({ ...message, text: message.text + next, status: undefined }))
        pumpTimer = window.setTimeout(pump, 28)
      } else {
        pumpTimer = undefined
        finish()
      }
    }

    const ensurePump = () => { if (pumpTimer == null) pumpTimer = window.setTimeout(pump, 20) }

    try {
      await api.chatStream({ sessionId: snapshot.sessionId, message: text, sourceMode: snapshot.mode,
        recommendationMode: snapshot.smartMode ? 'AGENT' : 'STANDARD',
        sourceStrategy: snapshot.smartMode ? 'UNIFIED' : 'SELECTED_ONLY' }, (event) => {
        if (event.type === 'status') patchAssistant((message) => ({ ...message, status: event.text }))
        else if (event.type === 'activity' && event.activity) patchAssistant((message) => ({ ...message,
          activities: [...(message.activities || []), event.activity!] }))
        else if (event.type === 'delta' && event.text) { pendingText += event.text; ensurePump() }
        else if (event.type === 'complete' && event.response) {
          receivedComplete = true
          completed = event.response
          if (!pendingText && !pumpTimer) finish()
          else ensurePump()
        } else if (event.type === 'error') throw new Error(event.text || '生成推荐失败')
      }, controller.signal)
      if (!receivedComplete) throw new Error('流式连接提前结束，请重试')
    } catch (error) {
      window.clearTimeout(pumpTimer)
      if (!controller.signal.aborted) {
        const message = error instanceof Error ? error.message : '发送失败，请稍后再试'
        patchAssistant((item) => ({ ...item, text: `抱歉，${message}`, status: undefined, streaming: false }))
        setState((current) => ({ ...current, loading: false, notice: { message, tone: 'error' } }))
      }
    }
  }

  async function confirmAction(messageId: string, actionId: string) {
    await resolveAction(messageId, actionId, 'confirm')
  }

  async function cancelAction(messageId: string, actionId: string) {
    await resolveAction(messageId, actionId, 'cancel')
  }

  async function resolveAction(messageId: string, actionId: string, operation: 'confirm' | 'cancel') {
    if (stateRef.current.loading) return
    setState((current) => ({ ...current, loading: true, notice: EMPTY_NOTICE }))
    try {
      const response = operation === 'confirm'
        ? await api.confirmAgentAction(actionId)
        : await api.cancelAgentAction(actionId)
      const committed = response.status === 'CONFIRMED'
      setState((current) => ({ ...current, loading: false,
        messages: current.messages.map((message) => message.id === messageId
          ? { ...message, actionStatus: response.status === 'CONFIRMED' ? 'CONFIRMED' : 'CANCELLED' }
          : message),
        mutationRevision: current.mutationRevision + (committed ? 1 : 0),
        dataRevision: current.dataRevision + (committed ? 1 : 0),
        notice: { message: response.message, tone: 'success' } }))
    } catch (error) {
      const message = error instanceof Error ? error.message : '操作失败，请重试'
      setState((current) => ({ ...current, loading: false, notice: { message, tone: 'error' } }))
    }
  }

  function clearNotice() { setState((current) => ({ ...current, notice: EMPTY_NOTICE })) }

  return <ChatSessionContext.Provider value={{ ...state, setMode, toggleSmartMode, send, clear, restore,
    confirmAction, cancelAction, clearNotice }}>
    {children}
  </ChatSessionContext.Provider>
}

function storageKey(userId: number) { return `diet.chat-session.v1.${userId}` }

function loadState(userId: number): ChatState {
  try {
    const value = JSON.parse(localStorage.getItem(storageKey(userId)) || 'null') as Partial<ChatState> | null
    return value ? { ...initialState, ...value, loading: false,
      messages: (value.messages || []).map((message) => ({ ...message, streaming: false, status: undefined })) } : initialState
  } catch { return initialState }
}

function saveState(userId: number, state: ChatState) {
  try { localStorage.setItem(storageKey(userId), JSON.stringify({ ...state, loading: false, messages: state.messages.slice(-100) })) }
  catch { /* Storage quota or privacy mode must not break chat. */ }
}

export function useChatSession() {
  const context = useContext(ChatSessionContext)
  if (!context) throw new Error('ChatSessionProvider 未初始化')
  return context
}
