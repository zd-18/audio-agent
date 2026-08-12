import { Descriptions } from 'antd'
import type { AnalysisTaskRecord } from '../../types/api'
import CopyableValue from '../workbench/CopyableValue'
import { formatDateTime } from '../../utils/formatters'

export default function RetryInfoCard({ task }: { task: AnalysisTaskRecord }) {
  return (
    <section className={`workbench-panel analysis-card${task.status === 'FAILED' ? ' analysis-card--error' : ''}`}>
      <div className="workbench-panel__heading"><div><span>RETRY & FAILURE</span><h3>重试与失败信息</h3></div></div>
      <Descriptions column={{ xs: 1, sm: 2 }} items={[
        { key: 'retry', label: '重试次数', children: `${task.retryCount ?? 0} / ${task.maxRetryCount ?? 0}` },
        { key: 'next', label: '下次重试时间', children: formatDateTime(task.nextRetryAt) },
        { key: 'code', label: '最后错误码', children: <CopyableValue value={task.lastErrorCode} mono /> },
        { key: 'messageId', label: '最后消息 ID', children: <CopyableValue value={task.lastMessageId} mono /> },
        { key: 'error', label: '错误信息', span: 2, children: task.errorMessage || '—' },
      ]} />
    </section>
  )
}
