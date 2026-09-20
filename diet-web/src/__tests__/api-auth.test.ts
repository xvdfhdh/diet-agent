// @vitest-environment jsdom
import { afterEach, expect, it, vi } from 'vitest'
import { api } from '../lib/api'

afterEach(() => { sessionStorage.clear(); vi.unstubAllGlobals() })

it('业务请求只用 Bearer JWT，不再发送旧用户 ID 请求头', async () => {
  sessionStorage.setItem('diet.auth.token', 'signed-token')
  const fetch = vi.fn().mockResolvedValue({
    ok: true, status: 200, headers: { get: () => null }, json: async () => [],
  })
  vi.stubGlobal('fetch', fetch)

  await api.meals('public')

  expect(fetch).toHaveBeenCalledWith('/api/v1/diet/meals/public', expect.objectContaining({
    headers: expect.objectContaining({ Authorization: 'Bearer signed-token' }),
  }))
  expect(fetch.mock.calls[0][1].headers['X-User-Id']).toBeUndefined()
})

it('受保护接口返回 401 时清除本地 JWT', async () => {
  sessionStorage.setItem('diet.auth.token', 'expired-token')
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
    ok: false, status: 401, text: async () => '{"message":"登录已失效"}',
  }))

  await expect(api.me()).rejects.toThrow('登录已失效')
  expect(sessionStorage.getItem('diet.auth.token')).toBeNull()
})

it('计划、打卡和购物接口使用约定的路径与 JSON 请求体', async () => {
  sessionStorage.setItem('diet.auth.token', 'signed-token')
  const fetch = vi.fn().mockResolvedValue({ ok: true, status: 200, headers: { get: () => null }, json: async () => ({}) })
  vi.stubGlobal('fetch', fetch)
  await api.addPlanItem({ planDate: '2026-09-21', mealPeriod: 'LUNCH', mealId: 7, acquisitionMode: 'COOK', servings: 2 })
  await api.checkInPlanItem(9, { rating: 5, satiety: 4 })
  await api.syncShoppingList('2026-09-21')
  expect(fetch).toHaveBeenNthCalledWith(1, '/api/v1/diet/plans/items', expect.objectContaining({ method: 'POST', body: expect.stringContaining('"mealId":7') }))
  expect(fetch).toHaveBeenNthCalledWith(2, '/api/v1/diet/plans/items/9/check-in', expect.objectContaining({ method: 'POST', body: '{"rating":5,"satiety":4}' }))
  expect(fetch).toHaveBeenNthCalledWith(3, '/api/v1/diet/shopping-lists/sync?weekStart=2026-09-21', expect.objectContaining({ method: 'POST' }))
})
