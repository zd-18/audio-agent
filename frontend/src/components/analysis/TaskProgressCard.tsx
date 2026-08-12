import { Progress } from 'antd'
import type { AnalysisTaskRecord } from '../../types/api'

export default function TaskProgressCard({ task, lastUpdatedAt }: { task: AnalysisTaskRecord; lastUpdatedAt: Date | null }) {
  const status = task.status === 'FAILED' ? 'exception' : task.status === 'SUCCESS' ? 'success' : 'active'
  return (
    <section className="workbench-panel analysis-card">
      <div className="workbench-panel__heading"><div><span>EXECUTION PROGRESS</span><h3>当前执行进度</h3></div><small>上次更新：{lastUpdatedAt?.toLocaleTimeString('zh-CN') || '—'}</small></div>
      <Progress percent={task.progress ?? 0} status={status} strokeColor={task.status === 'SUCCESS' ? 'var(--workbench-success)' : undefined} />
      <p>{task.status === 'PENDING' ? '任务正在等待调度。' : task.status === 'PROCESSING' ? '页面会自动更新进度，切到后台后将降低查询频率。' : '任务已进入终态，自动轮询已停止。'}</p>
    </section>
  )
}
