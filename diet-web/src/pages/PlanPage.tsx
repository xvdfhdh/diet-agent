import { FormEvent, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { CalendarDays, CheckCircle2, ChevronLeft, ChevronRight, Copy, RefreshCw, Sparkles, Trash2, X } from 'lucide-react'
import { AddToPlanDialog } from '../components/AddToPlanDialog'
import { MealPickerDialog } from '../components/MealPickerDialog'
import { Notice } from '../components/Notice'
import { api } from '../lib/api'
import type { MealPeriod, PlanItem, WeeklySummary } from '../types'

const periods: Array<{ value: MealPeriod; label: string }> = [
  { value: 'BREAKFAST', label: '早餐' }, { value: 'LUNCH', label: '午餐' },
  { value: 'DINNER', label: '晚餐' }, { value: 'SNACK', label: '加餐' },
]

export function PlanPage() {
  const [weekStart, setWeekStart] = useState(monday(new Date()))
  const [items, setItems] = useState<PlanItem[]>([])
  const [summary, setSummary] = useState<WeeklySummary>()
  const [loading, setLoading] = useState(true)
  const [working, setWorking] = useState(false)
  const [copying, setCopying] = useState<PlanItem>()
  const [checking, setChecking] = useState<PlanItem>()
  const [choosing, setChoosing] = useState<PlanItem>()
  const dragging = useRef(false)
  const [notice, setNotice] = useState({ message: '', tone: 'success' as 'success' | 'error' })
  const weekKey = iso(weekStart)
  const days = useMemo(() => Array.from({ length: 7 }, (_, index) => addDays(weekStart, index)), [weekStart])
  const load = useCallback(async () => {
    setLoading(true)
    try { const [plans, report] = await Promise.all([api.plans(weekKey), api.weeklySummary(weekKey)]); setItems(plans); setSummary(report) }
    catch (cause) { setNotice({ message: cause instanceof Error ? cause.message : '计划加载失败', tone: 'error' }) }
    finally { setLoading(false) }
  }, [weekKey])
  useEffect(() => { void load() }, [load])

  async function generate() {
    setWorking(true)
    try { await api.generatePlan(weekKey); await load(); setNotice({ message: '已根据偏好、历史和可用餐食安排本周', tone: 'success' }) }
    catch (cause) { setNotice({ message: cause instanceof Error ? cause.message : '生成失败', tone: 'error' }) }
    finally { setWorking(false) }
  }
  async function replace(item: PlanItem) { try { await api.replacePlanItem(item.id); await load(); setNotice({ message: '已换成一道相似餐食', tone: 'success' }) } catch (cause) { fail(cause) } }
  async function remove(item: PlanItem) { try { await api.deletePlanItem(item.id); await load() } catch (cause) { fail(cause) } }
  async function carryToNextWeek(optimize = false) {
    const next = addDays(weekStart, 7), nextKey = iso(next); setWorking(true)
    try {
      if (optimize) await api.generatePlan(nextKey)
      else await Promise.all(items.map((item) => api.addPlanItem({ planDate: iso(addDays(new Date(`${item.planDate}T12:00:00`), 7)), mealPeriod: item.mealPeriod, mealId: item.mealId, acquisitionMode: item.acquisitionMode, servings: item.servings })))
      setWeekStart(next); setNotice({ message: optimize ? '已为下周重新优化' : '已沿用到下周', tone: 'success' })
    } catch (cause) { fail(cause) } finally { setWorking(false) }
  }
  async function drop(event: React.DragEvent, date: Date, period: MealPeriod) {
    event.preventDefault(); const id = Number(event.dataTransfer.getData('text/plan-id')); if (!id) return
    const item = items.find((entry) => entry.id === id); if (!item || (item.planDate === iso(date) && item.mealPeriod === period)) return
    try { await api.updatePlanItem(id, { planDate: iso(date), mealPeriod: period }); await load() }
    catch (cause) { fail(cause) }
  }
  function fail(cause: unknown) { setNotice({ message: cause instanceof Error ? cause.message : '操作失败', tone: 'error' }) }

  return <section className="content-page plan-page">
    <header className="page-header row-header"><div><p className="eyebrow">一周节奏</p><h1>本周计划</h1><p>安排、执行、反馈，下一次推荐会更懂你。</p></div>
      <button className="primary-button" onClick={() => void generate()} disabled={working}><Sparkles size={17} />{working ? '正在安排…' : '让 AI 安排一周'}</button></header>
    <Notice {...notice} />
    <div className="week-toolbar"><button className="icon-button" aria-label="上一周" onClick={() => setWeekStart(addDays(weekStart, -7))}><ChevronLeft /></button>
      <div><CalendarDays size={18} /><strong>{formatDay(weekStart)} — {formatDay(addDays(weekStart, 6))}</strong></div>
      <button className="quiet-button" onClick={() => setWeekStart(monday(new Date()))}>回到本周</button><button className="icon-button" aria-label="下一周" onClick={() => setWeekStart(addDays(weekStart, 7))}><ChevronRight /></button></div>

    {loading ? <div className="loading-line">正在整理本周计划…</div> : <div className="plan-grid" role="grid">
      <div className="plan-corner" />{days.map((day) => <div className={`plan-day-head ${iso(day) === iso(new Date()) ? 'today' : ''}`} key={iso(day)}><strong>{weekday(day)}</strong><span>{day.getMonth() + 1}/{day.getDate()}</span></div>)}
      {periods.map((period) => <div className="plan-row" key={period.value}>
        <div className="plan-period">{period.label}</div>
        {days.map((day) => { const item = items.find((entry) => entry.planDate === iso(day) && entry.mealPeriod === period.value); return <div className="plan-slot" key={iso(day)} onDragOver={(e) => e.preventDefault()} onDrop={(e) => void drop(e, day, period.value)}>
          {item ? <article className={`plan-item status-${item.status.toLowerCase()}`} role="button" tabIndex={0} aria-label={`选择${item.meal.name}的替换餐食`} draggable
            onDragStart={(e) => { dragging.current = true; e.dataTransfer.setData('text/plan-id', String(item.id)) }}
            onDragEnd={() => { window.setTimeout(() => { dragging.current = false }, 0) }}
            onClick={() => { if (!dragging.current) setChoosing(item) }}
            onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); setChoosing(item) } }}>
            <div className="plan-item-top"><span>{item.acquisitionMode === 'COOK' ? '做饭' : '外食'} · {item.servings} 份</span>{item.status !== 'PLANNED' && <b>{statusLabel(item.status)}</b>}</div>
            <strong className="plan-meal-name">{item.meal.name}</strong>
            <div className="plan-item-actions"><button title="换一道相似餐食" onClick={(event) => { event.stopPropagation(); void replace(item) }}><RefreshCw size={14} /></button><button title="复制到其他餐次" onClick={(event) => { event.stopPropagation(); setCopying(item) }}><Copy size={14} /></button><button title="打卡" onClick={(event) => { event.stopPropagation(); setChecking(item) }}><CheckCircle2 size={14} /></button><button title="删除" onClick={(event) => { event.stopPropagation(); void remove(item) }}><Trash2 size={14} /></button></div>
          </article> : <span className="empty-slot">拖到这里</span>}
        </div>})}
      </div>)}
    </div>}

    {summary && <section className="weekly-summary"><div><small>计划完成率</small><strong>{summary.completionRate}%</strong><span>{summary.completedCount}/{summary.plannedCount} 餐</span></div><div><small>做饭 / 外食</small><strong>{summary.cookCount} / {summary.eatOutCount}</strong><span>本周安排</span></div><div><small>预计 / 实际</small><strong>¥{summary.estimatedCost || 0} / ¥{summary.actualCost || 0}</strong><span>允许为空的估算</span></div><div><small>本周偏好</small><strong>{[...summary.topTastes, ...summary.topCuisines].slice(0, 2).join(' · ') || '尚未形成'}</strong><span>{summary.skippedCount} 次跳过</span></div></section>}
    {summary && <div className="summary-actions"><button className="secondary-button" disabled={!items.length || working} onClick={() => void carryToNextWeek(false)}>下周沿用</button><button className="secondary-button" disabled={working} onClick={() => void carryToNextWeek(true)}><Sparkles size={16} />让 AI 优化下周</button></div>}
    {copying && <AddToPlanDialog meal={copying.meal} onClose={() => setCopying(undefined)} onAdded={() => { void load(); setNotice({ message: '已复制到新餐次', tone: 'success' }) }} />}
    {checking && <CheckinDialog item={checking} onClose={() => setChecking(undefined)} onSaved={() => { setChecking(undefined); void load() }} />}
    {choosing && <MealPickerDialog item={choosing} onClose={() => setChoosing(undefined)} onChanged={(mealName) => { setChoosing(undefined); setNotice({ message: `已选择「${mealName}」`, tone: 'success' }); void load() }} />}
  </section>
}

