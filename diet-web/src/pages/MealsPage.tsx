import { FormEvent, useEffect, useState } from 'react'
import { Plus, Sparkles, SquarePen, Trash2, WandSparkles, X } from 'lucide-react'
import { MealCard } from '../components/MealCard'
import { Notice } from '../components/Notice'
import { api } from '../lib/api'
import { useAuth } from '../lib/AuthContext'
import { PublicBatchDialog } from '../components/PublicBatchDialog'
import { AddToPlanDialog } from '../components/AddToPlanDialog'
import { MealDetailEditors } from '../components/MealDetailEditors'
import type { Meal, MealDraft } from '../types'

type SlotKey = 'mealTime' | 'mood' | 'scene' | 'healthGoal' | 'cuisine' | 'taste' | 'convenience'
const fields: Array<{ key: SlotKey; label: string }> = [
  { key: 'mealTime', label: '时段' }, { key: 'mood', label: '心情' }, { key: 'scene', label: '场景' },
  { key: 'healthGoal', label: '饮食目标' }, { key: 'cuisine', label: '菜系' }, { key: 'taste', label: '口味' }, { key: 'convenience', label: '便利偏好' },
]
const emptyDraft: MealDraft = { name: '', imageUrl: '', mealTime: [], mood: [], scene: [], healthGoal: [], cuisine: [], taste: [], convenience: [], acquisitionMode: 'BOTH', defaultServings: 1, ingredients: [], steps: [], substitutes: [] }

