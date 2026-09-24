// @vitest-environment jsdom
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { useState } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { ChatPage } from '../pages/ChatPage'
import { ChatSessionProvider } from '../lib/ChatSessionContext'

const mocked = vi.hoisted(() => ({
  meals: vi.fn(), recommendationHistory: vi.fn(), memories: vi.fn(), favorites: vi.fn(), plans: vi.fn(),
  chatStream: vi.fn(), feedback: vi.fn(), removeFavorite: vi.fn(), addFavorite: vi.fn(),
  replacePlanItem: vi.fn(), checkInPlanItem: vi.fn(),
  sessions: vi.fn(), sessionMessages: vi.fn(),
  confirmAgentAction: vi.fn(), cancelAgentAction: vi.fn(),
}))
vi.mock('../lib/api', () => ({ api: mocked }))
vi.mock('../lib/AuthContext', () => ({ useAuth: () => ({ user: { id: 7, username: 'tester', role: 'USER' } }) }))

beforeEach(() => {
  vi.clearAllMocks()
  localStorage.clear()
  mocked.meals.mockResolvedValue([])
  mocked.recommendationHistory.mockResolvedValue([])
  mocked.memories.mockResolvedValue([])
  mocked.favorites.mockResolvedValue([])
  mocked.plans.mockResolvedValue([])
  mocked.sessions.mockResolvedValue([])
  mocked.sessionMessages.mockResolvedValue([])
  mocked.confirmAgentAction.mockResolvedValue({ id: 'act-1', status: 'CONFIRMED', message: '已确认并完成操作', affectedDomains: ['PLAN'] })
  mocked.cancelAgentAction.mockResolvedValue({ id: 'act-1', status: 'CANCELLED', message: '已取消', affectedDomains: ['PLAN'] })
  mocked.chatStream.mockImplementation(async (_payload, onEvent) => {
    onEvent({ type: 'activity', activity: { name: '读取长期偏好', status: 'COMPLETED', detail: '已应用偏好' } })
    onEvent({ type: 'complete', response: {
      sessionId: 'session-1', traceId: 'trace-1', responseType: 'ANSWER', speechText: '给你推荐一道',
      displayBlocks: [], missingSlots: [], execution: {
        requestedMode: 'AGENT', actualMode: 'STANDARD', fallbackOccurred: true, fallbackCode: 'AGENT_TIMEOUT',
        activities: [{ name: '计划或购物清单操作', status: 'NOT_COMMITTED', detail: '没有执行' }], mutationsCommitted: false,
      },
    } })
  })
})
afterEach(cleanup)

it('智能开关持久化并展示 Agent 降级与未提交操作', async () => {
  renderChat()
  const toggle = screen.getByRole('switch', { name: /智能推荐/ })
  fireEvent.click(toggle)
  expect(toggle.getAttribute('aria-checked')).toBe('true')
  await waitFor(() => expect(JSON.parse(localStorage.getItem('diet.chat-session.v1.7') || '{}').smartMode).toBe(true))

  fireEvent.change(screen.getByLabelText('描述你的餐食需求'), { target: { value: '推荐一道菜并加入明天午餐计划' } })
  fireEvent.click(screen.getByRole('button', { name: '发送' }))

  await waitFor(() => expect(mocked.chatStream).toHaveBeenCalledWith(
    expect.objectContaining({ recommendationMode: 'AGENT', sourceMode: 'PUBLIC', sourceStrategy: 'UNIFIED' }), expect.any(Function), expect.any(AbortSignal),
  ))
  expect(await screen.findByText(/本次计划或清单操作未执行/)).toBeTruthy()
  expect(screen.getByText('智能 Agent')).toBeTruthy()
  expect(screen.getByText(/智能处理过程（已降级）/)).toBeTruthy()
})

