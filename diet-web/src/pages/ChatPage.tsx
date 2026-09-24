import { FormEvent, useEffect, useState } from 'react'
import { ArrowUp, Brain, CalendarCheck, Check, CheckCircle2, Clock3, MessagesSquare, RotateCcw, XCircle } from 'lucide-react'
import { Link } from 'react-router-dom'
import { api } from '../lib/api'
import { MealCard } from '../components/MealCard'
import { Notice } from '../components/Notice'
import type { Meal, RecommendationHistory, SessionSummary, UserMemory } from '../types'
import type { PlanItem } from '../types'
import { AddToPlanDialog } from '../components/AddToPlanDialog'
import { useChatSession } from '../lib/ChatSessionContext'

const suggestions = ['午饭想吃辣一点，最好快一些', '运动后想吃高蛋白的晚餐', '今天没胃口，来点清淡暖胃的']
const previewMeals: Meal[] = [
  { id: -1, sourceType: 'PUBLIC', name: '番茄鸡蛋面', imageUrl: '/meals/tomato-egg-noodles.jpg', mealTime: ['午餐'], mood: [], scene: [], healthGoal: ['清淡', '养胃'], cuisine: ['家常'], taste: ['番茄味'], convenience: ['快速'], matchScore: 0 },
  { id: -2, sourceType: 'PUBLIC', name: '清汤馄饨', imageUrl: '/meals/clear-wonton.jpg', mealTime: ['晚餐'], mood: [], scene: [], healthGoal: ['暖胃'], cuisine: ['小吃'], taste: ['咸鲜'], convenience: ['少餐具'], matchScore: 0 },
  { id: -3, sourceType: 'PUBLIC', name: '鸡胸肉轻食碗', imageUrl: '/meals/chicken-grain-bowl.jpg', mealTime: ['午餐'], mood: [], scene: [], healthGoal: ['高蛋白'], cuisine: ['轻食'], taste: ['清淡'], convenience: ['快速'], matchScore: 0 },
]

