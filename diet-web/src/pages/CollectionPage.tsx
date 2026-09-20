import { useEffect, useState } from 'react'
import { Bookmark, Clock3 } from 'lucide-react'
import { MealCard } from '../components/MealCard'
import { Notice } from '../components/Notice'
import { api } from '../lib/api'
import type { FavoriteMeal, RecommendationHistory } from '../types'
import type { Meal } from '../types'
import { AddToPlanDialog } from '../components/AddToPlanDialog'

export function CollectionPage() {
  const [favorites, setFavorites] = useState<FavoriteMeal[]>([])
  const [history, setHistory] = useState<RecommendationHistory[]>([])
  const [loading, setLoading] = useState(true)
  const [notice, setNotice] = useState({ message: '', tone: 'success' as 'success' | 'error' })
  const [planMeal, setPlanMeal] = useState<Meal>()

  useEffect(() => {
    let active = true
    Promise.all([api.favorites(), api.recommendationHistory(50, 'all')])
      .then(([saved, records]) => { if (active) { setFavorites(saved); setHistory(records) } })
      .catch((error) => { if (active) setNotice({ message: error instanceof Error ? error.message : '收藏与历史加载失败', tone: 'error' }) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [])

  async function removeFavorite(mealId: number, name: string) {
    try {
      await api.removeFavorite(mealId)
      setFavorites((items) => items.filter((item) => item.meal.id !== mealId))
      setNotice({ message: `已取消收藏「${name}」`, tone: 'success' })
    } catch (error) {
      setNotice({ message: error instanceof Error ? error.message : '取消收藏失败', tone: 'error' })
    }
  }

  return <section className="content-page collection-page">
    <header className="page-header"><p className="eyebrow">你的味觉足迹</p><h1>收藏与历史</h1><p>找回喜欢的餐食，也能直接对食刻说“上次那个类似的再推荐一下”。</p></header>
    <Notice {...notice} />
    {loading ? <div className="loading-line">正在整理你的味觉足迹…</div> : <>
      <section className="collection-section" aria-labelledby="favorites-title">
        <div className="section-heading"><div><Bookmark size={18} /><h2 id="favorites-title">我的收藏</h2></div><small>{favorites.length} 道</small></div>
        {favorites.length === 0 ? <div className="empty-state compact-empty"><h3>还没有收藏</h3><p>在推荐卡片上点“收藏”，它会出现在这里。</p></div> :
          <div className="meal-list collection-grid">{favorites.map(({ meal }) => <MealCard key={meal.id} meal={meal} favorited onFavorite={() => removeFavorite(meal.id, meal.name)} onPlan={() => setPlanMeal(meal)} />)}</div>}
      </section>
      <section className="collection-section" aria-labelledby="history-title">
        <div className="section-heading"><div><Clock3 size={18} /><h2 id="history-title">历史推荐</h2></div><small>{history.length} 次</small></div>
        {history.length === 0 ? <div className="empty-state compact-empty"><h3>暂无历史推荐</h3><p>每次生成过餐食卡片的推荐都会自动保留。</p></div> :
          <div className="full-history-list">{history.map((record) => <article className="full-history-entry" key={record.id}>
            <header><div><time dateTime={record.createdAt}>{formatDate(record.createdAt)}</time><span>{record.sourceMode === 'PERSONAL' ? '我的餐食' : '公共餐食'}</span></div><p>“{record.userInput}”</p></header>
            <div className="history-meal-grid">{record.meals.map((meal) => <MealCard key={`${record.id}-${meal.id}`} meal={meal} compact onPlan={() => setPlanMeal(meal)} />)}</div>
          </article>)}</div>}
      </section>
    </>}
    {planMeal && <AddToPlanDialog meal={planMeal} onClose={() => setPlanMeal(undefined)} onAdded={() => setNotice({ message: `已将「${planMeal.name}」加入计划`, tone: 'success' })} />}
  </section>
}

function formatDate(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false }).format(date)
}