export function MealsPage({ mode }: { mode: 'personal' | 'public' }) {
  const { user } = useAuth()
  const [meals, setMeals] = useState<Meal[]>([])
  const [options, setOptions] = useState<Record<string, string[]>>({})
  const [draft, setDraft] = useState<MealDraft>(emptyDraft)
  const [editingId, setEditingId] = useState<number>()
  const [open, setOpen] = useState(false)
  const [batchOpen, setBatchOpen] = useState(false)
  const [planMeal, setPlanMeal] = useState<Meal>()
  const [expandOpen, setExpandOpen] = useState(false)
  const [aiCompleting, setAiCompleting] = useState(false)
  const [selected, setSelected] = useState<Set<number>>(new Set())
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
    const { name, imageUrl, mealTime, mood, scene, healthGoal, cuisine, taste, convenience, acquisitionMode, prepMinutes, difficulty, priceMin, priceMax, defaultServings, ingredients, steps, dineOutTips, substitutes, nutrition } = meal
    setDraft({ name, imageUrl: imageUrl || '', mealTime, mood, scene, healthGoal, cuisine, taste, convenience, acquisitionMode: acquisitionMode || 'BOTH', prepMinutes, difficulty, priceMin, priceMax, defaultServings: defaultServings || 1, ingredients: ingredients || [], steps: steps || [], dineOutTips, substitutes: substitutes || [], nutrition })
    setEditingId(meal.id); setOpen(true)
  }
  function close() { setOpen(false); setEditingId(undefined); setDraft(emptyDraft) }
  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!draft.name.trim()) return
    try {
      if (mode === 'public') {
        if (editingId) await api.updatePublicMeal(editingId, draft); else await api.createPublicMeal(draft)
      } else {
        if (editingId) await api.updateMeal(editingId, draft); else await api.createMeal(draft)
      }
      setNotice({ message: editingId ? '餐食已更新' : '餐食已加入你的清单', tone: 'success' })
      close(); await load()
    } catch (error) { setNotice({ message: error instanceof Error ? error.message : '保存失败', tone: 'error' }) }
  }
  async function completeWithAi() {
    if (!draft.name.trim() || aiCompleting) return
    setAiCompleting(true)
    try {
      setDraft(await api.completeMeal(draft))
      setNotice({ message: 'AI 已补全空白信息，你可以继续修改后再保存', tone: 'success' })
    } catch (error) { setNotice({ message: error instanceof Error ? error.message : 'AI 补全失败', tone: 'error' }) }
    finally { setAiCompleting(false) }
  }
  async function remove(meal: Meal) {
    if (!window.confirm(`确定删除「${meal.name}」吗？`)) return
    try { if (mode === 'public') await api.deletePublicMeal(meal.id); else await api.deleteMeal(meal.id); await load(); setNotice({ message: '已删除', tone: 'success' }) }
    catch (error) { setNotice({ message: error instanceof Error ? error.message : '删除失败', tone: 'error' }) }
  }
  function toggle(key: SlotKey, value: string) {
    const current = draft[key]
    if (!Array.isArray(current)) return
    setDraft({ ...draft, [key]: current.includes(value) ? current.filter((item) => item !== value) : [...current, value] })
  }

  const personal = mode === 'personal'
  const editable = personal || user?.role === 'ADMIN'
  async function deleteSelected() {
    if (!selected.size || !window.confirm(`确定批量删除选中的 ${selected.size} 道公共餐食吗？`)) return
    try {
      const result = await api.bulkPublicMeals({ creates: [], updates: [], deleteIds: [...selected] })
      setSelected(new Set())
      await load()
      setNotice({ message: `已批量删除 ${result.deleted} 道公共餐食`, tone: 'success' })
    } catch (cause) { setNotice({ message: cause instanceof Error ? cause.message : '批量删除失败', tone: 'error' }) }
  }
  function toggleSelected(id: number) {
    setSelected((current) => { const next = new Set(current); if (next.has(id)) next.delete(id); else next.add(id); return next })
  }
  return <section className="content-page">
    <header className="page-header row-header"><div><p className="eyebrow">{personal ? '你的味觉档案' : '基础餐食库'}</p><h1>{personal ? '我的餐食' : '公共餐食'}</h1><p>{personal ? '把常吃、想再吃的餐食整理在这里。' : '所有人都可以使用的推荐基础。'}</p></div>
      {editable && <div className="meal-header-actions">{personal && <button type="button" className="secondary-button" onClick={() => setExpandOpen(true)}><Sparkles size={17} />AI 扩充餐食</button>}{!personal && <button type="button" className="secondary-button" onClick={() => setBatchOpen(true)}><SquarePen size={17} />批量新增 / 修改</button>}<button type="button" className="primary-button" onClick={() => setOpen(true)}><Plus size={17} />添加餐食</button></div>}
    </header>
    <Notice {...notice} />
    {!personal && editable && selected.size > 0 && <div className="bulk-delete-bar"><span>已选择 {selected.size} 道</span><button type="button" className="secondary-button danger-text" onClick={() => void deleteSelected()}><Trash2 size={16} />批量删除</button></div>}
    {loading ? <div className="loading-line">正在整理餐食…</div> : meals.length === 0 ? <div className="empty-state"><h2>这里还没有餐食</h2><p>添加几道你熟悉的味道，推荐会更贴近你。</p></div> : <div className="meal-list">
      {meals.map((meal) => <div className={!personal && editable ? 'selectable-meal' : ''} key={meal.id}>{!personal && editable && <label className="meal-select"><input type="checkbox" checked={selected.has(meal.id)} onChange={() => toggleSelected(meal.id)} aria-label={`选择${meal.name}`} /><span>选择</span></label>}<MealCard meal={meal} editable={editable} onEdit={() => edit(meal)} onDelete={() => remove(meal)} onPlan={() => setPlanMeal(meal)} /></div>)}
    </div>}

    {open && <div className="dialog-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) close() }}>
      <form className="dialog meal-form" onSubmit={submit}>
        <div className="dialog-title"><div><small>{editingId ? '编辑餐食' : '添加餐食'}</small><h2>记录一道熟悉的味道</h2></div><button type="button" className="icon-button" onClick={close}><X size={20} /></button></div>
        <div className="field"><span className="field-heading"><label htmlFor="meal-name"><b>餐食名称</b></label><button type="button" className="ai-fill-button" aria-label="AI 补全" disabled={!draft.name.trim() || aiCompleting} onClick={() => void completeWithAi()}><WandSparkles size={15} />{aiCompleting ? 'AI 补全中…' : 'AI 补全'}</button></span><input id="meal-name" autoFocus value={draft.name} onChange={(event) => setDraft({ ...draft, name: event.target.value })} placeholder="例如：土豆炖牛肉" /></div>
        <label className="field"><span>图片地址（选填）</span><input value={draft.imageUrl || ''} onChange={(event) => setDraft({ ...draft, imageUrl: event.target.value })} placeholder="例如：https://…/meal.jpg 或 /meals/meal.jpg" /></label>
        <div className="form-grid meal-detail-fields"><label className="field"><span>获取方式</span><select value={draft.acquisitionMode || 'BOTH'} onChange={(e) => setDraft({ ...draft, acquisitionMode: e.target.value as MealDraft['acquisitionMode'] })}><option value="BOTH">做饭或外食</option><option value="COOK">做饭</option><option value="EAT_OUT">外食</option></select></label><label className="field"><span>预计耗时（分钟）</span><input type="number" min="0" value={draft.prepMinutes ?? ''} onChange={(e) => setDraft({ ...draft, prepMinutes: e.target.value ? Number(e.target.value) : undefined })} /></label><label className="field"><span>难度</span><input value={draft.difficulty || ''} onChange={(e) => setDraft({ ...draft, difficulty: e.target.value })} placeholder="简单 / 适中 / 进阶" /></label><label className="field"><span>默认份数</span><input type="number" min="1" max="20" value={draft.defaultServings || 1} onChange={(e) => setDraft({ ...draft, defaultServings: Number(e.target.value) })} /></label><label className="field"><span>最低价格</span><input type="number" min="0" step="0.01" value={draft.priceMin ?? ''} onChange={(e) => setDraft({ ...draft, priceMin: e.target.value ? Number(e.target.value) : undefined })} /></label><label className="field"><span>最高价格</span><input type="number" min="0" step="0.01" value={draft.priceMax ?? ''} onChange={(e) => setDraft({ ...draft, priceMax: e.target.value ? Number(e.target.value) : undefined })} /></label></div>
        <MealDetailEditors draft={draft} onChange={setDraft} />
        <label className="field"><span>外食点餐建议</span><textarea value={draft.dineOutTips || ''} onChange={(e) => setDraft({ ...draft, dineOutTips: e.target.value })} /></label>
        <label className="field"><span>可替换菜品（每行一个）</span><textarea value={(draft.substitutes || []).join('\n')} onChange={(e) => setDraft({ ...draft, substitutes: lines(e.target.value) })} /></label>
        <fieldset className="choice-field"><legend>营养估算（可选）</legend><div className="nutrition-inputs">{(['calories', 'protein', 'fat', 'carbs'] as const).map((key) => <label className="field" key={key}><span>{{ calories: '热量 kcal', protein: '蛋白质 g', fat: '脂肪 g', carbs: '碳水 g' }[key]}</span><input type="number" min="0" step="0.01" value={draft.nutrition?.[key] ?? ''} onChange={(e) => setDraft({ ...draft, nutrition: { ...draft.nutrition, [key]: e.target.value ? Number(e.target.value) : undefined } })} /></label>)}</div></fieldset>
        {fields.map(({ key, label }) => <fieldset className="choice-field" key={key}><legend>{label}</legend><div>{(options[key] || []).map((value) => <button type="button" key={value} className={(draft[key] as string[]).includes(value) ? 'selected' : ''} onClick={() => toggle(key, value)}>{value}</button>)}</div></fieldset>)}
        <div className="dialog-actions"><button type="button" className="secondary-button" onClick={close}>取消</button><button className="primary-button">{editingId ? '保存修改' : '加入清单'}</button></div>
      </form>
    </div>}
    {batchOpen && <PublicBatchDialog meals={meals} options={options} onClose={() => setBatchOpen(false)} onSaved={(message) => { setBatchOpen(false); setNotice({ message, tone: 'success' }); void load() }} />}
    {planMeal && <AddToPlanDialog meal={planMeal} onClose={() => setPlanMeal(undefined)} onAdded={() => setNotice({ message: `已将「${planMeal.name}」加入计划`, tone: 'success' })} />}
    {expandOpen && <AiExpandDialog onClose={() => setExpandOpen(false)} onCreated={(count) => { setExpandOpen(false); setNotice({ message: `AI 已为你新增 ${count} 道个人餐食`, tone: 'success' }); void load() }} />}
  </section>
}

