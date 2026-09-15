import { useState } from 'react'
import { Play } from 'lucide-react'
import { api } from '../lib/api'
import { Notice } from '../components/Notice'
import type { EvaluationReport } from '../types'

export function EvaluationsPage() {
  const [days, setDays] = useState(7)
  const [includeLlmJudge, setIncludeLlmJudge] = useState(false)
  const [report, setReport] = useState<EvaluationReport>()
  const [running, setRunning] = useState(false)
  const [notice, setNotice] = useState({ message: '', tone: 'success' as 'success' | 'error' })
  async function run() {
    const end = new Date(); const start = new Date(end.getTime() - days * 86_400_000)
    setRunning(true)
    try { setReport(await api.evaluate({ startAt: start.toISOString().replace('Z', ''), endAt: end.toISOString().replace('Z', ''), includeLlmJudge, limit: 100 })) }
    catch (error) { setNotice({ message: error instanceof Error ? error.message : '评测运行失败', tone: 'error' }) }
    finally { setRunning(false) }
  }
  return <section className="content-page">
    <header className="page-header row-header"><div><p className="eyebrow">推荐质量</p><h1>效果评测</h1><p>用已标注的运行记录，观察推荐是否真的更懂需求。</p></div>
      <button className="primary-button" onClick={run} disabled={running}><Play size={16} />{running ? '评测中…' : '开始评测'}</button>
    </header>
    <Notice {...notice} />
    <div className="evaluation-controls"><label className="field"><span>时间范围</span><select value={days} onChange={(event) => setDays(Number(event.target.value))}><option value={1}>最近 1 天</option><option value={7}>最近 7 天</option><option value={30}>最近 30 天</option></select></label><label className="toggle"><input type="checkbox" checked={includeLlmJudge} onChange={(event) => setIncludeLlmJudge(event.target.checked)} /><span />加入模型评审（会产生调用）</label></div>
    {report ? <>
      <div className="metric-strip"><div><small>总记录</small><strong>{report.totalTraces}</strong></div><div><small>已标注</small><strong>{report.labeledTraces}</strong></div><div><small>平均得分</small><strong>{report.avgScore == null ? '—' : report.avgScore.toFixed(2)}</strong></div></div>
      <div className="metrics-section"><h2>分项表现</h2>{Object.entries(report.metricAverages).map(([key, value]) => <div className="metric-row" key={key}><span>{metricName(key)}</span><div><i style={{ width: `${Math.max(0, Math.min(100, value * (value <= 1 ? 100 : 1)))}%` }} /></div><b>{value.toFixed(2)}</b></div>)}</div>
    </> : <div className="empty-state evaluation-empty"><h2>还没有评测结果</h2><p>先在运行记录中补充标注，再从这里运行一次评测。</p></div>}
  </section>
}

function metricName(value: string) { return ({ intentAccuracy: '意图准确', slotAccuracy: '条件提取', clarifyAccuracy: '澄清决策', recommendationQuality: '推荐质量' } as Record<string, string>)[value] || value }

