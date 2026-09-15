import type { PropsWithChildren } from 'react'
import { BarChart3, BookOpenText, Bookmark, Brain, History, Settings2, Sparkles, UtensilsCrossed } from 'lucide-react'
import { NavLink } from 'react-router-dom'

const navItems = [
  { to: '/', label: '今日推荐', icon: Sparkles },
  { to: '/meals/personal', label: '我的餐食', icon: UtensilsCrossed },
  { to: '/collection', label: '收藏与历史', icon: Bookmark },
  { to: '/preferences', label: '我的偏好', icon: Brain },
  { to: '/meals/public', label: '公共餐食', icon: BookOpenText },
  { to: '/traces', label: '运行记录', icon: History },
  { to: '/evaluations', label: '效果评测', icon: BarChart3 },
]

export function AppShell({ children }: PropsWithChildren) {
  const currentUser = localStorage.getItem('diet.userId') || '1'
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <NavLink to="/" className="brand" aria-label="食刻首页">
          <span className="brand-mark">食</span>
          <span><strong>食刻</strong><small>好好吃每一餐</small></span>
        </NavLink>
        <nav className="primary-nav" aria-label="主导航">
          {navItems.map(({ to, label, icon: Icon }) => (
            <NavLink key={to} to={to} end={to === '/'} className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}>
              <Icon size={18} strokeWidth={1.8} />
              <span>{label}</span>
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-footer">
          <NavLink to="/settings/models" className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}>
            <Settings2 size={18} strokeWidth={1.8} />
            <span>模型设置</span>
          </NavLink>
          <label className="user-field">
            <span>当前用户</span>
            <input aria-label="当前用户 ID" defaultValue={currentUser} inputMode="numeric" onBlur={(event) => {
              const value = event.currentTarget.value.trim() || '1'
              localStorage.setItem('diet.userId', value)
              event.currentTarget.value = value
            }} />
          </label>
        </div>
      </aside>
      <main className="page-shell">{children}</main>
      <nav className="mobile-nav" aria-label="移动端导航">
        {navItems.slice(0, 4).map(({ to, label, icon: Icon }) => (
          <NavLink key={to} to={to} end={to === '/'} className={({ isActive }) => isActive ? 'active' : ''}>
            <Icon size={19} /><span>{label.replace('今日', '')}</span>
          </NavLink>
        ))}
        <NavLink to="/settings/models" className={({ isActive }) => isActive ? 'active' : ''}>
          <Settings2 size={19} /><span>设置</span>
        </NavLink>
      </nav>
    </div>
  )
}
