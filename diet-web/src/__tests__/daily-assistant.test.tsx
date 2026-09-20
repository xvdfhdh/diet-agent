// @vitest-environment jsdom
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { useState } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { MealDetailEditors } from '../components/MealDetailEditors'
import { PlanPage } from '../pages/PlanPage'
import { ShoppingPage } from '../pages/ShoppingPage'
import type { Meal, MealDraft, PlanItem, ShoppingList, WeeklySummary } from '../types'

const mocked = vi.hoisted(() => ({
  plans: vi.fn(), weeklySummary: vi.fn(), generatePlan: vi.fn(), replacePlanItem: vi.fn(),
  deletePlanItem: vi.fn(), updatePlanItem: vi.fn(), addPlanItem: vi.fn(), checkInPlanItem: vi.fn(),
  meals: vi.fn(),
  shoppingList: vi.fn(), syncShoppingList: vi.fn(), addShoppingItem: vi.fn(),
  updateShoppingItem: vi.fn(), deleteShoppingItem: vi.fn(),
}))
vi.mock('../lib/api', () => ({ api: mocked }))

const plan: PlanItem = {
  id: 8, planDate: new Date(Date.now() - new Date().getTimezoneOffset() * 60_000).toISOString().slice(0, 10),
  mealPeriod: 'LUNCH', mealId: 3, acquisitionMode: 'COOK', servings: 1, status: 'PLANNED',
  meal: { id: 3, sourceType: 'PUBLIC', name: '番茄鸡蛋饭', mealTime: ['午餐'], mood: [], scene: [], healthGoal: [], cuisine: [], taste: ['清淡'], convenience: [], matchScore: 0 },
}
const summary: WeeklySummary = { weekStart: plan.planDate, plannedCount: 1, completedCount: 0, skippedCount: 0, completionRate: 0, cookCount: 1, eatOutCount: 0, topTastes: ['清淡'], topCuisines: [], topHealthGoals: [], mostCompletedMeals: [], mostSkippedMeals: [], estimatedCost: 12, actualCost: 0 }
const shopping: ShoppingList = { id: 2, weekStart: plan.planDate, syncVersion: 1, status: 'DRAFT', needsSync: false, items: [{ id: 4, name: '番茄', category: '蔬菜', quantity: 2, unit: '个', sourceMealIds: [3], manual: false, completed: false }] }
const alternative: Meal = { ...plan.meal, id: 9, sourceType: 'PERSONAL', name: '鸡丝凉面', acquisitionMode: 'EAT_OUT' }

beforeEach(() => {
  vi.clearAllMocks(); mocked.plans.mockResolvedValue([plan]); mocked.weeklySummary.mockResolvedValue(summary)
  mocked.checkInPlanItem.mockResolvedValue({ ...plan, status: 'COMPLETED' }); mocked.shoppingList.mockResolvedValue(shopping)
  mocked.updateShoppingItem.mockResolvedValue({ ...shopping.items[0], completed: true })
  mocked.meals.mockImplementation((mode: string) => Promise.resolve(mode === 'personal' ? [alternative] : [plan.meal]))
  mocked.updatePlanItem.mockResolvedValue({ ...plan, mealId: alternative.id, meal: alternative })
})
afterEach(cleanup)

it('计划页可以打开打卡并提交评分和饱腹感', async () => {
  render(<MemoryRouter><PlanPage /></MemoryRouter>)
  expect(await screen.findByText('番茄鸡蛋饭')).toBeTruthy()
  fireEvent.click(screen.getByTitle('打卡'))
  fireEvent.change(screen.getByLabelText('评分（1—5）'), { target: { value: '5' } })
  fireEvent.change(screen.getByLabelText('饱腹感（1—5）'), { target: { value: '4' } })
  fireEvent.click(screen.getByRole('button', { name: '确认已吃' }))
  await waitFor(() => expect(mocked.checkInPlanItem).toHaveBeenCalledWith(8, expect.objectContaining({ rating: 5, satiety: 4, skipped: false })))
})

it('购物清单勾选操作只更新当前用户的具体购物项', async () => {
  render(<ShoppingPage />)
  expect(await screen.findByText('番茄')).toBeTruthy()
  fireEvent.click(screen.getByRole('button', { name: '勾选番茄' }))
  await waitFor(() => expect(mocked.updateShoppingItem).toHaveBeenCalledWith(4, { completed: true }))
})

it('餐食编辑器可逐项添加食材，并可按回车继续添加制作步骤', () => {
  const initial: MealDraft = { name: '番茄鸡蛋饭', mealTime: ['午餐'], mood: [], scene: [], healthGoal: [], cuisine: [], taste: [], convenience: [], ingredients: [], steps: [] }
  function Harness() { const [draft, setDraft] = useState(initial); return <MealDetailEditors draft={draft} onChange={setDraft} /> }
  render(<Harness />)
  fireEvent.click(screen.getByRole('button', { name: '添加食材' }))
  fireEvent.change(screen.getByLabelText('食材 1 名称'), { target: { value: '番茄' } })
  expect((screen.getByLabelText('食材 1 名称') as HTMLInputElement).value).toBe('番茄')
  fireEvent.click(screen.getByRole('button', { name: '添加步骤' }))
  fireEvent.change(screen.getByLabelText('步骤 1'), { target: { value: '鸡蛋打散' } })
  fireEvent.keyDown(screen.getByLabelText('步骤 1'), { key: 'Enter' })
  expect(screen.getByLabelText('步骤 2')).toBeTruthy()
})

it('点击周计划餐食可从个人库和公共库选择任意餐食', async () => {
  render(<MemoryRouter><PlanPage /></MemoryRouter>)
  fireEvent.click(await screen.findByRole('button', { name: '选择番茄鸡蛋饭的替换餐食' }))
  expect(await screen.findByRole('heading', { name: '选择计划餐食' })).toBeTruthy()
  fireEvent.click(screen.getByText('鸡丝凉面').closest('button')!)
  await waitFor(() => expect(mocked.updatePlanItem).toHaveBeenCalledWith(8, { mealId: 9, acquisitionMode: 'EAT_OUT' }))
})
