import { useEffect, useState } from 'react'
import { ArrowLeft, CalendarPlus, Clock3, CookingPot, MapPin, Utensils } from 'lucide-react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { AddToPlanDialog } from '../components/AddToPlanDialog'
import { Notice } from '../components/Notice'
import { api } from '../lib/api'
import type { Meal } from '../types'

export function MealDetailPage() {
  const navigate = useNavigate()
  const { id } = useParams(), [meal, setMeal] = useState<Meal>(), [adding, setAdding] = useState(false)
  const [error, setError] = useState('')
  useEffect(() => { api.meal(Number(id)).then(setMeal).catch((cause) => setError(cause instanceof Error ? cause.message : '加载失败')) }, [id])
  if (error) return <section className="content-page"><Notice message={error} tone="error" /><Link to="/">返回首页</Link></section>
  if (!meal) return <div className="loading-line">正在准备餐食详情…</div>
  const cook = meal.acquisitionMode !== 'EAT_OUT', eatOut = meal.acquisitionMode !== 'COOK'
  return <section className="content-page meal-detail-page"><button type="button" className="back-link" onClick={() => navigate(-1)}><ArrowLeft size={16} />返回</button>
    <header className="meal-detail-hero"><img src={meal.imageUrl || '/meals/chicken-grain-bowl.jpg'} alt={meal.name} /><div><p className="eyebrow">{meal.sourceType === 'PUBLIC' ? '公共餐食' : '我的餐食'}</p><h1>{meal.name}</h1><div className="detail-facts">{meal.prepMinutes != null && <span><Clock3 size={16} />约 {meal.prepMinutes} 分钟</span>}<span><Utensils size={16} />{meal.defaultServings || 1} 份</span>{meal.difficulty && <span>{meal.difficulty}</span>}{meal.priceMin != null && <span>¥{meal.priceMin}{meal.priceMax != null ? `—${meal.priceMax}` : ' 起'}</span>}</div><div className="tag-row">{[...meal.cuisine, ...meal.taste, ...meal.healthGoal].map((tag) => <span className="tag" key={tag}>{tag}</span>)}</div><button className="primary-button" onClick={() => setAdding(true)}><CalendarPlus size={17} />加入计划</button></div></header>
    {meal.nutrition && <section className="nutrition-strip"><div><strong>{meal.nutrition.calories ?? '—'}</strong><span>千卡</span></div><div><strong>{meal.nutrition.protein ?? '—'}g</strong><span>蛋白质</span></div><div><strong>{meal.nutrition.fat ?? '—'}g</strong><span>脂肪</span></div><div><strong>{meal.nutrition.carbs ?? '—'}g</strong><span>碳水</span></div><small>营养数据为估算值</small></section>}
    <div className="detail-columns">{cook && <section className="detail-panel"><h2><CookingPot size={20} />自己做</h2><h3>食材</h3>{meal.ingredients?.length ? <ul className="ingredient-list">{meal.ingredients.map((item, index) => <li key={`${item.name}-${index}`}><span>{item.name}<small>{item.category}</small></span><strong>{item.quantity ?? '适量'} {item.unit || ''}</strong></li>)}</ul> : <p className="muted-copy">暂未录入食材。</p>}<h3>步骤</h3>{meal.steps?.length ? <ol className="step-list">{meal.steps.map((step, index) => <li key={step}><span>{index + 1}</span><p>{step}</p></li>)}</ol> : <p className="muted-copy">暂未录入步骤。</p>}</section>}
      {eatOut && <section className="detail-panel"><h2><MapPin size={20} />外食参考</h2><h3>点餐建议</h3><p>{meal.dineOutTips || '按自己的饥饿程度调整主食和份量。'}</p><h3>可替换菜品</h3>{meal.substitutes?.length ? <div className="substitute-list">{meal.substitutes.map((item) => <span key={item}>{item}</span>)}</div> : <p className="muted-copy">暂无替换建议。</p>}</section>}</div>
    {adding && <AddToPlanDialog meal={meal} onClose={() => setAdding(false)} />}
  </section>
}
