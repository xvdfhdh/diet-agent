import type { PropsWithChildren } from 'react'
import { BarChart3, BookOpenText, Bookmark, Brain, CalendarDays, History, LogOut, Settings2, ShoppingBasket, Sparkles, UtensilsCrossed } from 'lucide-react'
import { NavLink } from 'react-router-dom'
import { useAuth } from '../lib/AuthContext'

const navItems = [
  { to: '/', label: '今日推荐', icon: Sparkles },
  { to: '/plan', label: '本周计划', icon: CalendarDays },
  { to: '/shopping', label: '购物清单', icon: ShoppingBasket },
  { to: '/meals/personal', label: '我的餐食', icon: UtensilsCrossed },
  { to: '/collection', label: '收藏与历史', icon: Bookmark },
  { to: '/preferences', label: '我的偏好', icon: Brain },
  { to: '/meals/public', label: '公共餐食', icon: BookOpenText },
]

const adminItems = [
  { to: '/traces', label: '运行记录', icon: History },
  { to: '/evaluations', label: '效果评测', icon: BarChart3 },
]
const mobileItems = [navItems[0], navItems[1], navItems[2], navItems[4]]

export function AppShell({ children }: PropsWithChildren) {
  const { user, logout } = useAuth()
  const visibleNav = user?.role === 'ADMIN' ? [...navItems, ...adminItems] : navItems
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <NavLink to="/" className="brand" aria-label="食刻首页">
          <span className="brand-mark">食</span>
          <span><strong>食刻</strong><small>好好吃每一餐</small></span>
        </NavLink>
        <nav className="primary-nav" aria-label="主导航">
          {visibleNav.map(({ to, label, icon: Icon }) => (
            <NavLink key={to} to={to} end={to === '/'} className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}>
              <Icon size={18} strokeWidth={1.8} />
              <span>{label}</span>
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-footer">
          {user?.role === 'ADMIN' && <NavLink to="/settings/models" className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}>
            <Settings2 size={18} strokeWidth={1.8} />
            <span>模型设置</span>
          </NavLink>}
          <div className="signed-in-user"><span>{user?.username}</span><small>{user?.role === 'ADMIN' ? '管理员' : '普通用户'}</small></div>
          <button type="button" className="nav-link logout-link" onClick={() => void logout()}><LogOut size={18} /><span>退出登录</span></button>
        </div>
      </aside>
      <main className="page-shell">{children}</main>
      <nav className="mobile-nav" aria-label="移动端导航">
        {mobileItems.map(({ to, label, icon: Icon }) => (
          <NavLink key={to} to={to} end={to === '/'} className={({ isActive }) => isActive ? 'active' : ''}>
            <Icon size={19} /><span>{label.replace('今日', '')}</span>
          </NavLink>
        ))}
        {user?.role === 'ADMIN' ? <NavLink to="/settings/models" className={({ isActive }) => isActive ? 'active' : ''}>
          <Settings2 size={19} /><span>设置</span>
        </NavLink> : <button type="button" onClick={() => void logout()}><LogOut size={19} /><span>退出</span></button>}
      </nav>
    </div>
  )
}
