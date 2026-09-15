import { FormEvent, useEffect, useState } from 'react'
import { ArrowUp, Brain, Check, Clock3, RotateCcw } from 'lucide-react'
import { api } from '../lib/api'
import { MealCard } from '../components/MealCard'
import { Notice } from '../components/Notice'
import type { Meal, RecommendationHistory, SourceMode, UserMemory } from '../types'

type Message = { role: 'user' | 'assistant'; text: string; meals?: Meal[]; traceId?: string }

const suggestions = ['午饭想吃辣一点，最好快一些', '运动后想吃高蛋白的晚餐', '今天没胃口，来点清淡暖胃的']
const previewMeals: Meal[] = [
  { id: -1, sourceType: 'PUBLIC', name: '番茄鸡蛋面', mealTime: ['午餐'], mood: [], scene: [], healthGoal: ['清淡', '养胃'], cuisine: ['家常'], taste: ['番茄味'], convenience: ['快速'], matchScore: 0 },
  { id: -2, sourceType: 'PUBLIC', name: '清汤馄饨', mealTime: ['晚餐'], mood: [], scene: [], healthGoal: ['暖胃'], cuisine: ['小吃'], taste: ['咸鲜'], convenience: ['少餐具'], matchScore: 0 },
  { id: -3, sourceType: 'PUBLIC', name: '鸡胸肉轻食碗', mealTime: ['午餐'], mood: [], scene: [], healthGoal: ['高蛋白'], cuisine: ['轻食'], taste: ['清淡'], convenience: ['快速'], matchScore: 0 },
]

export function ChatPage() {
  const [mode, setMode] = useState<SourceMode>('PUBLIC')
  const [input, setInput] = useState('')
  const [sessionId, setSessionId] = useState<string>()
  const [messages, setMessages] = useState<Message[]>([])
  const [publicMeals, setPublicMeals] = useState<Meal[]>([])
  const [history, setHistory] = useState<RecommendationHistory[]>([])
  const [memories, setMemories] = useState<UserMemory[]>([])
  const [loading, setLoading] = useState(false)
  const [notice, setNotice] = useState({ message: '', tone: 'success' as 'success' | 'error' })

  useEffect(() => {
    let active = true
    Promise.all([api.meals('public'), api.recommendationHistory(), api.memories()])
      .then(([meals, records, remembered]) => {
        if (!active) return
        setPublicMeals(meals)
        setHistory(records)
        setMemories(remembered)
      })
      .catch((error) => {
        if (active) setNotice({ message: error instanceof Error ? error.message : '加载今日推荐失败', tone: 'error' })
      })
    return () => { active = false }
  }, [])

  async function refreshPersonalContext() {
    try {
      const [records, remembered] = await Promise.all([api.recommendationHistory(), api.memories()])
      setHistory(records)
      setMemories(remembered)
    } catch (error) {
      setNotice({ message: error instanceof Error ? error.message : '推荐已生成，但历史记录刷新失败', tone: 'error' })
    }
  }

  async function send(message = input) {
    const text = message.trim()
    if (!text || loading) return
    setInput('')
    setMessages((items) => [...items, { role: 'user', text }])
    setLoading(true)
    try {
      const response = await api.chat({ sessionId, message: text, sourceMode: mode })
      setSessionId(response.sessionId)
      setMessages((items) => [...items, {
        role: 'assistant',
        text: response.clarifyQuestion || response.speechText,
        meals: response.displayBlocks,
        traceId: response.traceId,
      }])
      if (response.displayBlocks.length > 0) void refreshPersonalContext()
    } catch (error) {
      setNotice({ message: error instanceof Error ? error.message : '发送失败，请稍后再试', tone: 'error' })
    } finally {
      setLoading(false)
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

  const recommendations = messages.flatMap((item) => item.meals || []).slice(-3)
  const fallbackMeals = publicMeals.length ? publicMeals.slice(0, 3) : previewMeals
  const visibleMemories = memories.filter((memory) => memory.type === 'SLOT_PREFERENCE').slice(0, 8)
  return (
    <div className="chat-layout">
      <section className="chat-main">
        <header className="page-header chat-header">
          <div><p className="eyebrow">今天 · 一餐一味</p><h1>今天想吃点什么？</h1><p>说说你的时间、口味，或者此刻更在意什么。</p></div>
          {messages.length > 0 && <button className="quiet-button" onClick={() => { setMessages([]); setSessionId(undefined) }}><RotateCcw size={16} />新对话</button>}
        </header>

        <div className="source-switch" aria-label="餐食来源">
          <button className={mode === 'PERSONAL' ? 'active' : ''} onClick={() => setMode('PERSONAL')}>我的餐食{mode === 'PERSONAL' && <Check size={14} />}</button>
          <button className={mode === 'PUBLIC' ? 'active' : ''} onClick={() => setMode('PUBLIC')}>公共餐食{mode === 'PUBLIC' && <Check size={14} />}</button>
        </div>

        <div className="conversation" aria-live="polite">
          {messages.length === 0 ? <div className="empty-conversation">
            <div className="day-mark"><span>{new Date().getDate()}</span><small>{new Intl.DateTimeFormat('zh-CN', { month: 'short' }).format(new Date())}</small></div>
            <p>不用把需求整理得很完整。<br />像平时点餐一样说就好。</p>
            <div className="suggestion-list">
              {suggestions.map((item) => <button key={item} onClick={() => send(item)}>{item}</button>)}
            </div>
          </div> : messages.map((message, index) => <div className={`message ${message.role}`} key={`${message.role}-${index}`}>
            <span className="message-author">{message.role === 'user' ? '你' : '食刻'}</span>
            <div className="message-content"><p>{message.text}</p>{message.meals?.map((meal) => <MealCard key={meal.id} meal={meal} compact onFeedback={(action) => rememberFeedback(meal, action)} />)}</div>
          </div>)}
          {loading && <div className="message assistant"><span className="message-author">食刻</span><div className="message-content typing"><i /><i /><i /></div></div>}
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
        <div className="rail-section-title"><span>本轮推荐</span><small>{recommendations.length || fallbackMeals.length} 道</small></div>
        {(recommendations.length ? recommendations : fallbackMeals).map((meal) => <MealCard key={meal.id} meal={meal} compact />)}
        {!recommendations.length && <p className="rail-note">先给你三道参考。开始聊聊，推荐会跟着你的描述变化。</p>}

        <section className="history-section" aria-labelledby="today-history-title">
          <div className="rail-section-title" id="today-history-title"><span><Clock3 size={14} />今天的历史</span><small>{history.length ? `${history.reduce((sum, item) => sum + item.meals.length, 0)} 道` : '暂无'}</small></div>
          {history.length === 0 ? <p className="rail-note">今天生成的推荐会保存在这里，新对话也不会丢失。</p> :
            <div className="history-list">{history.map((record) => <article className="history-entry" key={record.id}>
              <div><time dateTime={record.createdAt}>{formatTime(record.createdAt)}</time><span>{record.sourceMode === 'PERSONAL' ? '我的餐食' : '公共餐食'}</span></div>
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
    </div>
  )
}

const memoryLabels: Record<string, string> = {
  healthGoal: '目标',
  cuisine: '菜系',
  taste: '口味',
  convenience: '习惯',
}

function memoryLabel(key: string) {
  return memoryLabels[key] || key
}

function formatTime(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false }).format(date)
}
