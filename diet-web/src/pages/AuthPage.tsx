import { useState, type FormEvent } from 'react'
import { ArrowRight, LockKeyhole } from 'lucide-react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { api } from '../lib/api'
import { useAuth } from '../lib/AuthContext'

export function AuthPage({ mode }: { mode: 'login' | 'register' }) {
  const { user, accept } = useAuth()
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  if (user) return <Navigate to="/" replace />

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError('')
    if (mode === 'register' && password !== confirmPassword) {
      setError('两次输入的密码不一致')
      return
    }
    setBusy(true)
    try {
      const result = mode === 'login'
        ? await api.login({ username, password })
        : await api.register({ username, password })
      accept(result)
      navigate('/', { replace: true })
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '操作失败，请稍后重试')
    } finally { setBusy(false) }
  }

  return <main className="auth-screen">
    <div className="auth-brand"><span className="brand-mark">食</span><div><strong>食刻</strong><small>好好吃每一餐</small></div></div>
    <section className="auth-card">
      <div className="auth-icon"><LockKeyhole size={21} /></div>
      <p className="eyebrow">你的味觉旅程</p>
      <h1>{mode === 'login' ? '欢迎回来' : '创建你的账户'}</h1>
      <p className="auth-lead">{mode === 'login' ? '登录后继续查看餐食、收藏与长期偏好。' : '注册后即可建立属于自己的餐食与偏好档案。'}</p>
      <form onSubmit={submit}>
        <label className="field"><span>用户名</span><input autoComplete="username" required minLength={3} maxLength={32} pattern="[A-Za-z0-9_]+" value={username} onChange={(event) => setUsername(event.target.value)} placeholder="字母、数字或下划线" /></label>
        <label className="field"><span>密码</span><input type="password" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} required minLength={8} value={password} onChange={(event) => setPassword(event.target.value)} placeholder="至少 8 位" /></label>
        {mode === 'register' && <label className="field"><span>确认密码</span><input type="password" autoComplete="new-password" required value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)} placeholder="再次输入密码" /></label>}
        {error && <p className="auth-error" role="alert">{error}</p>}
        <button className="primary-button auth-submit" disabled={busy}>{busy ? '请稍候…' : mode === 'login' ? '登录' : '注册并登录'}<ArrowRight size={17} /></button>
      </form>
      <p className="auth-switch">{mode === 'login' ? '还没有账户？' : '已经有账户？'} <Link to={mode === 'login' ? '/register' : '/login'}>{mode === 'login' ? '立即注册' : '返回登录'}</Link></p>
    </section>
  </main>
}
