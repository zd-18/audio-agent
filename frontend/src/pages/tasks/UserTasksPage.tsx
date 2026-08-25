import {
  CheckCircleFilled,
  ClockCircleOutlined,
  CloudUploadOutlined,
  ExclamationCircleFilled,
  FilterOutlined,
  LoadingOutlined,
  ReloadOutlined,
  RightOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { Alert, Button, Progress, Skeleton, Tag } from 'antd'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { retryAnalysisTask } from '../../api/analysisTasks'
import EmptyState from '../../components/workbench/EmptyState'
import PageContainer from '../../components/workbench/PageContainer'
import { useUserTasks } from '../../hooks/useUserTasks'
import type { UserTaskProgress, UserTaskStage, UserTaskStageStatus } from '../../types/userTask'
import { formatDateTime } from '../../utils/formatters'
import './user-tasks.css'

const STATUS_TONE: Record<UserTaskProgress['status'], string> = {
  PROCESSING: 'processing',
  WAITING_FOR_USER: 'action',
  COMPLETED: 'completed',
  FAILED: 'failed',
}

function StageIcon({ status }: { status: UserTaskStageStatus }) {
  if (status === 'COMPLETED') return <CheckCircleFilled />
  if (status === 'FAILED') return <ExclamationCircleFilled />
  if (status === 'ACTION_REQUIRED') return <UserOutlined />
  if (status === 'IN_PROGRESS') return <LoadingOutlined spin />
  return <ClockCircleOutlined />
}

function getStageLabel(code: string, fallbackLabel: string) {
  return code === 'INTELLIGENT_ANALYSIS' ? '智能诊断' : fallbackLabel
}

function TaskStages({ stages, currentStage }: { stages: UserTaskStage[]; currentStage: string }) {
  const mainStages = stages.filter((stage) => stage.code !== 'SPEECH_TRANSCRIPTION')

  return (
    <ol className="user-task-stages" aria-label="任务处理步骤">
      {mainStages.map((stage) => (
        <li
          key={stage.code}
          className={`is-${stage.status.toLowerCase()}`}
          aria-current={stage.code === currentStage ? 'step' : undefined}
        >
          <div className="user-task-stage__node">
            <span className="user-task-stage__icon" aria-hidden="true">
              <StageIcon status={stage.status} />
            </span>
          </div>
          <strong>{getStageLabel(stage.code, stage.label)}</strong>
        </li>
      ))}
    </ol>
  )
}

function TaskActions({
  task,
  retrying,
  onRetryAnalysis,
}: {
  task: UserTaskProgress
  retrying: boolean
  onRetryAnalysis: (taskId: string) => Promise<void>
}) {
  if (task.nextActions.length === 0) return null
  return (
    <div className="user-task-card__actions" aria-label="下一步操作">
      {task.nextActions.map((action) => action.type === 'RETRY_ANALYSIS' ? (
        <Button
          key={`${action.type}-${action.path}`}
          type="primary"
          loading={retrying}
          disabled={retrying}
          onClick={() => void onRetryAnalysis(task.taskId)}
        >
          {action.label}<RightOutlined />
        </Button>
      ) : (
        <Link key={`${action.type}-${action.path}`} to={action.path}>
          <Button type={action.primary ? 'primary' : 'default'}>
            {action.label}<RightOutlined />
          </Button>
        </Link>
      ))}
    </div>
  )
}

function CurrentTaskCard({
  task,
  retrying,
  onRetryAnalysis,
}: {
  task: UserTaskProgress
  retrying: boolean
  onRetryAnalysis: (taskId: string) => Promise<void>
}) {
  const exception = task.status === 'FAILED'
  return (
    <article className={`user-task-card is-${STATUS_TONE[task.status]}`}>
      <header className="user-task-card__header">
        <div>
          <span>音频任务</span>
          <h3>{task.fileName}</h3>
        </div>
        <Tag className={`user-task-status is-${STATUS_TONE[task.status]}`}>
          {task.statusLabel}
        </Tag>
      </header>

      <div className="user-task-card__now" aria-live="polite">
        <div>
          <div className="user-task-card__current-title">
            <span>当前阶段：</span>
            <strong>{getStageLabel(task.currentStage, task.currentStageLabel)}</strong>
          </div>
          <p>{task.currentActivity}</p>
        </div>
        <strong className="user-task-card__percent">{task.progressPercent}%</strong>
      </div>
      <Progress
        className="user-task-card__progress"
        percent={task.progressPercent}
        showInfo={false}
        size={['100%', 6]}
        status={exception ? 'exception' : 'active'}
        aria-label={`任务完成进度 ${task.progressPercent}%`}
      />

      <TaskStages stages={task.stages} currentStage={task.currentStage} />

      {task.failureReason && (
        <Alert
          className="user-task-card__failure"
          type="error"
          showIcon
          message={task.failureReason}
          description="原始音频不会受到影响，你可以按下方提示重新操作。"
        />
      )}
      <TaskActions task={task} retrying={retrying} onRetryAnalysis={onRetryAnalysis} />
    </article>
  )
}

function HistoryCard({ task }: { task: UserTaskProgress }) {
  return (
    <article className="user-task-history-card">
      <div className="user-task-history-card__main">
        <span>已完成</span>
        <h3>{task.fileName}</h3>
        <div className="user-task-history-card__time">
          <span>创建于 {formatDateTime(task.createdAt || undefined)}</span>
          <span>完成于 {formatDateTime(task.completedAt || undefined)}</span>
        </div>
      </div>
      <div className="user-task-history-card__operations">
        <span>主要操作</span>
        <div>{task.completedOperations.map((operation) => <Tag key={operation}>{operation}</Tag>)}</div>
      </div>
      {task.resultPath && (
        <Link to={task.resultPath}>
          <Button className="user-task-history-card__result-button" type="primary">
            查看结果<RightOutlined />
          </Button>
        </Link>
      )}
    </article>
  )
}

export default function UserTasksPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const exceptionsOnly = searchParams.get('view') === 'exceptions'
  const { data, loading, refreshing, error, refresh } = useUserTasks()
  const [retryingTaskId, setRetryingTaskId] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const currentTasks = data.records.filter((task) => exceptionsOnly
    ? task.status === 'FAILED'
    : task.status !== 'COMPLETED')
  const historyTasks = data.records.filter((task) => task.status === 'COMPLETED')

  const retryAnalysis = async (taskId: string) => {
    setRetryingTaskId(taskId)
    setActionError(null)
    try {
      await retryAnalysisTask(taskId)
      refresh()
    } catch (retryError) {
      setActionError(retryError instanceof Error
        ? retryError.message
        : '重新分析未能开始，请稍后重试。')
    } finally {
      setRetryingTaskId(null)
    }
  }

  return (
    <PageContainer>
      {error && (
        <Alert
          className="user-task-page__error"
          type="error"
          showIcon
          message="任务进度暂时无法加载"
          description="请检查网络后重试，已有任务不会受到影响。"
          action={<Button onClick={refresh}>重新加载</Button>}
        />
      )}
      {actionError && (
        <Alert
          className="user-task-page__error"
          type="error"
          showIcon
          closable
          message="操作未完成"
          description={actionError}
          onClose={() => setActionError(null)}
        />
      )}

      {loading && data.records.length === 0 ? (
        <section className="workbench-panel user-task-skeleton" aria-label="正在加载任务进度">
          <Skeleton active paragraph={{ rows: 8 }} />
        </section>
      ) : data.records.length === 0 && !error ? (
        <section className="workbench-panel">
          <EmptyState
            title="还没有音频任务"
            description="上传音频并开始智能诊断后，你可以在这里查看完整任务进度。"
            action={<Link to="/audio/upload"><Button type="primary" icon={<CloudUploadOutlined />}>上传音频</Button></Link>}
          />
        </section>
      ) : (
        <>
          <section className="user-task-section user-task-section--current" aria-labelledby="current-tasks-title">
            <div className="user-task-section__heading">
              <div><span>IN PROGRESS</span><h2 id="current-tasks-title">当前任务</h2></div>
              <div className="user-task-section__meta">
                <small>{currentTasks.length} 个需要关注</small>
                <Button
                  type={exceptionsOnly ? 'primary' : 'default'}
                  icon={<FilterOutlined />}
                  onClick={() => setSearchParams(exceptionsOnly
                    ? new URLSearchParams()
                    : new URLSearchParams({ view: 'exceptions' }))}
                >
                  {exceptionsOnly ? '查看全部任务' : '仅看异常任务'}
                </Button>
                <Button icon={<ReloadOutlined />} loading={refreshing} onClick={refresh}>
                  刷新进度
                </Button>
              </div>
            </div>
            {currentTasks.length > 0 ? (
              <div className="user-task-list">
                {currentTasks.map((task) => (
                  <CurrentTaskCard
                    key={task.taskId}
                    task={task}
                    retrying={retryingTaskId === task.taskId}
                    onRetryAnalysis={retryAnalysis}
                  />
                ))}
              </div>
            ) : (
              <div className="user-task-inline-empty"><CheckCircleFilled /><span>{exceptionsOnly ? '当前没有异常任务' : '当前没有待处理任务'}</span></div>
            )}
          </section>

          {!exceptionsOnly && <section className="user-task-section" aria-labelledby="history-tasks-title">
            <div className="user-task-section__heading">
              <div><span>PROCESSING RECORDS</span><h2 id="history-tasks-title">处理记录</h2></div>
              <small>最近 {historyTasks.length} 条完成记录</small>
            </div>
            {historyTasks.length > 0 ? (
              <div className="user-task-history-list">
                {historyTasks.map((task) => <HistoryCard key={task.taskId} task={task} />)}
              </div>
            ) : (
              <div className="user-task-inline-empty"><ClockCircleOutlined /><span>完成后的任务会记录在这里</span></div>
            )}
          </section>}
        </>
      )}
    </PageContainer>
  )
}
