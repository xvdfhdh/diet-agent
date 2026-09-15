import { useEffect, useState } from 'react'
import { Brain, Save } from 'lucide-react'
import { Notice } from '../components/Notice'
import { api } from '../lib/api'
import type { UserPreferenceProfile } from '../types'

type PreferenceKey = keyof UserPreferenceProfile
const groups: Array<{ key: PreferenceKey; label: string; description: string }> = [
  { key: 'healthGoal', label: '饮食目标', description: '例如减脂、高蛋白、均衡' },
  { key: 'cuisine', label: '喜欢的菜系', description: '用于优先召回熟悉的风味' },
  { key: 'taste', label: '口味偏好', description: '本轮明确说出的口味仍会优先' },
  { key: 'convenience', label: '用餐习惯', description: '例如快速、一人食或适合备餐' },
]
const empty: UserPreferenceProfile = { healthGoal: [], cuisine: [], taste: [], convenience: [] }

export function PreferencesPage() {
  const [profile, setProfile] = useState<UserPreferenceProfile>(empty)
  const [options, setOptions] = useState<Record<string, string[]>>({})
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [notice, setNotice] = useState({ message: '', tone: 'success' as 'success' | 'error' })

  useEffect(() => {
    let active = true
    Promise.all([api.preferences(), api.slotOptions()])
      .then(([saved, available]) => { if (active) { setProfile(saved); setOptions(available) } })
      .catch((error) => { if (active) setNotice({ message: error instanceof Error ? error.message : '偏好加载失败', tone: 'error' }) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [])

  function toggle(key: PreferenceKey, value: string) {
    setProfile((current) => ({ ...current, [key]: current[key].includes(value) ? current[key].filter((item) => item !== value) : [...current[key], value] }))
  }

  async function save() {
    setSaving(true)
    try {
      setProfile(await api.savePreferences(profile))
      setNotice({ message: '偏好已保存，之后的新对话会自动参考', tone: 'success' })
    } catch (error) {
      setNotice({ message: error instanceof Error ? error.message : '偏好保存失败', tone: 'error' })
    } finally { setSaving(false) }
  }

  return <section className="content-page preferences-page">
    <header className="page-header row-header"><div><p className="eyebrow">个人中心</p><h1>我的偏好</h1><p>手动维护长期画像。你在对话里的明确表达和收藏也会逐步更新记忆。</p></div><Brain className="page-symbol" size={42} /></header>
    <Notice {...notice} />
    {loading ? <div className="loading-line">正在读取长期记忆…</div> : <div className="preference-panel">
      {groups.map((group) => <fieldset className="preference-group" key={group.key}>
        <legend>{group.label}</legend><p>{group.description}</p>
        <div className="preference-options">{(options[group.key] || []).map((value) => <button type="button" key={value} className={profile[group.key].includes(value) ? 'selected' : ''} aria-pressed={profile[group.key].includes(value)} onClick={() => toggle(group.key, value)}>{value}</button>)}</div>
      </fieldset>)}
      <div className="preference-save"><p>已选择 {Object.values(profile).reduce((sum, values) => sum + values.length, 0)} 项</p><button className="primary-button" disabled={saving} onClick={save}><Save size={17} />{saving ? '保存中…' : '保存偏好'}</button></div>
    </div>}
  </section>
}
