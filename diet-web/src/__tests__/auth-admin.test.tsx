// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import App from '../App'
import type { Meal } from '../types'

const mocked = vi.hoisted(() => ({
  me: vi.fn(), login: vi.fn(), register: vi.fn(), logout: vi.fn(),
  meals: vi.fn(), slotOptions: vi.fn(), bulkPublicMeals: vi.fn(),
  recommendationHistory: vi.fn(), memories: vi.fn(), favorites: vi.fn(),
}))

vi.mock('../lib/api', () => ({ api: mocked }))

const meal: Meal = {
  id: 3, sourceType: 'PUBLIC', name: '清汤馄饨', imageUrl: '/meals/clear-wonton.jpg',
  mealTime: ['午餐'], mood: [], scene: [], healthGoal: [], cuisine: [], taste: ['清淡'],
  convenience: [], matchScore: 0,
}

function page(route: string) {
  return render(<MemoryRouter initialEntries={[route]}><App /></MemoryRouter>)
}

beforeEach(() => {
  vi.clearAllMocks()
  sessionStorage.clear()
  mocked.me.mockResolvedValue({ id: 1000, username: 'ordinary', role: 'USER' })
  mocked.meals.mockResolvedValue([meal])
  mocked.slotOptions.mockResolvedValue({ mealTime: ['午餐', '晚餐'], taste: ['清淡'] })
  mocked.bulkPublicMeals.mockResolvedValue({ created: 0, updated: 1, deleted: 0 })
  mocked.recommendationHistory.mockResolvedValue([])
  mocked.memories.mockResolvedValue([])
  mocked.favorites.mockResolvedValue([])
})

afterEach(() => { cleanup(); vi.unstubAllGlobals() })

