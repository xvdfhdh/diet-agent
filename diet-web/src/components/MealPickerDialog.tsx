import { useEffect, useMemo, useState } from 'react'
import { Check, Search, X } from 'lucide-react'
import { api } from '../lib/api'
import type { AcquisitionMode, Meal, PlanItem } from '../types'

export function MealPickerDialog({ item, onClose, onChanged }: {
  item: PlanItem
  onClose: () => void
  onChanged: (mealName: string) => void
}) {
  const [meals, setMeals] = useState<Meal[]>([])
  const [query, setQuery] = useState('')
  const [source, setSource] = useState<'ALL' | 'PERSONAL' | 'PUBLIC'>('ALL')
  const [loading, setLoading] = useState(true)
  const [savingId, setSavingId] = useState<number>()
  const [error, setError] = useState('')
  useEffect(() => {
    let active = true
    Promise.all([api.meals('personal'), api.meals('public')])
      .then(([personal, publicMeals]) => { if (active) setMeals([...personal, ...publicMeals]) })
      .catch((cause) => { if (active) setError(cause instanceof Error ? cause.message : '餐食加载失败') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [])
  const visible = useMemo(() => {
    const keyword = query.trim().toLowerCase()
    return meals.filter((meal) => (source === 'ALL' || meal.sourceType === source)
      && (!keyword || [meal.name, ...meal.cuisine, ...meal.taste, ...meal.healthGoal].join(' ').toLowerCase().includes(keyword)))
  }, [meals, query, source])

  async function choose(meal: Meal) {
    if (savingId != null) return
    setSavingId(meal.id); setError('')
    const mode: AcquisitionMode = meal.acquisitionMode === 'EAT_OUT' ? 'EAT_OUT'
      : meal.acquisitionMode === 'COOK' ? 'COOK' : item.acquisitionMode
    try { await api.updatePlanItem(item.id, { mealId: meal.id, acquisitionMode: mode }); onChanged(meal.name) }
    catch (cause) { setError(cause instanceof Error ? cause.message : '替换餐食失败'); setSavingId(undefined) }
  }

  return <div className="dialog-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget && savingId == null) onClose() }}>
    <section className="dialog meal-picker-dialog" role="dialog" aria-modal="true" aria-labelledby="meal-picker-title">
      <div className="dialog-title"><div><small>{item.planDate} · {periodLabel(item.mealPeriod)}</small><h2 id="meal-picker-title">选择计划餐食</h2></div><button type="button" className="icon-button" onClick={onClose} disabled={savingId != null}><X size={20} /></button></div>
      <div className="meal-picker-tools"><label><Search size={16} /><input aria-label="搜索餐食" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="搜索名称、口味或菜系" /></label><div className="source-switch compact-switch">{(['ALL', 'PERSONAL', 'PUBLIC'] as const).map((value) => <button type="button" key={value} className={source === value ? 'active' : ''} onClick={() => setSource(value)}>{value === 'ALL' ? '全部' : value === 'PERSONAL' ? '我的餐食' : '公共餐食'}</button>)}</div></div>
      {error && <p className="inline-error" role="alert">{error}</p>}
      {loading ? <div className="loading-line">正在加载可选餐食…</div> : visible.length === 0 ? <div className="empty-state compact-empty"><h3>没有匹配的餐食</h3><p>换个关键词，或先到“我的餐食”添加。</p></div> : <div className="meal-picker-list">{visible.map((meal) => <button type="button" className={meal.id === item.mealId ? 'meal-picker-item selected' : 'meal-picker-item'} key={meal.id} disabled={savingId != null} onClick={() => void choose(meal)}>
        <img src={meal.imageUrl || '/meals/chicken-grain-bowl.jpg'} alt="" /><span><strong>{meal.name}</strong><small>{meal.sourceType === 'PERSONAL' ? '我的餐食' : '公共餐食'} · {modeLabel(meal.acquisitionMode)}</small><em>{[...meal.taste, ...meal.cuisine, ...meal.healthGoal].slice(0, 3).join(' · ') || '暂无标签'}</em></span>{meal.id === item.mealId ? <Check size={18} /> : savingId === meal.id ? <i>替换中…</i> : <i>选择</i>}
      </button>)}</div>}
    </section>
  </div>
}

function periodLabel(period: PlanItem['mealPeriod']) { return ({ BREAKFAST: '早餐', LUNCH: '午餐', DINNER: '晚餐', SNACK: '加餐' } as const)[period] }
function modeLabel(mode?: AcquisitionMode) { return mode === 'COOK' ? '做饭' : mode === 'EAT_OUT' ? '外食' : '做饭或外食' }
