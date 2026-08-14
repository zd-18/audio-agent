import {
  CheckCircleFilled,
  ClockCircleOutlined,
  CloseCircleFilled,
  LoadingOutlined,
} from '@ant-design/icons'
import { Alert, Button } from 'antd'
import { useNavigate } from 'react-router-dom'
import type { AgentProcessingWorkflow } from '../../types/agent'
import { formatDuration } from '../../utils/formatters'

const PROGRESS_LABELS = [
  '正在理解需求',
  '已生成方案',
  '等待确认',
  '正在处理',
  '正在检查结果',
  '完成',
] as const

const ACTIVE_INDEX: Record<AgentProcessingWorkflow['status'], number> = {
  PLANNING: 0,
  WAITING_CONFIRMATION: 2,
  EXECUTING: 3,
  REVIEWING: 4,
  SUCCESS: 5,
  FAILED: 4,
}

function stepDescription(step: AgentProcessingWorkflow['steps'][number]) {
  if (step.operationType === 'TRIM_SEGMENT' && step.startMs !== null && step.endMs !== null) {
    return `${step.title}（${formatDuration(step.startMs)}–${formatDuration(step.endMs)}）`
  }
  return step.title
}

export default function AgentProcessingWorkflowCard({
  workflow,
  confirming,
  onConfirm,
}: {
  workflow: AgentProcessingWorkflow
  confirming: boolean
  onConfirm: (workflowId: string) => void
}) {
  const activeIndex = ACTIVE_INDEX[workflow.status]
  const failed = workflow.status === 'FAILED'
  const navigate = useNavigate()

  return (
    <section className={`agent-workflow-card${failed ? ' is-failed' : ''}`} aria-label="音频处理进度">
      <ol className="agent-workflow-progress" aria-label="处理流程">
        {PROGRESS_LABELS.map((label, index) => {
          const completed = !failed && index < activeIndex || workflow.status === 'SUCCESS'
          const active = index === activeIndex && workflow.status !== 'SUCCESS'
          return (
            <li
              key={label}
              className={`${completed ? 'is-completed' : ''}${active ? ' is-active' : ''}${failed && active ? ' is-failed' : ''}`}
              aria-current={active ? 'step' : undefined}
            >
              <span className="agent-workflow-progress__icon" aria-hidden="true">
                {failed && active
                  ? <CloseCircleFilled />
                  : completed
                    ? <CheckCircleFilled />
                    : active
                      ? <LoadingOutlined spin={workflow.status !== 'WAITING_CONFIRMATION'} />
                      : <ClockCircleOutlined />}
              </span>
              <span>{label}</span>
            </li>
          )
        })}
      </ol>

      {workflow.summary && (
        <div className="agent-workflow-plan">
          <strong>{workflow.summary}</strong>
          <ol>
            {workflow.steps.map((step) => (
              <li key={`${step.order}-${step.operationType}`}>
                <span>{stepDescription(step)}</span>
                {step.reason && <small>{step.reason}</small>}
              </li>
            ))}
          </ol>
        </div>
      )}

      {workflow.status === 'WAITING_CONFIRMATION' && (
        <div className="agent-workflow-confirm">
          <p>确认后才会修改音频，并生成一个新的结果文件；原文件不会被覆盖。</p>
          <Button
            type="primary"
            loading={confirming}
            disabled={confirming}
            onClick={() => onConfirm(workflow.workflowId)}
          >
            {confirming ? '正在开始处理' : '确认并开始处理'}
          </Button>
        </div>
      )}

      {(workflow.status === 'EXECUTING' || workflow.status === 'REVIEWING') && (
        <p className="agent-workflow-live" aria-live="polite">
          {workflow.status === 'REVIEWING'
            ? '处理步骤已完成，正在检查文件和音频信息。'
            : `正在生成结果${workflow.progressPercent === null ? '' : `，已完成 ${workflow.progressPercent}%`}`}
        </p>
      )}

      {workflow.status === 'SUCCESS' && (
        <Alert
          type="success"
          showIcon
          message="音频处理完成"
          description="结果已通过检查并保存，可以前往处理任务中查看。"
          action={workflow.executionId
            ? (
              <Button
                type="primary"
                size="small"
                onClick={() => navigate(`/analysis/tasks/${workflow.taskId}/processing-execution`)}
              >
                查看处理结果
              </Button>
            )
            : undefined}
        />
      )}
      {failed && (
        <Alert
          type="error"
          showIcon
          message="本次处理未通过检查"
          description={workflow.failureReason || '结果文件或音频信息异常，请检查需求后重新发起处理。'}
        />
      )}
    </section>
  )
}
