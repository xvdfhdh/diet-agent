import { Navigate, Route, Routes } from 'react-router-dom'
import { AppShell } from './components/AppShell'
import { ChatPage } from './pages/ChatPage'
import { MealsPage } from './pages/MealsPage'
import { TracesPage } from './pages/TracesPage'
import { EvaluationsPage } from './pages/EvaluationsPage'
import { ModelSettingsPage } from './pages/ModelSettingsPage'
import { CollectionPage } from './pages/CollectionPage'
import { PreferencesPage } from './pages/PreferencesPage'

export default function App() {
  return (
    <AppShell>
      <Routes>
        <Route path="/" element={<ChatPage />} />
        <Route path="/meals/personal" element={<MealsPage mode="personal" />} />
        <Route path="/meals/public" element={<MealsPage mode="public" />} />
        <Route path="/collection" element={<CollectionPage />} />
        <Route path="/preferences" element={<PreferencesPage />} />
        <Route path="/traces" element={<TracesPage />} />
        <Route path="/evaluations" element={<EvaluationsPage />} />
        <Route path="/settings/models" element={<ModelSettingsPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AppShell>
  )
}