function CheckinDialog({ item, onClose, onSaved }: { item: PlanItem; onClose: () => void; onSaved: () => void }) {
  const [rating, setRating] = useState(item.checkin?.rating || 4), [satiety, setSatiety] = useState(item.checkin?.satiety || 3)
  const [reasonCode, setReason] = useState(item.checkin?.reasonCode || ''), [note, setNote] = useState(item.checkin?.note || '')
  const [actualSpent, setSpent] = useState(item.checkin?.actualSpent?.toString() || ''), [error, setError] = useState('')
  async function submit(event: FormEvent, skipped = false) { event.preventDefault(); try { await api.checkInPlanItem(item.id, { skipped, rating: skipped ? undefined : rating, satiety: skipped ? undefined : satiety, reasonCode: reasonCode || undefined, note: note || undefined, actualSpent: actualSpent ? Number(actualSpent) : undefined }); onSaved() } catch (cause) { setError(cause instanceof Error ? cause.message : '打卡失败') } }
  return <div className="dialog-backdrop"><form className="dialog checkin-dialog" onSubmit={(e) => void submit(e)}><div className="dialog-title"><div><small>今日执行</small><h2>{item.meal.name}</h2></div><button type="button" className="icon-button" onClick={onClose}><X /></button></div>
    <div className="form-grid"><label className="field"><span>评分（1—5）</span><input type="number" min="1" max="5" value={rating} onChange={(e) => setRating(Number(e.target.value))} /></label><label className="field"><span>饱腹感（1—5）</span><input type="number" min="1" max="5" value={satiety} onChange={(e) => setSatiety(Number(e.target.value))} /></label><label className="field"><span>实际花费</span><input type="number" min="0" step="0.01" value={actualSpent} onChange={(e) => setSpent(e.target.value)} placeholder="选填" /></label><label className="field"><span>未执行原因 / 约束</span><select value={reasonCode} onChange={(e) => setReason(e.target.value)}><option value="">无</option><option value="TOO_EXPENSIVE">太贵</option><option value="TOO_SLOW">太费时间</option><option value="NOT_FILLING">吃不饱</option><option value="NO_APPETITE">没胃口</option></select></label></div>
    <label className="field"><span>备注</span><textarea value={note} onChange={(e) => setNote(e.target.value)} placeholder="实际吃了什么、哪里需要调整…" /></label>{error && <p className="inline-error">{error}</p>}
    <div className="dialog-actions"><button type="button" className="secondary-button" onClick={(e) => void submit(e as unknown as FormEvent, true)}>跳过这餐</button><button className="primary-button">确认已吃</button></div></form></div>
}

function monday(value: Date) { const date = new Date(value); date.setHours(12, 0, 0, 0); const day = date.getDay() || 7; date.setDate(date.getDate() - day + 1); return date }
function addDays(value: Date, days: number) { const result = new Date(value); result.setDate(result.getDate() + days); return result }
function iso(value: Date) { return new Date(value.getTime() - value.getTimezoneOffset() * 60_000).toISOString().slice(0, 10) }
function weekday(value: Date) { return new Intl.DateTimeFormat('zh-CN', { weekday: 'short' }).format(value) }
function formatDay(value: Date) { return new Intl.DateTimeFormat('zh-CN', { month: 'short', day: 'numeric' }).format(value) }
function statusLabel(status: PlanItem['status']) { return ({ PLANNED: '待执行', COMPLETED: '已吃', SKIPPED: '已跳过', REPLACED: '已替换' } as const)[status] }
