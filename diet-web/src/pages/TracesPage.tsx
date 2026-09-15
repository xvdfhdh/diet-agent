import { FormEvent, useEffect, useState } from 'react'
import { ChevronRight, X } from 'lucide-react'
import { api } from '../lib/api'
import { Notice } from '../components/Notice'
import type { Trace } from '../types'

const intents = ['MEAL_RECOMMENDATION', 'CLARIFY_NEEDED', 'MEAL_ADJUST', 'MEAL_PLAN', 'HEALTH_RISK', 'OTHER']

export function TracesPage() {
  const [traces, setTraces] = useState<Trace[]>([])
  const [selected, setSelected] = useState<Trace>()
  const [onlyUnlabeled, setOnlyUnlabeled] = useState(false)
  const [label, setLabel] = useState({ expectedIntent: '', expectedClarifyAction: '', labelNote: '' })
  const [notice, setNotice] = useState({ message: '', tone: 'success' as 'success' | 'error' })
  async function load() {
    try { setTraces(await api.traces(7, onlyUnlabeled)) }
    catch (error) { setNotice({ message: error instanceof Error ? error.message : '记录加载失败', tone: 'error' }) }
  }
  useEffect(() => { load() }, [onlyUnlabeled])
  function choose(trace: Trace) {
    setSelected(trace)
    setLabel({ expectedIntent: trace.expectedIntent || '', expectedClarifyAction: trace.expectedClarifyAction || '', labelNote: trace.labelNote || '' })
  }
  async function save(event: FormEvent) {
    event.preventDefault()
    if (!selected) return
    try { await api.labelTrace(selected.traceId, label); setNotice({ message: '标注已保存', tone: 'success' }); setSelected(undefined); await load() }
    catch (error) { setNotice({ message: error instanceof Error ? error.message : '标注保存失败', tone: 'error' }) }
  }
  return <section className="content-page">
    <header className="page-header row-header"><div><p className="eyebrow">最近 7 天</p><h1>运行记录</h1><p>查看每次推荐经过了什么，并为评测补充人工标注。</p></div>
      <label className="toggle"><input type="checkbox" checked={onlyUnlabeled} onChange={(event) => setOnlyUnlabeled(event.target.checked)} /><span />只看未标注</label>
    </header>
    <Notice {...notice} />
    <div className="table-wrap"><table><thead><tr><th>时间</th><th>会话</th><th>状态</th><th>耗时</th><th>标注</th><th /></tr></thead><tbody>
      {traces.map((trace) => <tr key={trace.traceId} onClick={() => choose(trace)}><td>{new Date(trace.createdAt).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })}</td><td className="mono">{trace.sessionId.slice(0, 10)}</td><td><span className={`status ${trace.status.toLowerCase()}`}>{trace.status}</span></td><td>{trace.durationMs ? `${trace.durationMs} ms` : '—'}</td><td>{trace.expectedIntent || '待标注'}</td><td><ChevronRight size={17} /></td></tr>)}
    </tbody></table>{traces.length === 0 && <div className="empty-table">这个时间范围内没有记录。</div>}</div>
    {selected && <div className="dialog-backdrop"><form className="dialog trace-dialog" onSubmit={save}>
      <div className="dialog-title"><div><small>运行详情</small><h2>{selected.traceId.slice(0, 18)}</h2></div><button type="button" className="icon-button" onClick={() => setSelected(undefined)}><X size={20} /></button></div>
      <div className="trace-summary"><span>状态 <b>{selected.status}</b></span><span>事件 <b>{selected.eventCount}</b></span><span>耗时 <b>{selected.durationMs || 0} ms</b></span></div>
      {selected.errorMessage && <p className="error-box">{selected.errorMessage}</p>}
      <details><summary>查看原始 Trace JSON</summary><pre>{formatJson(selected.traceJson)}</pre></details>
      <div className="form-grid"><label className="field"><span>期望意图</span><select value={label.expectedIntent} onChange={(e) => setLabel({ ...label, expectedIntent: e.target.value })}><option value="">未标注</option>{intents.map((item) => <option key={item}>{item}</option>)}</select></label>
      <label className="field"><span>期望澄清动作</span><select value={label.expectedClarifyAction} onChange={(e) => setLabel({ ...label, expectedClarifyAction: e.target.value })}><option value="">未标注</option><option>ASK</option><option>READY</option></select></label></div>
      <label className="field"><span>备注</span><textarea rows={3} value={label.labelNote} onChange={(e) => setLabel({ ...label, labelNote: e.target.value })} placeholder="这次推荐哪里好、哪里需要调整？" /></label>
      <div className="dialog-actions"><button type="button" className="secondary-button" onClick={() => setSelected(undefined)}>取消</button><button className="primary-button">保存标注</button></div>
    </form></div>}
  </section>
}

function formatJson(value: string) { try { return JSON.stringify(JSON.parse(value), null, 2) } catch { return value } }