export function ChatPage() {
  const { mode, setMode, smartMode, toggleSmartMode, sessionId, messages, loading, send: sendChat,
    clear: clearConversation, restore: restoreConversation, notice: chatNotice, clearNotice,
    confirmAction, cancelAction, dataRevision, mutationRevision, conversationRevision } = useChatSession()
  const [input, setInput] = useState('')
  const [publicMeals, setPublicMeals] = useState<Meal[]>([])
  const [history, setHistory] = useState<RecommendationHistory[]>([])
  const [memories, setMemories] = useState<UserMemory[]>([])
  const [favoriteIds, setFavoriteIds] = useState<Set<number>>(new Set())
  const [todayPlans, setTodayPlans] = useState<PlanItem[]>([])
  const [sessions, setSessions] = useState<SessionSummary[]>([])
  const [planMeal, setPlanMeal] = useState<Meal>()
  const [notice, setNotice] = useState({ message: '', tone: 'success' as 'success' | 'error' })

  useEffect(() => {
    let active = true
    Promise.all([api.meals('public'), api.recommendationHistory(), api.memories(), api.favorites(), api.plans(currentWeek()), api.sessions()])
      .then(([meals, records, remembered, favorites, plans, sessionRecords]) => {
        if (!active) return
        setPublicMeals(meals)
        setHistory(records)
        setMemories(remembered)
        setFavoriteIds(new Set(favorites.map((item) => item.meal.id)))
        setTodayPlans(plans.filter((item) => item.planDate === today()))
        setSessions(sessionRecords)
      })
      .catch((error) => {
        if (active) setNotice({ message: error instanceof Error ? error.message : '加载今日推荐失败', tone: 'error' })
      })
    return () => { active = false }
  }, [])

  useEffect(() => {
    if (!chatNotice.message) return
    setNotice(chatNotice)
    clearNotice()
  }, [chatNotice.message])

  useEffect(() => { if (dataRevision > 0) void refreshPersonalContext() }, [dataRevision])
  useEffect(() => { if (mutationRevision > 0) void refreshTodayPlans() }, [mutationRevision])
  useEffect(() => {
    if (conversationRevision > 0) void api.sessions().then(setSessions).catch(() => undefined)
  }, [conversationRevision])

  async function refreshPersonalContext() {
    try {
      const [records, remembered] = await Promise.all([api.recommendationHistory(), api.memories()])
      setHistory(records)
      setMemories(remembered)
    } catch (error) {
      setNotice({ message: error instanceof Error ? error.message : '推荐已生成，但历史记录刷新失败', tone: 'error' })
    }
  }

  async function refreshTodayPlans() {
    try {
      const plans = await api.plans(currentWeek())
      setTodayPlans(plans.filter((item) => item.planDate === today()))
    } catch (error) {
      setNotice({ message: error instanceof Error ? error.message : '计划已修改，但今日计划刷新失败', tone: 'error' })
    }
  }

  function send(message = input) {
    const text = message.trim()
    if (!text || loading) return
    setInput('')
    sendChat(text)
  }

  async function openSession(item: SessionSummary) {
    if (loading) return
    try {
      const records = await api.sessionMessages(item.id)
      restoreConversation(item.id, item.sourceMode, records)
    } catch (error) {
      setNotice({ message: error instanceof Error ? error.message : '聊天记录加载失败', tone: 'error' })
    }
  }

  async function rememberFeedback(meal: Meal, action: 'LIKE' | 'DISLIKE') {
    if (!sessionId) return
    try {
      await api.feedback({ sessionId, itemId: meal.id, action, rating: action === 'LIKE' ? 5 : 1 })
      setNotice({ message: action === 'LIKE' ? `已记住你喜欢「${meal.name}」` : `已记住以后少推荐「${meal.name}」`, tone: 'success' })
      void refreshPersonalContext()
    } catch (error) {
      setNotice({ message: error instanceof Error ? error.message : '反馈提交失败', tone: 'error' })
    }
  }

  async function toggleFavorite(meal: Meal) {
    if (meal.id < 0) return
    try {
      if (favoriteIds.has(meal.id)) {
        await api.removeFavorite(meal.id)
        setFavoriteIds((ids) => { const next = new Set(ids); next.delete(meal.id); return next })
        setNotice({ message: `已取消收藏「${meal.name}」`, tone: 'success' })
      } else {
        await api.addFavorite(meal.id, sessionId)
        setFavoriteIds((ids) => new Set(ids).add(meal.id))
        setNotice({ message: `已收藏「${meal.name}」，也会用于长期偏好`, tone: 'success' })
        void refreshPersonalContext()
      }
    } catch (error) {
      setNotice({ message: error instanceof Error ? error.message : '收藏操作失败', tone: 'error' })
    }
  }

  async function executeToday(item: PlanItem, action: 'COMPLETE' | 'SKIP' | 'REPLACE') {
    try {
      const updated = action === 'REPLACE' ? await api.replacePlanItem(item.id)
        : await api.checkInPlanItem(item.id, { skipped: action === 'SKIP' })
      setTodayPlans((plans) => plans.map((entry) => entry.id === item.id ? updated : entry))
      setNotice({ message: action === 'REPLACE' ? '已换一道类似的' : action === 'SKIP' ? '已记录跳过' : '打卡成功', tone: 'success' })
    } catch (cause) { setNotice({ message: cause instanceof Error ? cause.message : '操作失败', tone: 'error' }) }
  }

  const recommendations = messages.flatMap((item) => item.meals || []).slice(-3)
  const fallbackMeals = publicMeals.length ? publicMeals.slice(0, 3) : previewMeals
  const visibleMemories = memories.filter((memory) => memory.type === 'SLOT_PREFERENCE').slice(0, 8)
  return (
    <div className="chat-layout">
      <section className="chat-main">
        <header className="page-header chat-header">
          <div><p className="eyebrow">今天 · 一餐一味</p><h1>今天想吃点什么？</h1><p>说说你的时间、口味，或者此刻更在意什么。</p></div>
          {messages.length > 0 && <button className="quiet-button" onClick={clearConversation}><RotateCcw size={16} />新对话</button>}
        </header>

        <section className="today-plan-strip"><div className="today-plan-heading"><CalendarCheck size={18} /><span><strong>今日计划</strong><small>{todayPlans.length ? `${todayPlans.filter((item) => item.status === 'COMPLETED').length}/${todayPlans.length} 已完成` : '今天还没安排'}</small></span><Link to="/plan">查看整周</Link></div><div className="today-plan-items">{todayPlans.length ? todayPlans.slice(0, 4).map((item) => <article key={item.id} className={`today-plan-card ${item.status.toLowerCase()}`}><div><b>{periodLabel(item.mealPeriod)}</b><Link to={`/meals/${item.mealId}`}>{item.meal.name}</Link><small>{item.acquisitionMode === 'COOK' ? `做饭${item.meal.prepMinutes ? ` · ${item.meal.prepMinutes} 分钟` : ''}` : `外食${item.meal.priceMin != null ? ` · ¥${item.meal.priceMin} 起` : ''}`}</small></div>{(item.status === 'PLANNED' || item.status === 'REPLACED') && <div className="today-plan-actions"><button onClick={() => void executeToday(item, 'COMPLETE')}>已吃</button><button onClick={() => void executeToday(item, 'SKIP')}>跳过</button><button onClick={() => void executeToday(item, 'REPLACE')}>换一道</button></div>}</article>) : <p>从推荐卡片加入今天，或让 AI 安排一周。</p>}</div></section>

        <div className="recommendation-controls">
          {smartMode ? <div className="unified-source-note"><Brain size={15} /><span><b>智能跨库</b><small>个人餐食优先，也会检索公共餐食</small></span></div> :
            <div className="source-switch" aria-label="餐食来源">
              <button disabled={loading} className={mode === 'PERSONAL' ? 'active' : ''} onClick={() => setMode('PERSONAL')}>我的餐食{mode === 'PERSONAL' && <Check size={14} />}</button>
              <button disabled={loading} className={mode === 'PUBLIC' ? 'active' : ''} onClick={() => setMode('PUBLIC')}>公共餐食{mode === 'PUBLIC' && <Check size={14} />}</button>
            </div>}
          <button type="button" role="switch" aria-checked={smartMode} className={`smart-mode-toggle ${smartMode ? 'active' : ''}`} disabled={loading} onClick={toggleSmartMode}>
            <span className="switch-track"><i /></span><Brain size={15} />智能推荐<small>{smartMode ? 'Agent' : '关闭'}</small>
          </button>
        </div>

        <div className="conversation" aria-live="polite">
          {messages.length === 0 ? <div className="empty-conversation">
            <div className="day-mark"><span>{new Date().getDate()}</span><small>{new Intl.DateTimeFormat('zh-CN', { month: 'short' }).format(new Date())}</small></div>
            <p>不用把需求整理得很完整。<br />像平时点餐一样说就好。</p>
            <div className="suggestion-list">
              {suggestions.map((item) => <button key={item} onClick={() => send(item)}>{item}</button>)}
            </div>
          </div> : messages.map((message) => <div className={`message ${message.role}`} key={message.id}>
            <span className="message-author">{message.role === 'user' ? '你' : '食刻'}{message.role === 'assistant' && message.recommendationMode === 'AGENT' && <em className="agent-badge">智能 Agent</em>}</span>
            <div className={`message-content ${message.streaming && !message.text ? 'typing' : ''}`}>
              {message.status ? <p className="stream-status"><i /><i /><i />{message.status}</p> : <p className={message.streaming ? 'streaming-copy' : undefined}>{message.text}</p>}
              {!!message.activities?.length && <details className="agent-activities" open={message.streaming}>
                <summary>{message.execution?.fallbackOccurred ? '智能处理过程（已降级）' : '智能处理过程'} · {message.activities.length} 项</summary>
                <ul>{message.activities.map((activity, index) => <li key={`${activity.name}-${index}`} className={activity.status.toLowerCase()}><Check size={13} /><span><b>{activity.name}</b>{activity.detail && <small>{activity.detail}</small>}</span></li>)}</ul>
              </details>}
              {message.actionPreview && <section className={`agent-action-preview ${message.actionStatus ? 'resolved' : ''}`}>
                <header><span>{message.actionStatus === 'CONFIRMED' ? <CheckCircle2 size={17} /> : message.actionStatus === 'CANCELLED' ? <XCircle size={17} /> : <CalendarCheck size={17} />}</span>
                  <div><b>{message.actionStatus === 'CONFIRMED' ? '已执行' : message.actionStatus === 'CANCELLED' ? '已取消' : '操作预览'}</b><p>{message.actionPreview.summary}</p></div></header>
                <ul>{message.actionPreview.changes.map((change, index) => <li key={`${change.domain}-${change.operation}-${index}`}><span>{domainLabel(change.domain)}</span>{change.description}</li>)}</ul>
                {!message.actionStatus && <><small>预览将在 {formatTime(message.actionPreview.expiresAt)} 过期，确认前不会修改数据。</small>
                  <div className="agent-action-buttons"><button type="button" disabled={loading} onClick={() => void confirmAction(message.id, message.actionPreview!.id)}>确认执行</button><button type="button" disabled={loading} onClick={() => void cancelAction(message.id, message.actionPreview!.id)}>取消</button></div></>}
              </section>}
              {message.meals?.map((meal) => <MealCard key={meal.id} meal={meal} compact favorited={favoriteIds.has(meal.id)} onFavorite={() => toggleFavorite(meal)} onFeedback={(action) => rememberFeedback(meal, action)} onPlan={() => setPlanMeal(meal)} />)}
            </div>
          </div>)}
        </div>

        <form className="chat-composer" onSubmit={(event: FormEvent) => { event.preventDefault(); send() }}>
          <textarea value={input} onChange={(event) => setInput(event.target.value)} onKeyDown={(event) => {
            if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); send() }
          }} placeholder="比如：午饭想吃辣一点的" aria-label="描述你的餐食需求" rows={2} />
          <button disabled={!input.trim() || loading} aria-label="发送"><ArrowUp size={20} /></button>
        </form>
        <Notice {...notice} />
      </section>

      <aside className="recommendation-rail">
        <div className="rail-heading"><span>今日推荐</span><small>{history.length} 次记录</small></div>
        <section className="chat-history-section" aria-labelledby="chat-history-title">
          <div className="rail-section-title" id="chat-history-title"><span><MessagesSquare size={14} />聊天记录</span><small>{sessions.length} 个会话</small></div>
          {sessions.length === 0 ? <p className="rail-note">完成一次对话后，会话会保存在这里。</p> :
            <div className="session-history-list">{sessions.slice(0, 12).map((item) => <button key={item.id} disabled={loading} className={item.id === sessionId ? 'active' : ''} onClick={() => void openSession(item)}>
              <span>{item.preview || '新会话'}</span><small>{formatSessionTime(item.updatedAt)} · {item.messageCount} 条</small>
            </button>)}</div>}
        </section>
        <div className="rail-section-title"><span>本轮推荐</span><small>{recommendations.length || fallbackMeals.length} 道</small></div>
        {(recommendations.length ? recommendations : fallbackMeals).map((meal) => <MealCard key={meal.id} meal={meal} compact favorited={favoriteIds.has(meal.id)} onFavorite={meal.id > 0 ? () => toggleFavorite(meal) : undefined} onPlan={meal.id > 0 ? () => setPlanMeal(meal) : undefined} />)}
        {!recommendations.length && <p className="rail-note">先给你三道参考。开始聊聊，推荐会跟着你的描述变化。</p>}

        <section className="history-section" aria-labelledby="today-history-title">
          <div className="rail-section-title" id="today-history-title"><span><Clock3 size={14} />今天的历史</span><small>{history.length ? `${history.reduce((sum, item) => sum + item.meals.length, 0)} 道` : '暂无'}</small></div>
          {history.length === 0 ? <p className="rail-note">今天生成的推荐会保存在这里，新对话也不会丢失。</p> :
            <div className="history-list">{history.map((record) => <article className="history-entry" key={record.id}>
              <div><time dateTime={record.createdAt}>{formatTime(record.createdAt)}</time><span>{record.sourceStrategy === 'UNIFIED' ? '智能跨库' : record.sourceMode === 'PERSONAL' ? '我的餐食' : '公共餐食'}</span></div>
              <p>{record.userInput}</p>
              <ul>{record.meals.map((meal) => <li key={`${record.id}-${meal.id}`}>{meal.name}</li>)}</ul>
            </article>)}</div>}
        </section>

        <section className="memory-section" aria-labelledby="memory-title">
          <div className="rail-section-title" id="memory-title"><span><Brain size={14} />长期记忆</span><small>跨对话生效</small></div>
          {visibleMemories.length === 0 ? <p className="rail-note">告诉我喜欢的口味，或给推荐点赞，我会逐渐记住。</p> :
            <div className="memory-chips">{visibleMemories.map((memory) => <span key={memory.id} title={`记忆强度 ${memory.strength}`}>{memoryLabel(memory.key)} · {memory.value}</span>)}</div>}
        </section>
      </aside>
      {planMeal && <AddToPlanDialog meal={planMeal} onClose={() => setPlanMeal(undefined)} onAdded={() => setNotice({ message: `已将「${planMeal.name}」加入计划`, tone: 'success' })} />}
    </div>
  )
}

function today() { return new Date(Date.now() - new Date().getTimezoneOffset() * 60_000).toISOString().slice(0, 10) }
function currentWeek() { const date = new Date(); const day = date.getDay() || 7; date.setDate(date.getDate() - day + 1); return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 10) }
function periodLabel(period: PlanItem['mealPeriod']) { return ({ BREAKFAST: '早', LUNCH: '午', DINNER: '晚', SNACK: '加' } as const)[period] }

const memoryLabels: Record<string, string> = {
  healthGoal: '目标',
  cuisine: '菜系',
  taste: '口味',
  convenience: '习惯',
}

function memoryLabel(key: string) {
  return memoryLabels[key] || key
}

function domainLabel(domain: string) {
  return ({ PLAN: '计划', SHOPPING: '清单', CHECKIN: '打卡', FAVORITE: '收藏', PREFERENCE: '偏好' } as Record<string, string>)[domain] || domain
}

function formatTime(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false }).format(date)
}

function formatSessionTime(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false }).format(date)
}