describe('登录与公共库角色界面', () => {
  it('未登录时先显示登录页；注册先校验确认密码，成功后保存登录态', async () => {
    page('/meals/public')
    expect(await screen.findByRole('heading', { name: '欢迎回来' })).toBeTruthy()
    expect(screen.queryByRole('heading', { name: '公共餐食' })).toBeNull()
    fireEvent.click(screen.getByRole('link', { name: '立即注册' }))
    expect(await screen.findByRole('heading', { name: '创建你的账户' })).toBeTruthy()
    fireEvent.change(screen.getByLabelText('用户名'), { target: { value: 'new_user' } })
    fireEvent.change(screen.getByLabelText('密码', { exact: true }), { target: { value: 'password123' } })
    fireEvent.change(screen.getByLabelText('确认密码'), { target: { value: 'not-the-same' } })
    fireEvent.click(screen.getByRole('button', { name: /注册并登录/ }))
    expect(screen.getByRole('alert').textContent).toContain('两次输入的密码不一致')
    expect(mocked.register).not.toHaveBeenCalled()
    mocked.register.mockResolvedValue({ token: 'registered-jwt', user: { id: 1002, username: 'new_user', role: 'USER' } })
    fireEvent.change(screen.getByLabelText('确认密码'), { target: { value: 'password123' } })
    fireEvent.click(screen.getByRole('button', { name: /注册并登录/ }))
    await waitFor(() => expect(mocked.register).toHaveBeenCalledWith({ username: 'new_user', password: 'password123' }))
    expect(await screen.findByText('new_user')).toBeTruthy()
    expect(sessionStorage.getItem('diet.auth.token')).toBe('registered-jwt')
  })

  it('正确登录后进入应用并保存 JWT', async () => {
    mocked.login.mockResolvedValue({ token: 'login-jwt', user: { id: 1000, username: 'ordinary', role: 'USER' } })
    page('/login')
    fireEvent.change(await screen.findByLabelText('用户名'), { target: { value: 'ordinary' } })
    fireEvent.change(screen.getByLabelText('密码'), { target: { value: 'password123' } })
    fireEvent.click(screen.getByRole('button', { name: '登录' }))
    await waitFor(() => expect(mocked.login).toHaveBeenCalledWith({ username: 'ordinary', password: 'password123' }))
    expect(await screen.findByText('ordinary')).toBeTruthy()
    expect(sessionStorage.getItem('diet.auth.token')).toBe('login-jwt')
  })

  it('失效 JWT 清除本地登录态；退出登录也清除令牌', async () => {
    sessionStorage.setItem('diet.auth.token', 'expired-jwt')
    mocked.me.mockRejectedValueOnce(new Error('已失效'))
    const rendered = page('/meals/public')
    expect(await screen.findByRole('heading', { name: '欢迎回来' })).toBeTruthy()
    expect(sessionStorage.getItem('diet.auth.token')).toBeNull()

    rendered.unmount()
    sessionStorage.setItem('diet.auth.token', 'valid-jwt')
    mocked.me.mockResolvedValue({ id: 1000, username: 'ordinary', role: 'USER' })
    mocked.logout.mockResolvedValue(undefined)
    page('/meals/public')
    fireEvent.click(await screen.findByRole('button', { name: '退出登录' }))
    await waitFor(() => expect(mocked.logout).toHaveBeenCalledOnce())
    expect(await screen.findByRole('heading', { name: '欢迎回来' })).toBeTruthy()
    expect(sessionStorage.getItem('diet.auth.token')).toBeNull()
  })

  it('普通用户能看公共库，但没有任何公共库增删改入口', async () => {
    sessionStorage.setItem('diet.auth.token', 'user-token')
    page('/meals/public')
    expect(await screen.findByRole('heading', { name: '公共餐食' })).toBeTruthy()
    expect(await screen.findByRole('heading', { name: meal.name })).toBeTruthy()
    expect(screen.queryByRole('button', { name: /批量新增 \/ 修改/ })).toBeNull()
    expect(screen.queryByRole('button', { name: '添加餐食' })).toBeNull()
    expect(screen.queryByRole('button', { name: `编辑${meal.name}` })).toBeNull()
    expect(screen.queryByRole('button', { name: `删除${meal.name}` })).toBeNull()
    expect(screen.queryByRole('checkbox')).toBeNull()
  })

  it('管理员可以批量新增两道餐食，界面提交一个批量请求', async () => {
    sessionStorage.setItem('diet.auth.token', 'admin-token')
    mocked.me.mockResolvedValue({ id: 1001, username: 'admin', role: 'ADMIN' })
    mocked.bulkPublicMeals.mockResolvedValue({ created: 2, updated: 0, deleted: 0 })
    page('/meals/public')
    fireEvent.click(await screen.findByRole('button', { name: /批量新增 \/ 修改/ }))
    const dialog = await screen.findByRole('dialog', { name: '批量新增与修改' })
    fireEvent.click(within(dialog).getByRole('button', { name: '新餐食' }))
    const rows = dialog.querySelectorAll('.batch-row')
    expect(rows).toHaveLength(2)
    rows.forEach((row, index) => {
      fireEvent.change(within(row as HTMLElement).getByLabelText('名称'), { target: { value: `新餐食${index + 1}` } })
      fireEvent.click(within(row as HTMLElement).getByRole('button', { name: '午餐' }))
    })
    fireEvent.click(within(dialog).getByRole('button', { name: '保存 2 道餐食' }))
    await waitFor(() => expect(mocked.bulkPublicMeals).toHaveBeenCalledWith({
      creates: [expect.objectContaining({ name: '新餐食1', mealTime: ['午餐'] }),
        expect.objectContaining({ name: '新餐食2', mealTime: ['午餐'] })],
      updates: [], deleteIds: [],
    }))
  })

  it('管理员可批量修改已有餐食并批量删除选中项', async () => {
    sessionStorage.setItem('diet.auth.token', 'admin-token')
    mocked.me.mockResolvedValue({ id: 1001, username: 'admin', role: 'ADMIN' })
    mocked.bulkPublicMeals.mockResolvedValueOnce({ created: 0, updated: 1, deleted: 0 })
      .mockResolvedValueOnce({ created: 0, updated: 0, deleted: 1 })
    vi.stubGlobal('confirm', vi.fn(() => true))
    page('/meals/public')
    fireEvent.click(await screen.findByRole('button', { name: /批量新增 \/ 修改/ }))
    const dialog = await screen.findByRole('dialog', { name: '批量新增与修改' })
    fireEvent.change(within(dialog).getByRole('combobox', { name: '选择要修改的公共餐食' }),
      { target: { value: String(meal.id) } })
    expect(within(dialog).getByText(/修改公共餐食/)).toBeTruthy()
    fireEvent.click(within(dialog).getByRole('button', { name: '保存 1 道餐食' }))
    await waitFor(() => expect(mocked.bulkPublicMeals).toHaveBeenNthCalledWith(1, {
      creates: [], updates: [{ id: meal.id, meal: expect.objectContaining({ name: meal.name, mealTime: ['午餐'] }) }],
      deleteIds: [],
    }))
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
    fireEvent.click(screen.getByRole('checkbox', { name: `选择${meal.name}` }))
    fireEvent.click(screen.getByRole('button', { name: '批量删除' }))
    await waitFor(() => expect(mocked.bulkPublicMeals).toHaveBeenNthCalledWith(2, {
      creates: [], updates: [], deleteIds: [meal.id],
    }))
  })
})
