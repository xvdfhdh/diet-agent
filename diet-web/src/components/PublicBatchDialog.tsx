import { useState } from 'react'
import { Plus, X } from 'lucide-react'
import { api } from '../lib/api'
import type { Meal, MealDraft } from '../types'

type Row = { key: string; id?: number; draft: MealDraft }
const fields: Array<{ key: keyof MealDraft; label: string }> = [
  { key: 'mealTime', label: '时段' }, { key: 'mood', label: '心情' }, { key: 'scene', label: '场景' },
  { key: 'healthGoal', label: '饮食目标' }, { key: 'cuisine', label: '菜系' }, { key: 'taste', label: '口味' },
  { key: 'convenience', label: '便利偏好' },
]
const emptyDraft = (): MealDraft => ({ name: '', imageUrl: '', mealTime: [], mood: [], scene: [], healthGoal: [], cuisine: [], taste: [], convenience: [] })

function draftFromMeal(meal: Meal): MealDraft {
  return { name: meal.name, imageUrl: meal.imageUrl || '', mealTime: [...meal.mealTime], mood: [...meal.mood], scene: [...meal.scene], healthGoal: [...meal.healthGoal], cuisine: [...meal.cuisine], taste: [...meal.taste], convenience: [...meal.convenience] }
}

export function PublicBatchDialog({ meals, options, onClose, onSaved }: {
  meals: Meal[]
  options: Record<string, string[]>
  onClose: () => void
  onSaved: (message: string) => void
}) {
  const [rows, setRows] = useState<Row[]>([{ key: crypto.randomUUID(), draft: emptyDraft() }])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const usedIds = new Set(rows.map((row) => row.id).filter((id): id is number => id !== undefined))

  function updateRow(key: string, draft: MealDraft) {
    setRows((current) => current.map((row) => row.key === key ? { ...row, draft } : row))
  }

  function addExisting(id: number) {
    const meal = meals.find((item) => item.id === id)
    if (!meal || usedIds.has(id) || rows.length >= 100) return
    setRows((current) => {
      const onlyEmptyDraft = current.length === 1 && current[0].id === undefined && !current[0].draft.name.trim()
        && current[0].draft.mealTime.length === 0
      return [...(onlyEmptyDraft ? [] : current), { key: crypto.randomUUID(), id, draft: draftFromMeal(meal) }]
    })
  }

  async function save() {
    setError('')
    if (rows.some((row) => !row.draft.name.trim() || row.draft.mealTime.length === 0)) {
      setError('每行都需要餐食名称，并至少选择一个时段')
      return
    }
    setBusy(true)
    try {
      const result = await api.bulkPublicMeals({
        creates: rows.filter((row) => row.id === undefined).map((row) => row.draft),
        updates: rows.filter((row): row is Row & { id: number } => row.id !== undefined).map((row) => ({ id: row.id, meal: row.draft })),
        deleteIds: [],
      })
      onSaved(`批量完成：新增 ${result.created} 道，修改 ${result.updated} 道`)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '批量保存失败')
    } finally { setBusy(false) }
  }

  return <div className="dialog-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}>
    <div className="dialog batch-dialog" role="dialog" aria-modal="true" aria-labelledby="batch-title">
      <div className="dialog-title"><div><small>管理员 · 公共库</small><h2 id="batch-title">批量新增与修改</h2></div><button type="button" className="icon-button" aria-label="关闭批量编辑" onClick={onClose}><X size={20} /></button></div>
      <p className="batch-help">每行是一道餐食；可加入多个新餐食，或从现有公共库加入多道餐食进行修改。一次提交会整体成功或整体回滚。</p>
      <div className="batch-tools"><button type="button" className="secondary-button" disabled={rows.length >= 100} onClick={() => setRows((current) => [...current, { key: crypto.randomUUID(), draft: emptyDraft() }])}><Plus size={16} />新餐食</button>
        <label>修改已有餐食<select aria-label="选择要修改的公共餐食" value="" onChange={(event) => addExisting(Number(event.target.value))}><option value="">选择餐食…</option>{meals.filter((meal) => !usedIds.has(meal.id)).map((meal) => <option key={meal.id} value={meal.id}>{meal.name}</option>)}</select></label>
      </div>
      <div className="batch-rows">{rows.map((row, index) => <section className="batch-row" key={row.key}>
        <header><strong>{index + 1}. {row.id ? '修改公共餐食' : '新增公共餐食'}</strong><button type="button" className="icon-button" aria-label={`移除第 ${index + 1} 行`} onClick={() => setRows((current) => current.filter((item) => item.key !== row.key))}><X size={16} /></button></header>
        <div className="batch-basic"><label className="field"><span>名称</span><input value={row.draft.name} onChange={(event) => updateRow(row.key, { ...row.draft, name: event.target.value })} placeholder="餐食名称" /></label>
          <label className="field"><span>图片地址（选填）</span><input value={row.draft.imageUrl || ''} onChange={(event) => updateRow(row.key, { ...row.draft, imageUrl: event.target.value })} placeholder="/meals/… 或 https://…" /></label></div>
        {fields.map(({ key, label }) => <fieldset className="choice-field" key={key}><legend>{label}</legend><div>{(options[key] || []).map((value) => <button type="button" key={value} className={(row.draft[key] as string[]).includes(value) ? 'selected' : ''} onClick={() => {
          const selected = row.draft[key] as string[]
          updateRow(row.key, { ...row.draft, [key]: selected.includes(value) ? selected.filter((item) => item !== value) : [...selected, value] })
        }}>{value}</button>)}</div></fieldset>)}
      </section>)}</div>
      {error && <p className="auth-error" role="alert">{error}</p>}
      <div className="dialog-actions"><button type="button" className="secondary-button" onClick={onClose}>取消</button><button type="button" className="primary-button" disabled={busy || rows.length === 0} onClick={() => void save()}>{busy ? '提交中…' : `保存 ${rows.length} 道餐食`}</button></div>
    </div>
  </div>
}
