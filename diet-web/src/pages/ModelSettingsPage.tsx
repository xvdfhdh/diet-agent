import { FormEvent, useEffect, useState } from 'react'
import { Check, ExternalLink, Eye, EyeOff, PlugZap, Save } from 'lucide-react'
import { api } from '../lib/api'
import { Notice } from '../components/Notice'
import type { ModelConfig, ModelConfigDraft, ProviderTemplate } from '../types'

const blank: ModelConfigDraft = { providerId: 'custom', displayName: '自定义服务', baseUrl: '', endpointPath: '/chat/completions', mainModel: '', lightModel: '', apiKey: '' }
const fallbackTemplates: ProviderTemplate[] = [
  { id: 'aliyun', name: '阿里云百炼', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', endpointPath: '/chat/completions', mainModel: 'qwen-max', lightModel: 'qwen-turbo', helpUrl: 'https://help.aliyun.com/zh/model-studio/compatibility-of-openai-with-dashscope' },
  { id: 'openai', name: 'OpenAI', baseUrl: 'https://api.openai.com/v1', endpointPath: '/chat/completions', mainModel: 'gpt-4.1', lightModel: 'gpt-4.1-mini', helpUrl: 'https://platform.openai.com/docs' },
  { id: 'deepseek', name: 'DeepSeek', baseUrl: 'https://api.deepseek.com', endpointPath: '/chat/completions', mainModel: 'deepseek-v4-pro', lightModel: 'deepseek-v4-flash', helpUrl: 'https://api-docs.deepseek.com/' },
  { id: 'zhipu', name: '智谱 AI', baseUrl: 'https://open.bigmodel.cn/api/paas/v4', endpointPath: '/chat/completions', mainModel: 'glm-5.2', lightModel: 'glm-4.5-flash', helpUrl: 'https://docs.bigmodel.cn/cn/guide/develop/openai/introduction' },
  { id: 'moonshot', name: '月之暗面', baseUrl: 'https://api.moonshot.cn/v1', endpointPath: '/chat/completions', mainModel: 'kimi-k2.5', lightModel: 'moonshot-v1-8k', helpUrl: 'https://platform.moonshot.cn/docs' },
  { id: 'siliconflow', name: '硅基流动', baseUrl: 'https://api.siliconflow.cn/v1', endpointPath: '/chat/completions', mainModel: 'deepseek-ai/DeepSeek-V3.2', lightModel: 'Qwen/Qwen3.6-27B', helpUrl: 'https://docs.siliconflow.cn/docs/userguide/quickstart' },
  { id: 'custom', name: '自定义', baseUrl: '', endpointPath: '/chat/completions', mainModel: '', lightModel: '', helpUrl: '' },
]
const fallbackConfig: ModelConfig = { providerId: 'aliyun', displayName: '阿里云百炼', baseUrl: fallbackTemplates[0].baseUrl, endpointPath: '/chat/completions', mainModel: 'qwen-max', lightModel: 'qwen-turbo', apiKeyConfigured: false, apiKeyMasked: '', templates: fallbackTemplates }

export function ModelSettingsPage() {
  const [config, setConfig] = useState<ModelConfig>()
  const [draft, setDraft] = useState<ModelConfigDraft>(blank)
  const [showKey, setShowKey] = useState(false)
  const [busy, setBusy] = useState<'save' | 'test'>()
  const [notice, setNotice] = useState({ message: '', tone: 'success' as 'success' | 'error' })
  useEffect(() => { api.modelConfig().then((value) => { setConfig(value); setDraft(toDraft(value)) }).catch(() => { setConfig(fallbackConfig); setDraft(toDraft(fallbackConfig)); setNotice({ message: '后端暂未连接，正在展示默认模板', tone: 'error' }) }) }, [])
  function apply(template: ProviderTemplate) {
    setDraft({ ...draft, providerId: template.id, displayName: template.name, baseUrl: template.baseUrl, endpointPath: template.endpointPath, mainModel: template.mainModel, lightModel: template.lightModel })
    setNotice({ message: template.id === 'custom' ? '已切换为自定义配置' : `已套用${template.name}模板，请核对当前可用的模型名`, tone: 'success' })
  }
  async function save(event: FormEvent) {
    event.preventDefault(); setBusy('save')
    try { const result = await api.saveModelConfig(draft); setConfig(result); setDraft(toDraft(result)); setNotice({ message: '配置已保存，新对话会立即使用这组模型', tone: 'success' }) }
    catch (error) { setNotice({ message: error instanceof Error ? error.message : '保存失败', tone: 'error' }) }
    finally { setBusy(undefined) }
  }
  async function test() {
    setBusy('test')
    try { const result = await api.testModelConfig(draft); setNotice({ message: `${result.message} · ${result.latencyMs} ms`, tone: result.success ? 'success' : 'error' }) }
    catch (error) { setNotice({ message: error instanceof Error ? error.message : '连接测试失败', tone: 'error' }) }
    finally { setBusy(undefined) }
  }
  const selected = config?.templates.find((item) => item.id === draft.providerId)
  return <section className="content-page settings-page">
    <header className="page-header"><p className="eyebrow">服务连接</p><h1>模型设置</h1><p>选择常用厂商，或接入任意兼容 OpenAI Chat Completions 的服务。</p></header>
    <Notice {...notice} />
    <form onSubmit={save}>
      <section className="settings-section"><div className="section-label"><span>01</span><div><h2>选择厂商</h2><p>模板只负责填入建议值，保存前仍可修改。</p></div></div>
        <div className="provider-list">{config?.templates.map((item) => <button type="button" key={item.id} className={draft.providerId === item.id ? 'selected' : ''} onClick={() => apply(item)}><span>{item.name}</span>{draft.providerId === item.id && <Check size={17} />}</button>) || <span className="loading-line">正在读取配置…</span>}</div>
      </section>
      <section className="settings-section"><div className="section-label"><span>02</span><div><h2>接口与凭证</h2><p>Key 只提交给后端，页面不会读回明文。</p></div></div>
        <div className="settings-fields"><label className="field"><span>厂商显示名称</span><input value={draft.displayName} onChange={(e) => setDraft({ ...draft, displayName: e.target.value })} placeholder="例如：本地模型服务" /></label>
          <label className="field"><span>接口路径</span><input value={draft.endpointPath} onChange={(e) => setDraft({ ...draft, endpointPath: e.target.value })} placeholder="/chat/completions" /></label>
          <label className="field wide"><span>接口地址 Base URL</span><input value={draft.baseUrl} onChange={(e) => setDraft({ ...draft, baseUrl: e.target.value })} placeholder="https://api.example.com/v1" /></label>
          <label className="field api-key-field"><span>API Key</span><div><input type={showKey ? 'text' : 'password'} value={draft.apiKey} onChange={(e) => setDraft({ ...draft, apiKey: e.target.value })} placeholder={config?.apiKeyConfigured ? `已配置 ${config.apiKeyMasked}；留空则不修改` : 'sk-…'} autoComplete="new-password" /><button type="button" onClick={() => setShowKey(!showKey)} aria-label={showKey ? '隐藏 Key' : '显示 Key'}>{showKey ? <EyeOff size={17} /> : <Eye size={17} />}</button></div></label>
        </div>
        {selected?.helpUrl && <a className="doc-link" href={selected.helpUrl} target="_blank" rel="noreferrer">查看 {selected.name} 接入文档 <ExternalLink size={14} /></a>}
      </section>
      <section className="settings-section"><div className="section-label"><span>03</span><div><h2>分配模型</h2><p>复杂推荐使用主模型，意图识别与澄清使用轻量模型。</p></div></div>
        <div className="settings-fields"><label className="field"><span>主模型</span><input value={draft.mainModel} onChange={(e) => setDraft({ ...draft, mainModel: e.target.value })} placeholder="模型标识" /><small>推荐生成、离线评审</small></label><label className="field"><span>轻量模型</span><input value={draft.lightModel} onChange={(e) => setDraft({ ...draft, lightModel: e.target.value })} placeholder="模型标识" /><small>意图识别、条件澄清</small></label></div>
      </section>
      <div className="settings-actions"><p>自定义地址需要提供兼容的 <code>/chat/completions</code> 接口。</p><div><button type="button" className="secondary-button" onClick={test} disabled={!!busy}><PlugZap size={16} />{busy === 'test' ? '测试中…' : '测试连接'}</button><button className="primary-button" disabled={!!busy}><Save size={16} />{busy === 'save' ? '保存中…' : '保存配置'}</button></div></div>
    </form>
  </section>
}

function toDraft(value: ModelConfig): ModelConfigDraft { return { providerId: value.providerId, displayName: value.displayName, baseUrl: value.baseUrl, endpointPath: value.endpointPath, mainModel: value.mainModel, lightModel: value.lightModel, apiKey: '' } }
