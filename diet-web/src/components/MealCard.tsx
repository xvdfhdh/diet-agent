import { Clock3, Heart, Pencil, Trash2 } from 'lucide-react'
import type { Meal } from '../types'

function imageFor(name: string) {
  if (name.includes('番茄') || name.includes('鸡蛋面')) return '/meals/tomato-egg-noodles.jpg'
  if (name.includes('馄饨') || name.includes('清汤')) return '/meals/clear-wonton.jpg'
  if (name.includes('鸡胸') || name.includes('轻食')) return '/meals/chicken-grain-bowl.jpg'
  return '/meals/chicken-grain-bowl.jpg'
}

export function MealCard({ meal, compact = false, editable = false, onEdit, onDelete, onFeedback }: {
  meal: Meal
  compact?: boolean
  editable?: boolean
  onEdit?: () => void
  onDelete?: () => void
  onFeedback?: () => void
}) {
  const tags = [...meal.cuisine, ...meal.taste, ...meal.healthGoal].slice(0, 4)
  return (
    <article className={`meal-card ${compact ? 'compact' : ''}`}>
      <img src={imageFor(meal.name)} alt={`${meal.name}餐食示意`} />
      <div className="meal-card-body">
        <div className="meal-card-title">
          <h3>{meal.name}</h3>
          {meal.matchScore > 0 && <span className="score">{Math.round(meal.matchScore * (meal.matchScore <= 1 ? 100 : 1))}%</span>}
        </div>
        <div className="meal-meta">
          {meal.convenience[0] && <span><Clock3 size={14} />{meal.convenience[0]}</span>}
          {tags.map((tag) => <span className="tag" key={tag}>{tag}</span>)}
        </div>
        {(editable || onFeedback) && <div className="meal-actions">
          {onFeedback && <button className="text-button" onClick={onFeedback}><Heart size={15} />合口味</button>}
          {editable && <>
            <button className="icon-button" aria-label={`编辑${meal.name}`} onClick={onEdit}><Pencil size={16} /></button>
            <button className="icon-button danger" aria-label={`删除${meal.name}`} onClick={onDelete}><Trash2 size={16} /></button>
          </>}
        </div>}
      </div>
    </article>
  )
}
