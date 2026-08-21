import {
  AudioOutlined,
  ClockCircleOutlined,
  CloudUploadOutlined,
  RightOutlined,
} from '@ant-design/icons'
import { Alert, Button, Progress, Skeleton } from 'antd'
import { Link } from 'react-router-dom'
import AudioFileStatusBadge from '../../components/workbench/AudioFileStatusBadge'
import PageContainer from '../../components/workbench/PageContainer'
import PageTitle from '../../components/workbench/PageTitle'
import { useAudioFileList } from '../../hooks/useAudioFileList'
import { useUserTasks } from '../../hooks/useUserTasks'
import type { AudioFileListItem } from '../../types/api'
import type { UserTaskProgress } from '../../types/userTask'
import { formatDateTime, formatDuration } from '../../utils/formatters'
import './dashboard.css'

const RECENT_AUDIO_QUERY = { current: 1, size: 5 }

const TASK_STATUS_LABELS: Record<UserTaskProgress['status'], string> = {
  PROCESSING: '处理中',
  WAITING_FOR_USER: '等待处理',
  COMPLETED: '已完成',
  FAILED: '处理失败',
}

function audioFormat(record: AudioFileListItem) {
  const extension = record.originalFileName?.split('.').pop()
  if (extension && extension !== record.originalFileName) return extension.toUpperCase()
  const subtype = record.contentType?.split('/').pop()
  return subtype ? subtype.toUpperCase() : '—'
}

function DashboardSectionHeading({
  eyebrow,
  id,
  title,
  to,
}: {
  eyebrow: string
  id: string
  title: string
  to: string
}) {
  return (
    <div className="dashboard-section__heading">
      <div><span>{eyebrow}</span><h2 id={id}>{title}</h2></div>
      <Link to={to}>查看全部<RightOutlined aria-hidden="true" /></Link>
    </div>
  )
}

function DashboardLoading({ label }: { label: string }) {
  return (
    <div className="dashboard-loading" aria-label={label}>
      {[0, 1, 2].map((item) => <Skeleton key={item} active avatar paragraph={{ rows: 1 }} />)}
    </div>
  )
}

function RecentAudioList({ records }: { records: AudioFileListItem[] }) {
  return (
    <ul className="dashboard-list dashboard-audio-list">
      {records.map((record) => (
        <li key={record.audioFileId}>
          <span className="dashboard-list__icon" aria-hidden="true"><AudioOutlined /></span>
          <div className="dashboard-list__main">
            <Link className="dashboard-list__title" to={`/audio/files/${encodeURIComponent(record.audioFileId)}`}>
              {record.originalFileName || '未命名音频'}
            </Link>
            <div className="dashboard-list__meta">
              <span>{formatDuration(record.duration)}</span>
              <span>{audioFormat(record)}</span>
              <span>{formatDateTime(record.createdAt)}</span>
            </div>
          </div>
          <AudioFileStatusBadge status={record.status} />
          <div className="dashboard-list__actions">
            <Link to={`/audio/files/${encodeURIComponent(record.audioFileId)}`}><Button type="link">打开</Button></Link>
            {record.status === 'AVAILABLE' && (
              <Link to={`/audio/files/${encodeURIComponent(record.audioFileId)}/agent`}>
                <Button type="text">智能处理</Button>
              </Link>
            )}
          </div>
        </li>
      ))}
    </ul>
  )
}

