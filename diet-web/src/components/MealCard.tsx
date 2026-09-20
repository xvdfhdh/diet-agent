import { Bookmark, CalendarPlus, Clock3, Eye, Pencil, ThumbsDown, Trash2 } from 'lucide-react'
import { Link } from 'react-router-dom'
import type { Meal } from '../types'

const FALLBACK_IMAGE = '/meals/chicken-grain-bowl.jpg'

export function MealCard({ meal, compact = false, editable = false, favorited = false, onEdit, onDelete, onFeedback, onFavorite, onPlan }: {
  meal: Meal
  compact?: boolean
  editable?: boolean
  onEdit?: () => void
  onDelete?: () => void
  onFeedback?: (action: 'LIKE' | 'DISLIKE') => void
  onFavorite?: () => void
  onPlan?: () => void
  favorited?: boolean
}) {
  const tags = [...meal.cuisine, ...meal.taste, ...meal.healthGoal].slice(0, 4)
  return (
    <article className={`meal-card ${compact ? 'compact' : ''}`}>
      <img src={meal.imageUrl || FALLBACK_IMAGE} alt={`${meal.name}餐食示意`} onError={(event) => { event.currentTarget.src = FALLBACK_IMAGE }} />
      <div className="meal-card-body">
        <div className="meal-card-title">
          <h3>{meal.name}</h3>
          {meal.matchScore > 0 && <span className="score">{Math.round(meal.matchScore * (meal.matchScore <= 1 ? 100 : 1))}%</span>}
        </div>
        <div className="meal-meta">
          {meal.convenience[0] && <span><Clock3 size={14} />{meal.convenience[0]}</span>}
          {tags.map((tag) => <span className="tag" key={tag}>{tag}</span>)}
        </div>
        <div className="meal-actions">
          {meal.id > 0 && <Link className="text-button" to={`/meals/${meal.id}`}><Eye size={15} />详情</Link>}
          {onPlan && <button type="button" className="text-button" onClick={onPlan}><CalendarPlus size={15} />加入计划</button>}
          {onFavorite && <button type="button" className={`text-button ${favorited ? 'favorite-active' : ''}`} onClick={onFavorite}><Bookmark size={15} fill={favorited ? 'currentColor' : 'none'} />{favorited ? '已收藏' : '收藏'}</button>}
          {onFeedback && <>
            <button type="button" className="text-button muted" onClick={() => onFeedback('DISLIKE')}><ThumbsDown size={14} />不喜欢</button>
          </>}
          {editable && <>
            <button type="button" className="icon-button" aria-label={`编辑${meal.name}`} onClick={onEdit}><Pencil size={16} /></button>
            <button type="button" className="icon-button danger" aria-label={`删除${meal.name}`} onClick={onDelete}><Trash2 size={16} /></button>
          </>}
        </div>
      </div>
    </article>
  )
}
