import { createContext, useContext, useEffect, useState, type PropsWithChildren } from 'react'
import { api } from './api'
import { AUTH_CHANGED_EVENT, authToken, clearAuthToken, saveAuthToken } from './authStorage'
import type { AuthResponse, AuthUser } from '../types'

type AuthContextValue = {
  user: AuthUser | null
  loading: boolean
  accept: (response: AuthResponse) => void
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: PropsWithChildren) {
  const [user, setUser] = useState<AuthUser | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let active = true
    const sync = () => { if (!authToken() && active) setUser(null) }
    window.addEventListener(AUTH_CHANGED_EVENT, sync)
    if (authToken()) {
      api.me().then((current) => { if (active) setUser(current) })
        .catch(() => { if (active) { clearAuthToken(); setUser(null) } })
        .finally(() => { if (active) setLoading(false) })
    } else {
      setLoading(false)
    }
    return () => { active = false; window.removeEventListener(AUTH_CHANGED_EVENT, sync) }
  }, [])

  function accept(response: AuthResponse) {
    saveAuthToken(response.token)
    setUser(response.user)
  }

  async function logout() {
    try { await api.logout() }
    catch { /* 网络故障时仍清除本地令牌，避免界面继续保持登录态。 */ }
    finally { clearAuthToken(); setUser(null) }
  }

  return <AuthContext.Provider value={{ user, loading, accept, logout }}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('AuthProvider 未初始化')
  return context
}
