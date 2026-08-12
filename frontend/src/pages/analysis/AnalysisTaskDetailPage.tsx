import { AudioOutlined, ReloadOutlined } from '@ant-design/icons'
import { Alert, Button, Descriptions, Skeleton } from 'antd'
import { Link, useParams } from 'react-router-dom'
import { isValidResourceId } from '../../api/http'
import AudioMetadataGrid from '../../components/analysis/AudioMetadataGrid'
import ManualRetryButton from '../../components/analysis/ManualRetryButton'
import ProcessingPlanAccessButton from '../../components/analysis/ProcessingPlanAccessButton'
import ReportAccessButton from '../../components/analysis/ReportAccessButton'
import RetryInfoCard from '../../components/analysis/RetryInfoCard'
import TaskOverviewCard from '../../components/analysis/TaskOverviewCard'
import TaskProgressCard from '../../components/analysis/TaskProgressCard'
import CopyableValue from '../../components/workbench/CopyableValue'
import EmptyState from '../../components/workbench/EmptyState'
import PageContainer from '../../components/workbench/PageContainer'
import PageTitle from '../../components/workbench/PageTitle'
import TaskStatusBadge from '../../components/workbench/TaskStatusBadge'
import { useAnalysisTaskPolling } from '../../hooks/useAnalysisTaskPolling'
import { formatDateTime } from '../../utils/formatters'

export default function AnalysisTaskDetailPage() {
  const { taskId } = useParams()
  const validId = isValidResourceId(taskId) ? taskId : undefined
  const { task, loading, refreshing, error, lastUpdatedAt, refresh, resumeWith } = useAnalysisTaskPolling(validId)

  if (!validId) {
    return <PageContainer><PageTitle eyebrow="INVALID TASK" title="任务 ID 无效" description="URL 中的 taskId 为空或不是有效的正整数。" /><Alert type="error" showIcon message="无法查询任务" description="请返回分析任务页并输入创建接口返回的真实 taskId。" action={<Link to="/analysis/tasks"><Button>返回查询</Button></Link>} /></PageContainer>
  }

  return (
    <PageContainer>
      <PageTitle
        eyebrow="ANALYSIS TASK DETAIL"
        title="分析任务详情"
        description={`taskId: ${validId}`}
        actions={<><Button icon={<ReloadOutlined />} loading={refreshing} onClick={refresh}>手动刷新</Button>{task && <ReportAccessButton taskId={task.taskId} status={task.status} type={task.status === 'SUCCESS' ? 'primary' : 'default'} />}{task && <ProcessingPlanAccessButton taskId={task.taskId} status={task.status} />}{task?.status === 'FAILED' && <ManualRetryButton taskId={task.taskId} onRetried={resumeWith} />}</>}
      />

      {loading && !task && <section className="workbench-panel"><Skeleton active paragraph={{ rows: 10 }} /></section>}
      {error && <Alert className="resource-detail-alert" type="error" showIcon message={task ? '本次刷新失败' : '任务查询失败'} description={error} action={<Button onClick={refresh}>重试</Button>} />}

      {task && (
        <div className="analysis-detail-sections">
          <TaskOverviewCard task={task} />
          <TaskProgressCard task={task} lastUpdatedAt={lastUpdatedAt} />

          <section className="workbench-panel analysis-card">
            <div className="workbench-panel__heading"><div><span>RELATIONSHIP</span><h3>文件与任务关联信息</h3></div><TaskStatusBadge status={task.status} /></div>
            <Descriptions column={{ xs: 1, sm: 2 }} items={[
              { key: 'task', label: 'taskId', children: <CopyableValue value={task.taskId} mono /> },
              { key: 'file', label: 'audioFileId', children: <CopyableValue value={task.audioFileId} mono /> },
              { key: 'type', label: '分析类型', children: task.analysisType || '—' },
              { key: 'status', label: '当前状态', children: <TaskStatusBadge status={task.status} /> },
              { key: 'message', label: '消息 ID', span: 2, children: <CopyableValue value={task.lastMessageId} mono /> },
            ]} />
            <Link className="analysis-related-link" to={`/audio/files/${task.audioFileId}`}><AudioOutlined /> 查看关联音频文件</Link>
          </section>

          <RetryInfoCard task={task} />

          <section className="workbench-panel analysis-card">
            <div className="workbench-panel__heading"><div><span>FFPROBE RESULT</span><h3>ffprobe 元数据结果</h3></div></div>
            {task.status === 'SUCCESS' && task.result
              ? <AudioMetadataGrid result={task.result} />
              : <EmptyState title={task.status === 'FAILED' ? '本次分析未生成结果' : '等待分析结果'} description={task.status === 'FAILED' ? '可查看失败信息并发起人工重试。' : '任务完成后将在这里展示真实 ffprobe 元数据。'} />}
          </section>

          <section className="workbench-panel analysis-card">
            <div className="workbench-panel__heading"><div><span>TIMELINE</span><h3>时间信息</h3></div></div>
            <Descriptions column={{ xs: 1, sm: 3 }} items={[
              { key: 'created', label: '创建时间', children: formatDateTime(task.createdAt) },
              { key: 'started', label: '开始时间', children: formatDateTime(task.startedAt) },
              { key: 'finished', label: '完成时间', children: formatDateTime(task.finishedAt) },
            ]} />
          </section>

          <div className="resource-detail-actions">
            <Link to="/analysis/tasks"><Button>查询其他任务</Button></Link>
            <Link to="/audio/upload"><Button type="text">上传新音频</Button></Link>
          </div>
        </div>
      )}
    </PageContainer>
  )
}
