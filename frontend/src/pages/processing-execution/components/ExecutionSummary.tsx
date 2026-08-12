import {
  AudioOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  CloseCircleOutlined,
  LoadingOutlined,
} from '@ant-design/icons'
import { Typography } from 'antd'
import type { ProcessingExecution } from '../../../types/processingExecution'
import { formatDateTime } from '../../../utils/formatters'
import { EXECUTION_STATUS_META } from '../../../utils/processingExecutionDisplay'

function compactId(value: string) {
  return value.length > 18 ? `${value.slice(0, 8)}…${value.slice(-6)}` : value
}

function StatusIcon({ status }: { status: ProcessingExecution['executionStatus'] }) {
  if (status === 'SUCCESS') return <CheckCircleOutlined />
  if (status === 'FAILED' || status === 'DEAD_LETTER') return <CloseCircleOutlined />
  if (status === 'PROCESSING') return <LoadingOutlined spin />
  return <ClockCircleOutlined />
}

export default function ExecutionSummary({
  execution,
  fileName,
}: {
  execution: ProcessingExecution
  fileName?: string | null
}) {
  const status = EXECUTION_STATUS_META[execution.executionStatus]
  return (
    <section className="processing-execution-summary" aria-labelledby="processing-execution-summary-title">
      <div className="processing-execution-summary__identity">
        <span className="processing-execution-summary__audio" aria-hidden="true"><AudioOutlined /></span>
        <div>
          <span>ORIGINAL AUDIO</span>
          <h2 id="processing-execution-summary-title">{fileName || '正在读取原始文件信息'}</h2>
          <p>{status.description}</p>
        </div>
        <span className={`processing-execution-status is-${status.tone}`}>
          <StatusIcon status={execution.executionStatus} />
          {status.label}
        </span>
      </div>

      <dl className="processing-execution-summary__meta">
        <div><dt>创建时间</dt><dd>{formatDateTime(execution.createdAt || undefined)}</dd></div>
        <div><dt>开始时间</dt><dd>{formatDateTime(execution.startedAt || undefined)}</dd></div>
        <div><dt>完成时间</dt><dd>{formatDateTime(execution.finishedAt || undefined)}</dd></div>
        <div><dt>重试次数</dt><dd>{execution.retryCount}</dd></div>
        <div className="processing-execution-summary__id">
          <dt>执行任务 ID</dt>
          <dd>
            <Typography.Text copyable={{ text: execution.executionId, tooltips: ['复制执行任务 ID', '已复制'] }}>
              {compactId(execution.executionId)}
            </Typography.Text>
          </dd>
        </div>
      </dl>
    </section>
  )
}
