import { Navigate, Route, Routes } from 'react-router-dom'
import { AppShell } from './components/AppShell'
import { ChatPage } from './pages/ChatPage'
import { MealsPage } from './pages/MealsPage'
import { TracesPage } from './pages/TracesPage'
import { EvaluationsPage } from './pages/EvaluationsPage'
import { ModelSettingsPage } from './pages/ModelSettingsPage'
import { CollectionPage } from './pages/CollectionPage'
import { PreferencesPage } from './pages/PreferencesPage'
import { AuthPage } from './pages/AuthPage'
import { PlanPage } from './pages/PlanPage'
import { ShoppingPage } from './pages/ShoppingPage'
import { MealDetailPage } from './pages/MealDetailPage'
import { AuthProvider, useAuth } from './lib/AuthContext'

export default function App() {
  return <AuthProvider><AppRoutes /></AuthProvider>
}

function AppRoutes() {
  const { user, loading } = useAuth()
  if (loading) return <div className="auth-loading">正在检查登录状态…</div>
  return (
    <Routes>
      <Route path="/login" element={<AuthPage mode="login" />} />
      <Route path="/register" element={<AuthPage mode="register" />} />
      <Route path="/*" element={user ? <AppShell><Routes>
        <Route path="/" element={<ChatPage />} />
        <Route path="/meals/personal" element={<MealsPage mode="personal" />} />
        <Route path="/meals/public" element={<MealsPage mode="public" />} />
        <Route path="/collection" element={<CollectionPage />} />
        <Route path="/plan" element={<PlanPage />} />
        <Route path="/shopping" element={<ShoppingPage />} />
        <Route path="/meals/:id" element={<MealDetailPage />} />
        <Route path="/preferences" element={<PreferencesPage />} />
        <Route path="/traces" element={user.role === 'ADMIN' ? <TracesPage /> : <Navigate to="/" replace />} />
        <Route path="/evaluations" element={user.role === 'ADMIN' ? <EvaluationsPage /> : <Navigate to="/" replace />} />
        <Route path="/settings/models" element={user.role === 'ADMIN' ? <ModelSettingsPage /> : <Navigate to="/" replace />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes></AppShell> : <Navigate to="/login" replace />} />
    </Routes>
  )
}
