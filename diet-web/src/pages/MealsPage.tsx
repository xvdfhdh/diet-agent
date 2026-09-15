import { FormEvent, useEffect, useState } from 'react'
import { Plus, X } from 'lucide-react'
import { MealCard } from '../components/MealCard'
import { Notice } from '../components/Notice'
import { api } from '../lib/api'
import type { Meal, MealDraft } from '../types'

const fields: Array<{ key: keyof MealDraft; label: string }> = [
  { key: 'mealTime', label: '时段' }, { key: 'mood', label: '心情' }, { key: 'scene', label: '场景' },
  { key: 'healthGoal', label: '饮食目标' }, { key: 'cuisine', label: '菜系' }, { key: 'taste', label: '口味' }, { key: 'convenience', label: '便利偏好' },
]
const emptyDraft: MealDraft = { name: '', imageUrl: '', mealTime: [], mood: [], scene: [], healthGoal: [], cuisine: [], taste: [], convenience: [] }

export function MealsPage({ mode }: { mode: 'personal' | 'public' }) {
  const [meals, setMeals] = useState<Meal[]>([])
  const [options, setOptions] = useState<Record<string, string[]>>({})
  const [draft, setDraft] = useState<MealDraft>(emptyDraft)
  const [editingId, setEditingId] = useState<number>()
  const [open, setOpen] = useState(false)
  const [loading, setLoading] = useState(true)
  const [notice, setNotice] = useState({ message: '', tone: 'success' as 'success' | 'error' })

  async function load() {
    setLoading(true)
    try { setMeals(await api.meals(mode)) }
    catch (error) { setNotice({ message: error instanceof Error ? error.message : '餐食加载失败', tone: 'error' }) }
    finally { setLoading(false) }
  }
  useEffect(() => { load(); api.slotOptions().then(setOptions).catch(() => undefined) }, [mode])

  function edit(meal: Meal) {
    const { name, imageUrl, mealTime, mood, scene, healthGoal, cuisine, taste, convenience } = meal
    setDraft({ name, imageUrl: imageUrl || '', mealTime, mood, scene, healthGoal, cuisine, taste, convenience })
    setEditingId(meal.id); setOpen(true)
  }
  function close() { setOpen(false); setEditingId(undefined); setDraft(emptyDraft) }
  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!draft.name.trim()) return
    try {
      if (editingId) await api.updateMeal(editingId, draft); else await api.createMeal(draft)
      setNotice({ message: editingId ? '餐食已更新' : '餐食已加入你的清单', tone: 'success' })
      close(); await load()
    } catch (error) { setNotice({ message: error instanceof Error ? error.message : '保存失败', tone: 'error' }) }
  }
  async function remove(meal: Meal) {
    if (!window.confirm(`确定删除「${meal.name}」吗？`)) return
    try { await api.deleteMeal(meal.id); await load(); setNotice({ message: '已删除', tone: 'success' }) }
    catch (error) { setNotice({ message: error instanceof Error ? error.message : '删除失败', tone: 'error' }) }
  }
  function toggle(key: keyof MealDraft, value: string) {
    const current = draft[key]
    if (!Array.isArray(current)) return
    setDraft({ ...draft, [key]: current.includes(value) ? current.filter((item) => item !== value) : [...current, value] })
  }

  const personal = mode === 'personal'
  return <section className="content-page">
    <header className="page-header row-header"><div><p className="eyebrow">{personal ? '你的味觉档案' : '基础餐食库'}</p><h1>{personal ? '我的餐食' : '公共餐食'}</h1><p>{personal ? '把常吃、想再吃的餐食整理在这里。' : '所有人都可以使用的推荐基础。'}</p></div>
      {personal && <button className="primary-button" onClick={() => setOpen(true)}><Plus size={17} />添加餐食</button>}
    </header>
    <Notice {...notice} />
    {loading ? <div className="loading-line">正在整理餐食…</div> : meals.length === 0 ? <div className="empty-state"><h2>这里还没有餐食</h2><p>添加几道你熟悉的味道，推荐会更贴近你。</p></div> : <div className="meal-list">
      {meals.map((meal) => <MealCard key={meal.id} meal={meal} editable={personal} onEdit={() => edit(meal)} onDelete={() => remove(meal)} />)}
    </div>}

    {open && <div className="dialog-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) close() }}>
      <form className="dialog meal-form" onSubmit={submit}>
        <div className="dialog-title"><div><small>{editingId ? '编辑餐食' : '添加餐食'}</small><h2>记录一道熟悉的味道</h2></div><button type="button" className="icon-button" onClick={close}><X size={20} /></button></div>
        <label className="field"><span>餐食名称</span><input autoFocus value={draft.name} onChange={(event) => setDraft({ ...draft, name: event.target.value })} placeholder="例如：土豆炖牛肉" /></label>
        <label className="field"><span>图片地址（选填）</span><input value={draft.imageUrl || ''} onChange={(event) => setDraft({ ...draft, imageUrl: event.target.value })} placeholder="例如：https://…/meal.jpg 或 /meals/meal.jpg" /></label>
        {fields.map(({ key, label }) => <fieldset className="choice-field" key={key}><legend>{label}</legend><div>{(options[key] || []).slice(0, 16).map((value) => <button type="button" key={value} className={(draft[key] as string[]).includes(value) ? 'selected' : ''} onClick={() => toggle(key, value)}>{value}</button>)}</div></fieldset>)}
        <div className="dialog-actions"><button type="button" className="secondary-button" onClick={close}>取消</button><button className="primary-button">{editingId ? '保存修改' : '加入清单'}</button></div>
      </form>
    </div>}
  </section>
}
