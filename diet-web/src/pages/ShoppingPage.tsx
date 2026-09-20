import { FormEvent, useCallback, useEffect, useMemo, useState } from 'react'
import { Check, ChevronLeft, ChevronRight, Plus, RefreshCw, ShoppingBasket, Trash2 } from 'lucide-react'
import { Notice } from '../components/Notice'
import { api } from '../lib/api'
import type { ShoppingList } from '../types'

export function ShoppingPage() {
  const [weekStart, setWeekStart] = useState(monday(new Date()))
  const [list, setList] = useState<ShoppingList>()
  const [name, setName] = useState(''), [category, setCategory] = useState('其他'), [quantity, setQuantity] = useState(''), [unit, setUnit] = useState('')
  const [loading, setLoading] = useState(true), [syncing, setSyncing] = useState(false)
  const [notice, setNotice] = useState({ message: '', tone: 'success' as 'success' | 'error' })
  const weekKey = iso(weekStart)
  const load = useCallback(async () => { setLoading(true); try { setList(await api.shoppingList(weekKey)) } catch (cause) { fail(cause) } finally { setLoading(false) } }, [weekKey])
  useEffect(() => { void load() }, [load])
  const groups = useMemo(() => Object.entries((list?.items || []).reduce<Record<string, NonNullable<ShoppingList['items']>>>((result, item) => { (result[item.category || '其他'] ||= []).push(item); return result }, {})), [list])
  function fail(cause: unknown) { setNotice({ message: cause instanceof Error ? cause.message : '操作失败', tone: 'error' }) }
  async function sync() { setSyncing(true); try { setList(await api.syncShoppingList(weekKey)); setNotice({ message: '已从做饭计划重新聚合，手工添加项已保留', tone: 'success' }) } catch (cause) { fail(cause) } finally { setSyncing(false) } }
  async function add(event: FormEvent) { event.preventDefault(); if (!name.trim()) return; try { await api.addShoppingItem(weekKey, { name, category, quantity: quantity ? Number(quantity) : undefined, unit: unit || undefined }); setName(''); setQuantity(''); setUnit(''); await load() } catch (cause) { fail(cause) } }
  async function toggle(id: number, completed: boolean) { try { await api.updateShoppingItem(id, { completed }); await load() } catch (cause) { fail(cause) } }
  async function remove(id: number) { try { await api.deleteShoppingItem(id); await load() } catch (cause) { fail(cause) } }

  return <section className="content-page shopping-page"><header className="page-header row-header"><div><p className="eyebrow">从计划到备餐</p><h1>智能购物清单</h1><p>只聚合“自己做”的餐食；相同食材和单位自动合并。</p></div><button className="primary-button" onClick={() => void sync()} disabled={syncing}><RefreshCw size={17} />{syncing ? '同步中…' : '从计划同步'}</button></header>
    <Notice {...notice} />
    <div className="week-toolbar"><button className="icon-button" aria-label="上一周" onClick={() => setWeekStart(addDays(weekStart, -7))}><ChevronLeft /></button><strong>{weekKey} 当周</strong><button className="icon-button" aria-label="下一周" onClick={() => setWeekStart(addDays(weekStart, 7))}><ChevronRight /></button></div>
    {list?.needsSync && <div className="sync-banner"><ShoppingBasket size={18} /><span>计划在上次同步后有变化。点击“从计划同步”更新自动生成项，手工项不会被删除。</span></div>}
    <form className="shopping-add" onSubmit={add}><input aria-label="食材名称" value={name} onChange={(e) => setName(e.target.value)} placeholder="手工添加食材" /><input aria-label="分类" value={category} onChange={(e) => setCategory(e.target.value)} placeholder="分类" /><input aria-label="数量" type="number" min="0" step="0.01" value={quantity} onChange={(e) => setQuantity(e.target.value)} placeholder="数量" /><input aria-label="单位" value={unit} onChange={(e) => setUnit(e.target.value)} placeholder="单位" /><button className="secondary-button"><Plus size={16} />添加</button></form>
    {loading ? <div className="loading-line">正在取出购物清单…</div> : groups.length === 0 ? <div className="empty-state"><h2>购物袋还是空的</h2><p>先安排本周做饭计划，再同步清单。</p></div> : <div className="shopping-groups">{groups.map(([group, items]) => <section className="shopping-group" key={group}><h2>{group}</h2>{items.map((item) => <article className={item.completed ? 'shopping-item completed' : 'shopping-item'} key={item.id}><button className="check-button" aria-label={item.completed ? `取消勾选${item.name}` : `勾选${item.name}`} onClick={() => void toggle(item.id, !item.completed)}>{item.completed && <Check size={15} />}</button><div><strong>{item.name}</strong><span>{item.quantity ?? '适量'} {item.unit || ''}</span></div><small>{item.manual ? '手工添加' : `来自 ${item.sourceMealIds.length} 道餐食`}</small><button className="icon-button danger" aria-label={`删除${item.name}`} onClick={() => void remove(item.id)}><Trash2 size={16} /></button></article>)}</section>)}</div>}
  </section>
}

function monday(value: Date) { const date = new Date(value); date.setHours(12, 0, 0, 0); const day = date.getDay() || 7; date.setDate(date.getDate() - day + 1); return date }
function addDays(value: Date, days: number) { const result = new Date(value); result.setDate(result.getDate() + days); return result }
function iso(value: Date) { return new Date(value.getTime() - value.getTimezoneOffset() * 60_000).toISOString().slice(0, 10) }
