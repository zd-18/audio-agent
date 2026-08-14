import { ReloadOutlined, WarningOutlined } from '@ant-design/icons'
import { Alert, Button, Modal } from 'antd'
import { useState } from 'react'
import type { ProcessingExecution } from '../../../types/processingExecution'
import {
  getExecutionFailureMessage,
  getExecutionProgress,
  getProcessingExecutionErrorMessage,
  getStageLabel,
} from '../../../utils/processingExecutionDisplay'

export default function ExecutionFailure({
  execution,
  retrying,
  onRetry,
}: {
  execution: ProcessingExecution
  retrying: boolean
  onRetry: () => Promise<ProcessingExecution | null>
}) {
  const [modal, modalContext] = Modal.useModal()
  const [retryError, setRetryError] = useState<string | null>(null)
  const retryable = execution.executionStatus === 'FAILED' || execution.executionStatus === 'DEAD_LETTER'

  const requestRetry = () => {
    modal.confirm({
      title: '重新处理音频',
      content: '系统将使用相同的已确认步骤重新尝试，不会创建新的原始音频或新的执行任务记录。',
      okText: '确认重新处理',
      cancelText: '返回',
      centered: true,
      onOk: async () => {
        setRetryError(null)
        try {
          await onRetry()
        } catch (error) {
          setRetryError(getProcessingExecutionErrorMessage(error))
          throw error
        }
      },
    })
  }

  return (
    <section className="processing-execution-failure" aria-labelledby="processing-execution-failure-title">
      {modalContext}
      <div className="processing-execution-failure__icon" aria-hidden="true"><WarningOutlined /></div>
      <div className="processing-execution-failure__body">
        <span>RECOVERY</span>
        <h2 id="processing-execution-failure-title">处理未能完成</h2>
        <p>{getExecutionFailureMessage(execution.failureCode)}</p>
        <dl>
          <div><dt>失败阶段</dt><dd>{execution.currentStage ? getStageLabel(execution.currentStage) : '音频处理'}</dd></div>
          <div><dt>当前进度</dt><dd>{getExecutionProgress(execution.executionStatus, execution.progressPercent)}%</dd></div>
        </dl>
      </div>
      {retryable && (
        <Button
          type="primary"
          size="large"
          icon={<ReloadOutlined />}
          loading={retrying}
          disabled={retrying}
          onClick={requestRetry}
        >
          重新处理
        </Button>
      )}
      {retryError && (
        <Alert className="processing-execution-failure__alert" type="error" showIcon message={retryError} />
      )}
    </section>
  )
}
