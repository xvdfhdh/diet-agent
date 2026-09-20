import { FormEvent, useState } from 'react'
import { CalendarPlus, X } from 'lucide-react'
import { api } from '../lib/api'
import type { AcquisitionMode, Meal, MealPeriod } from '../types'

const periods: Array<{ value: MealPeriod; label: string }> = [
  { value: 'BREAKFAST', label: '早餐' }, { value: 'LUNCH', label: '午餐' },
  { value: 'DINNER', label: '晚餐' }, { value: 'SNACK', label: '加餐' },
]

export function AddToPlanDialog({ meal, onClose, onAdded }: { meal: Meal; onClose: () => void; onAdded?: () => void }) {
  const [date, setDate] = useState(today())
  const [period, setPeriod] = useState<MealPeriod>('LUNCH')
  const [mode, setMode] = useState<AcquisitionMode>(meal.acquisitionMode === 'EAT_OUT' ? 'EAT_OUT' : 'COOK')
  const [servings, setServings] = useState(meal.defaultServings || 1)
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  async function submit(event: FormEvent) {
    event.preventDefault(); setSaving(true); setError('')
    try { await api.addPlanItem({ planDate: date, mealPeriod: period, mealId: meal.id, acquisitionMode: mode, servings }); onAdded?.(); onClose() }
    catch (cause) { setError(cause instanceof Error ? cause.message : '加入计划失败') }
    finally { setSaving(false) }
  }
  const supportsBoth = !meal.acquisitionMode || meal.acquisitionMode === 'BOTH'
  return <div className="dialog-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}>
    <form className="dialog plan-dialog" onSubmit={submit}>
      <div className="dialog-title"><div><small>加入一周计划</small><h2>{meal.name}</h2></div><button type="button" className="icon-button" onClick={onClose}><X size={20} /></button></div>
      <div className="form-grid"><label className="field"><span>日期</span><input type="date" value={date} onChange={(e) => setDate(e.target.value)} required /></label>
        <label className="field"><span>餐次</span><select value={period} onChange={(e) => setPeriod(e.target.value as MealPeriod)}>{periods.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}</select></label>
        <label className="field"><span>份数</span><input type="number" min="1" max="20" value={servings} onChange={(e) => setServings(Number(e.target.value))} /></label>
        <label className="field"><span>获取方式</span><select value={mode} onChange={(e) => setMode(e.target.value as AcquisitionMode)}>
          {(supportsBoth || meal.acquisitionMode === 'COOK') && <option value="COOK">自己做</option>}
          {(supportsBoth || meal.acquisitionMode === 'EAT_OUT') && <option value="EAT_OUT">外食</option>}
        </select></label></div>
      <p className="form-hint">该日期餐次已有安排时，会用这道餐食替换。</p>
      {error && <p className="inline-error">{error}</p>}
      <div className="dialog-actions"><button type="button" className="secondary-button" onClick={onClose}>取消</button><button className="primary-button" disabled={saving}><CalendarPlus size={17} />{saving ? '保存中…' : '加入计划'}</button></div>
    </form>
  </div>
}

function today() { return new Date(Date.now() - new Date().getTimezoneOffset() * 60_000).toISOString().slice(0, 10) }
