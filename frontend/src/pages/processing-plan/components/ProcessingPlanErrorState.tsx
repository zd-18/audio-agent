import { FileSearchOutlined, ReloadOutlined } from '@ant-design/icons'
import { Button } from 'antd'
import { Link } from 'react-router-dom'
import { ApiError } from '../../../api/http'

const PROCESSING_PLAN_NOT_FOUND_CODE = 40209
const PROCESSING_PLAN_NOT_READY_CODE = 40210
const AUDIO_TASK_NOT_FOUND_CODE = 40101

interface ProcessingPlanErrorStateProps {
  taskId: string
  error: ApiError
  generating: boolean
  onRetry: () => void
  onGenerate: () => void
  allowGenerate?: boolean
  backPath?: string
  backLabel?: string
}

export default function ProcessingPlanErrorState({
  taskId,
  error,
  generating,
  onRetry,
  onGenerate,
  allowGenerate = true,
  backPath,
  backLabel = '返回分析报告',
}: ProcessingPlanErrorStateProps) {
  const notFound = error.code === PROCESSING_PLAN_NOT_FOUND_CODE
  const notReady = error.code === PROCESSING_PLAN_NOT_READY_CODE
  const taskNotFound = error.code === AUDIO_TASK_NOT_FOUND_CODE
  const title = notFound
    ? '当前任务还没有处理方案'
    : notReady
      ? '分析结果尚未准备完成'
      : taskNotFound
        ? '未找到对应分析任务'
        : '处理方案加载失败'
  const description = notFound
    ? allowGenerate
      ? '可以根据当前诊断结果生成建议步骤，生成过程不会修改原始音频。'
      : '请返回智能处理，重新描述处理需求以生成方案。'
    : notReady
      ? '暂时无法生成处理方案，请等待分析完成后再试。'
      : taskNotFound
        ? '请返回分析任务列表，确认任务是否仍然存在。'
        : '处理方案加载失败，请稍后重试。'

  return (
    <section className="processing-plan-error" role="alert">
      <span className="processing-plan-error__icon"><FileSearchOutlined /></span>
      <h2>{title}</h2>
      <p>{description}</p>
      <div className="processing-plan-error__actions">
        {notFound && allowGenerate && <Button type="primary" loading={generating} onClick={onGenerate}>生成处理方案</Button>}
        {!taskNotFound && !notFound && <Button icon={<ReloadOutlined />} onClick={onRetry}>重试</Button>}
        <Link to={backPath || `/analysis/tasks/${encodeURIComponent(taskId)}/report`}><Button>{backLabel}</Button></Link>
      </div>
    </section>
  )
}
