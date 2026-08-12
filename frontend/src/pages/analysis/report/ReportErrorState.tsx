import { ClockCircleOutlined, CloseCircleOutlined, WarningOutlined } from '@ant-design/icons'
import { Button } from 'antd'
import { Link, useNavigate } from 'react-router-dom'
import type { ApiError } from '../../../api/http'
import ManualRetryButton from '../../../components/analysis/ManualRetryButton'
import type { AnalysisTaskRecord } from '../../../types/api'

const REPORT_NOT_READY_CODE = 40205
const TASK_NOT_FOUND_CODE = 40101

interface ReportErrorStateProps {
  taskId: string
  error: ApiError
  relatedTask: AnalysisTaskRecord | null
  onRetry: () => void
}

export default function ReportErrorState({ taskId, error, relatedTask, onRetry }: ReportErrorStateProps) {
  const navigate = useNavigate()
  const taskFailed = error.code === REPORT_NOT_READY_CODE && relatedTask?.status === 'FAILED'
  const notReady = error.code === REPORT_NOT_READY_CODE && !taskFailed
  const notFound = error.code === TASK_NOT_FOUND_CODE

  const icon = taskFailed
    ? <CloseCircleOutlined />
    : notReady ? <ClockCircleOutlined /> : <WarningOutlined />
  const title = taskFailed
    ? '本次分析未完成，暂时无法生成报告。'
    : notReady
      ? '音频仍在分析中，报告生成后即可查看。'
      : notFound
        ? '未找到对应的分析任务。'
        : '报告加载失败，请稍后重试。'
  const description = taskFailed
    ? '你可以先查看任务失败原因；修复问题后，使用现有重试流程重新发起分析。'
    : notReady
      ? '当前任务尚未返回可用报告，可以返回任务详情查看进度，或重新检查状态。'
      : notFound
        ? '请确认地址中的任务 ID，或返回分析任务列表重新选择。'
        : '请求没有成功完成。重试只会重新获取报告，不会刷新整个浏览器页面。'

  return (
    <section className={`report-error-state${taskFailed ? ' report-error-state--failed' : ''}`} role="alert">
      <span className="report-error-state__icon">{icon}</span>
      <h2>{title}</h2>
      <p>{description}</p>
      <div className="report-error-state__actions">
        {notFound ? (
          <Link to="/analysis/tasks"><Button type="primary">返回分析任务</Button></Link>
        ) : (
          <>
            <Link to={`/analysis/tasks/${taskId}`}><Button>{taskFailed ? '查看失败原因' : '返回任务详情'}</Button></Link>
            {taskFailed ? (
              <ManualRetryButton
                taskId={taskId}
                label="重试任务"
                onRetried={(task) => navigate(`/analysis/tasks/${task.taskId}`)}
              />
            ) : (
              <Button type="primary" onClick={onRetry}>{notReady ? '重新检查状态' : '重新加载报告'}</Button>
            )}
          </>
        )}
      </div>
      {error.requestId && <small>请求标识：{error.requestId}</small>}
    </section>
  )
}