function AiExpandDialog({ onClose, onCreated }: { onClose: () => void; onCreated: (count: number) => void }) {
  const [preference, setPreference] = useState('')
  const [count, setCount] = useState(3)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  async function generate(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError('')
    try { const created = await api.expandPersonalMeals({ preference: preference.trim() || undefined, count }); onCreated(created.length) }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'AI 扩充失败') }
    finally { setBusy(false) }
  }
  return <div className="dialog-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget && !busy) onClose() }}><form className="dialog ai-expand-dialog" onSubmit={generate}>
    <div className="dialog-title"><div><small>结合长期偏好</small><h2>AI 扩充个人餐食</h2></div><button type="button" className="icon-button" disabled={busy} onClick={onClose}><X size={20} /></button></div>
    <p className="dialog-intro">AI 会参考“我的偏好”和已有个人餐食，避免重复。你还可以写下这一次的额外要求。</p>
    <label className="field"><span>这次想增加什么餐食？（选填）</span><textarea value={preference} onChange={(event) => setPreference(event.target.value)} placeholder="例如：适合工作日带饭，预算 20 元以内，少辣，多一些鸡肉和蔬菜" rows={4} /></label>
    <label className="field"><span>生成数量</span><select value={count} onChange={(event) => setCount(Number(event.target.value))}>{[1, 3, 5, 8].map((value) => <option key={value} value={value}>{value} 道</option>)}</select></label>
    {error && <p className="inline-error" role="alert">{error}</p>}
    <div className="dialog-actions"><button type="button" className="secondary-button" disabled={busy} onClick={onClose}>取消</button><button className="primary-button" disabled={busy}><Sparkles size={17} />{busy ? '正在生成并保存…' : '生成并加入个人餐食'}</button></div>
  </form></div>
}

function lines(value: string) { return value.split(/\r?\n/).map((item) => item.trim()).filter(Boolean) }
