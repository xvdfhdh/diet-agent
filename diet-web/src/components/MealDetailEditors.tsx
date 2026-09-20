import { Plus, Trash2 } from 'lucide-react'
import type { MealDraft, MealIngredient } from '../types'

export function MealDetailEditors({ draft, onChange }: { draft: MealDraft; onChange: (draft: MealDraft) => void }) {
  const ingredients = draft.ingredients || []
  const steps = draft.steps || []

  function updateIngredient(index: number, patch: Partial<MealIngredient>) {
    onChange({ ...draft, ingredients: ingredients.map((item, itemIndex) => itemIndex === index ? { ...item, ...patch } : item) })
  }
  function addIngredient() {
    onChange({ ...draft, ingredients: [...ingredients, { name: '', category: '其他', quantity: undefined, unit: '' }] })
  }
  function removeIngredient(index: number) {
    onChange({ ...draft, ingredients: ingredients.filter((_, itemIndex) => itemIndex !== index) })
  }
  function updateStep(index: number, value: string) {
    onChange({ ...draft, steps: steps.map((step, stepIndex) => stepIndex === index ? value : step) })
  }
  function addStep(afterIndex?: number) {
    const next = [...steps]
    next.splice(afterIndex == null ? next.length : afterIndex + 1, 0, '')
    onChange({ ...draft, steps: next })
  }
  function removeStep(index: number) {
    onChange({ ...draft, steps: steps.filter((_, stepIndex) => stepIndex !== index) })
  }

  return <div className="meal-array-editors">
    <fieldset className="array-editor"><legend><span>食材</span><small>每项分别填写，数量与单位可留空</small></legend>
      <div className="ingredient-editor-head"><span>名称</span><span>分类</span><span>数量</span><span>单位</span><span /></div>
      {ingredients.map((ingredient, index) => <div className="ingredient-editor-row" key={index}>
        <input aria-label={`食材 ${index + 1} 名称`} value={ingredient.name} onChange={(event) => updateIngredient(index, { name: event.target.value })} placeholder="番茄" />
        <input aria-label={`食材 ${index + 1} 分类`} value={ingredient.category} onChange={(event) => updateIngredient(index, { category: event.target.value })} placeholder="蔬菜" />
        <input aria-label={`食材 ${index + 1} 数量`} type="number" min="0" step="0.01" value={ingredient.quantity ?? ''} onChange={(event) => updateIngredient(index, { quantity: event.target.value ? Number(event.target.value) : undefined })} placeholder="2" />
        <input aria-label={`食材 ${index + 1} 单位`} value={ingredient.unit || ''} onChange={(event) => updateIngredient(index, { unit: event.target.value })} placeholder="个" />
        <button type="button" className="icon-button danger" aria-label={`删除食材 ${index + 1}`} onClick={() => removeIngredient(index)}><Trash2 size={15} /></button>
      </div>)}
      {ingredients.length === 0 && <p className="array-empty">暂未添加食材</p>}
      <button type="button" className="array-add-button" onClick={addIngredient}><Plus size={15} />添加食材</button>
    </fieldset>

    <fieldset className="array-editor"><legend><span>制作步骤</span><small>一项就是一步，按回车可继续添加</small></legend>
      {steps.map((step, index) => <div className="step-editor-row" key={index}><b>{index + 1}</b>
        <input aria-label={`步骤 ${index + 1}`} value={step} onChange={(event) => updateStep(index, event.target.value)} onKeyDown={(event) => {
          if (event.key === 'Enter') { event.preventDefault(); addStep(index) }
        }} placeholder="描述这一步怎么做" />
        <button type="button" className="icon-button danger" aria-label={`删除步骤 ${index + 1}`} onClick={() => removeStep(index)}><Trash2 size={15} /></button>
      </div>)}
      {steps.length === 0 && <p className="array-empty">暂未添加制作步骤</p>}
      <button type="button" className="array-add-button" onClick={() => addStep()}><Plus size={15} />添加步骤</button>
    </fieldset>
  </div>
}