it('智能批量计划展示预览并仅在确认后提交', async () => {
  mocked.chatStream.mockImplementationOnce(async (_payload, onEvent) => {
    onEvent({ type: 'complete', response: {
      sessionId: 'session-1', traceId: 'trace-2', responseType: 'ANSWER', speechText: '已生成今日计划草案',
      displayBlocks: [], missingSlots: [], actionPreview: {
        id: 'act-1', summary: '将昨天三餐复制到今天', expiresAt: '2026-09-23T20:00:00', requiresConfirmation: true,
        changes: [{ domain: 'PLAN', operation: 'UPSERT', description: '今天早餐加入历史早餐' }],
      }, execution: { requestedMode: 'AGENT', actualMode: 'AGENT', fallbackOccurred: false,
        activities: [], mutationsCommitted: false, taskType: 'PLAN', sourceStrategy: 'UNIFIED', repairCount: 0 },
    } })
  })
  renderChat()
  fireEvent.click(screen.getByRole('switch', { name: /智能推荐/ }))
  fireEvent.change(screen.getByLabelText('描述你的餐食需求'), { target: { value: '按照昨天的计划填写今天' } })
  fireEvent.click(screen.getByRole('button', { name: '发送' }))

  expect(await screen.findByText('将昨天三餐复制到今天')).toBeTruthy()
  expect(mocked.confirmAgentAction).not.toHaveBeenCalled()
  fireEvent.click(screen.getByRole('button', { name: '确认执行' }))
  await waitFor(() => expect(mocked.confirmAgentAction).toHaveBeenCalledWith('act-1'))
  expect(await screen.findByText('已执行')).toBeTruthy()
})

it('切换页面期间流式请求继续，返回推荐页后会话仍在', async () => {
  let emit: ((event: any) => void) | undefined
  let finishRequest: (() => void) | undefined
  mocked.chatStream.mockImplementationOnce(async (_payload, onEvent) => {
    emit = onEvent
    await new Promise<void>((resolve) => { finishRequest = resolve })
  })
  function Harness() {
    const [show, setShow] = useState(true)
    return <><button onClick={() => setShow(!show)}>{show ? '切到其他页面' : '返回推荐页'}</button>{show ? <ChatPage /> : <div>其他页面</div>}</>
  }
  render(<MemoryRouter><ChatSessionProvider><Harness /></ChatSessionProvider></MemoryRouter>)
  fireEvent.change(screen.getByLabelText('描述你的餐食需求'), { target: { value: '推荐晚餐' } })
  fireEvent.click(screen.getByRole('button', { name: '发送' }))
  await waitFor(() => expect(emit).toBeTypeOf('function'))
  act(() => {
    emit!({ type: 'delta', text: '逐字输出正常' })
    emit!({ type: 'complete', response: { sessionId: 'session-live', responseType: 'ANSWER', speechText: '逐字输出正常', displayBlocks: [], missingSlots: [] } })
    finishRequest!()
  })
  expect(screen.queryByText('逐字输出正常')).toBeNull()
  fireEvent.click(screen.getByRole('button', { name: '切到其他页面' }))
  await new Promise((resolve) => setTimeout(resolve, 180))
  fireEvent.click(screen.getByRole('button', { name: '返回推荐页' }))
  expect(await screen.findByText('逐字输出正常')).toBeTruthy()
  expect(screen.getByText('推荐晚餐')).toBeTruthy()
})

it('聊天记录可以恢复完整用户和助手消息', async () => {
  mocked.sessions.mockResolvedValue([{ id: 'old-session', phase: 'RECOMMEND', sourceMode: 'PERSONAL', preview: '想吃清淡午餐', messageCount: 2,
    createdAt: '2026-09-20T10:00:00', updatedAt: '2026-09-20T10:01:00' }])
  mocked.sessionMessages.mockResolvedValue([
    { id: 1, role: 'user', content: '想吃清淡午餐', createdAt: '2026-09-20T10:00:00' },
    { id: 2, role: 'assistant', content: '可以试试番茄鸡蛋面', createdAt: '2026-09-20T10:01:00' },
  ])
  renderChat()
  fireEvent.click(await screen.findByRole('button', { name: /想吃清淡午餐/ }))
  expect(await screen.findByText('可以试试番茄鸡蛋面')).toBeTruthy()
  expect(mocked.sessionMessages).toHaveBeenCalledWith('old-session')
  expect(screen.getByRole('button', { name: /我的餐食/ }).className).toContain('active')
})

function renderChat() {
  return render(<MemoryRouter><ChatSessionProvider><ChatPage /></ChatSessionProvider></MemoryRouter>)
}