function RecentTaskList({ records }: { records: UserTaskProgress[] }) {
  return (
    <ul className="dashboard-list dashboard-task-list">
      {records.map((task) => (
        <li key={task.taskId}>
          <span className="dashboard-list__icon dashboard-list__icon--task" aria-hidden="true"><ClockCircleOutlined /></span>
          <div className="dashboard-list__main">
            <strong className="dashboard-list__title">{task.fileName || '未命名音频'}</strong>
            <div className="dashboard-list__meta">
              <span>音频任务</span>
              {task.currentStageLabel && <span>{task.currentStageLabel}</span>}
              <span>{formatDateTime(task.completedAt || task.createdAt || undefined)}</span>
            </div>
          </div>
          <span className={`dashboard-task-status is-${task.status.toLowerCase()}`}>
            <i aria-hidden="true" />{TASK_STATUS_LABELS[task.status]}
          </span>
          <div className="dashboard-task-progress">
            <Progress
              percent={task.progressPercent}
              showInfo={false}
              size="small"
              status={task.status === 'FAILED' ? 'exception' : undefined}
              aria-label={`${task.fileName || '音频任务'}处理进度 ${task.progressPercent}%`}
            />
            <span>{task.progressPercent}%</span>
          </div>
          <Link className="dashboard-task-link" to="/tasks">查看进度<RightOutlined aria-hidden="true" /></Link>
        </li>
      ))}
    </ul>
  )
}

export default function DashboardPage() {
  const audio = useAudioFileList(RECENT_AUDIO_QUERY)
  const tasks = useUserTasks(5)
  const recentAudio = audio.data.records.slice(0, 5)
  const recentTasks = tasks.data.records.slice(0, 5)

  return (
    <PageContainer>
      <div className="dashboard-hero">
        <PageTitle
          eyebrow="WORKSPACE"
          title="AudioAgent 工作台"
          actions={(
            <>
              <Link to="/audio/upload"><Button type="primary" icon={<CloudUploadOutlined aria-hidden="true" />}>上传新音频</Button></Link>
              <Link to="/audio/files"><Button>查看全部音频</Button></Link>
            </>
          )}
        />
      </div>

      <section className="dashboard-section" aria-labelledby="recent-audio-title">
        <DashboardSectionHeading eyebrow="RECENT AUDIO" id="recent-audio-title" title="最近音频" to="/audio/files" />
        {audio.error && (
          <Alert
            className="dashboard-section__alert"
            type="error"
            showIcon
            message="最近音频暂时无法加载"
            description="请检查网络连接后重试，其他工作台内容不受影响。"
            action={<Button onClick={audio.refresh}>重新加载</Button>}
          />
        )}
        {audio.loading && recentAudio.length === 0 ? (
          <DashboardLoading label="正在加载最近音频" />
        ) : recentAudio.length > 0 ? (
          <RecentAudioList records={recentAudio} />
        ) : !audio.error ? (
          <div className="dashboard-empty dashboard-empty--audio">
            <span className="dashboard-empty__icon" aria-hidden="true"><AudioOutlined /></span>
            <div><h3>还没有上传音频</h3><p>上传第一段音频开始分析和处理</p></div>
            <Link to="/audio/upload"><Button type="primary" icon={<CloudUploadOutlined aria-hidden="true" />}>上传音频</Button></Link>
          </div>
        ) : null}
      </section>

      <section className="dashboard-section" aria-labelledby="recent-tasks-title">
        <DashboardSectionHeading eyebrow="RECENT TASKS" id="recent-tasks-title" title="最近任务" to="/tasks" />
        {tasks.error && (
          <Alert
            className="dashboard-section__alert"
            type="error"
            showIcon
            message="最近任务暂时无法加载"
            description="请检查网络连接后重试，音频文件仍可正常使用。"
            action={<Button onClick={tasks.refresh}>重新加载</Button>}
          />
        )}
        {tasks.loading && recentTasks.length === 0 ? (
          <DashboardLoading label="正在加载最近任务" />
        ) : recentTasks.length > 0 ? (
          <RecentTaskList records={recentTasks} />
        ) : !tasks.error ? (
          <div className="dashboard-empty dashboard-empty--tasks">
            <span className="dashboard-empty__icon" aria-hidden="true"><ClockCircleOutlined /></span>
            <div><h3>还没有任务记录</h3><p>开始分析或处理音频后，最近进度会显示在这里。</p></div>
          </div>
        ) : null}
      </section>
    </PageContainer>
  )
}
