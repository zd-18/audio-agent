import {
  CloudUploadOutlined,
  EyeOutlined,
  MoreOutlined,
  ReloadOutlined,
  SearchOutlined,
  UndoOutlined,
} from '@ant-design/icons'
import {
  Alert,
  Button,
  Dropdown,
  Input,
  Pagination,
  Progress,
  Select,
  Space,
  Spin,
  Table,
  Tooltip,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import ManualRetryButton from '../../components/analysis/ManualRetryButton'
import ReportAccessButton from '../../components/analysis/ReportAccessButton'
import EmptyState from '../../components/workbench/EmptyState'
import PageContainer from '../../components/workbench/PageContainer'
import PageTitle from '../../components/workbench/PageTitle'
import TaskStatusBadge from '../../components/workbench/TaskStatusBadge'
import WorkbenchFilterBar, { WorkbenchFilterField } from '../../components/workbench/WorkbenchFilterBar'
import { useAnalysisTaskList } from '../../hooks/useAnalysisTaskList'
import { useUserSettings } from '../../settings/UserSettingsContext'
import type { AnalysisTaskListItem, AnalysisTaskRecord, AnalysisTaskStatus } from '../../types/api'

const PAGE_SIZES = [10, 20, 50]
const TASK_STATUSES: Array<{ value: AnalysisTaskStatus; label: string }> = [
  { value: 'PENDING', label: '等待调度' },
  { value: 'PROCESSING', label: '正在分析' },
  { value: 'SUCCESS', label: '分析成功' },
  { value: 'FAILED', label: '分析失败' },
]
const TASK_STATUS_LABELS = Object.fromEntries(
  TASK_STATUSES.map((item) => [item.value, item.label]),
) as Record<AnalysisTaskStatus, string>
const ANALYSIS_TYPES = [{ value: 'FULL', label: 'FULL' }]

function readPositiveInteger(value: string | null, fallback: number) {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback
}

function readStatus(value: string | null): AnalysisTaskStatus | undefined {
  return TASK_STATUSES.some((item) => item.value === value)
    ? value as AnalysisTaskStatus
    : undefined
}

function normalizeProgress(value?: number) {
  if (value === undefined || value === null || Number.isNaN(value)) return 0
  return Math.min(100, Math.max(0, Math.round(value)))
}

function formatCompactDateTime(value?: string) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (part: number) => String(part).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function useMediaQuery(query: string) {
  const [matches, setMatches] = useState(false)

  useEffect(() => {
    const mediaQuery = window.matchMedia(query)
    const update = () => setMatches(mediaQuery.matches)
    update()
    mediaQuery.addEventListener('change', update)
    return () => mediaQuery.removeEventListener('change', update)
  }, [query])

  return matches
}

function TaskIdValue({ value }: { value: string }) {
  return (
    <Typography.Text
      className="analysis-task-list__id"
      copyable={{ text: value, tooltips: ['复制 taskId', '已复制'] }}
    >
      <Tooltip title={value}>
        <span className="analysis-task-list__id-value">{value}</span>
      </Tooltip>
    </Typography.Text>
  )
}

function FileNameValue({ value }: { value?: string }) {
  if (!value) return <span className="analysis-task-list__empty-value">—</span>
  return (
    <Tooltip title={value}>
      <span className="analysis-task-list__file-name">{value}</span>
    </Tooltip>
  )
}

function TaskProgressValue({ task }: { task: AnalysisTaskListItem }) {
  const progress = normalizeProgress(task.progress)
  return task.status === 'PROCESSING'
    ? (
      <div className="analysis-task-list__progress">
        <Progress percent={progress} size="small" showInfo={false} strokeColor="var(--workbench-blue)" />
        <span>{progress}%</span>
      </div>
    )
    : <span className="workbench-mono">{progress}%</span>
}

function TaskStatusValue({ task }: { task: AnalysisTaskListItem }) {
  const errorCode = task.lastErrorCode || '未提供错误码'
  return (
    <div className="analysis-task-list__status">
      <TaskStatusBadge status={task.status} label={TASK_STATUS_LABELS[task.status]} />
      {task.status === 'FAILED' && (
        <Tooltip
          title={(
            <div className="analysis-task-list__error-tooltip">
              <strong>{errorCode}</strong>
              {task.errorMessage && <span>{task.errorMessage}</span>}
            </div>
          )}
        >
          <span
            className="analysis-task-list__error-code"
            tabIndex={0}
            aria-label={`错误码：${errorCode}${task.errorMessage ? `，${task.errorMessage}` : ''}`}
          >
            {errorCode}
          </span>
        </Tooltip>
      )}
    </div>
  )
}

interface TaskActionsProps {
  task: AnalysisTaskListItem
  compact: boolean
  refreshingTaskId: string | null
  onRefresh: (taskId: string) => void
  onRetried: (task: AnalysisTaskRecord) => void
}

function TaskActions({ task, compact, refreshingTaskId, onRefresh, onRetried }: TaskActionsProps) {
  const [retryOpen, setRetryOpen] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)
  const isRefreshing = refreshingTaskId === task.taskId
  const refreshDisabled = refreshingTaskId !== null && !isRefreshing
  const refreshButton = (
    <Button
      type="text"
      size="small"
      icon={<ReloadOutlined />}
      loading={isRefreshing}
      disabled={refreshDisabled}
      onClick={() => {
        setMenuOpen(false)
        onRefresh(task.taskId)
      }}
    >
      刷新
    </Button>
  )
  const retryButton = task.status === 'FAILED' ? (
    <Button danger type="text" size="small" icon={<ReloadOutlined />} onClick={() => { setMenuOpen(false); setRetryOpen(true) }}>
      人工重试
    </Button>
  ) : null

  return (
    <Space size={compact ? 4 : 0} wrap={false} className="analysis-task-list__actions">
      <Link to={`/analysis/tasks/${task.taskId}`}>
        <Button type="link" size="small" icon={<EyeOutlined />}>详情</Button>
      </Link>
      {!compact && <ReportAccessButton taskId={task.taskId} status={task.status} type="link" size="small" />}
      <Dropdown
        trigger={['click']}
        placement="bottomRight"
        open={menuOpen}
        onOpenChange={setMenuOpen}
        menu={{ items: [] }}
        popupRender={() => (
          <div className="analysis-task-list__action-menu">
            {compact && <ReportAccessButton taskId={task.taskId} status={task.status} type="text" size="small" block />}
            {refreshButton}
            {retryButton}
          </div>
        )}
      >
        <Button type="text" size="small" icon={<MoreOutlined />}>更多</Button>
      </Dropdown>
      {task.status === 'FAILED' && (
        <ManualRetryButton
          taskId={task.taskId}
          open={retryOpen}
          hideTrigger
          onOpenChange={setRetryOpen}
          onRetried={onRetried}
        />
      )}
    </Space>
  )
}

export default function AnalysisTaskLookupPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const { settings } = useUserSettings()
  const defaultPageSize = settings?.defaultPageSize ?? 10
  const isMobile = useMediaQuery('(max-width: 767px)')
  const isCompactTable = useMediaQuery('(max-width: 1023px)')
  const current = readPositiveInteger(searchParams.get('current'), 1)
  const requestedSize = readPositiveInteger(searchParams.get('size'), defaultPageSize)
  const size = PAGE_SIZES.includes(requestedSize) ? requestedSize : defaultPageSize
  const status = readStatus(searchParams.get('status'))
  const keyword = searchParams.get('keyword')?.trim() || undefined
  const analysisType = searchParams.get('analysisType') === 'FULL' ? 'FULL' : undefined

  const [statusDraft, setStatusDraft] = useState<AnalysisTaskStatus | undefined>(status)
  const [keywordDraft, setKeywordDraft] = useState(keyword || '')
  const [analysisTypeDraft, setAnalysisTypeDraft] = useState<string | undefined>(analysisType)
  const {
    data,
    loading,
    error,
    actionError,
    refreshingTaskId,
    refresh,
    refreshTask,
    applyTaskUpdate,
    clearActionError,
  } = useAnalysisTaskList({ current, size, status, keyword, analysisType })

  useEffect(() => {
    setStatusDraft(status)
    setKeywordDraft(keyword || '')
    setAnalysisTypeDraft(analysisType)
  }, [status, keyword, analysisType])

  const replaceQuery = (next: {
    current: number
    size: number
    status?: string
    keyword?: string
    analysisType?: string
  }) => {
    const params = new URLSearchParams()
    if (next.current > 1) params.set('current', String(next.current))
    if (next.size !== defaultPageSize) params.set('size', String(next.size))
    if (next.status) params.set('status', next.status)
    if (next.keyword) params.set('keyword', next.keyword)
    if (next.analysisType) params.set('analysisType', next.analysisType)
    setSearchParams(params)
  }

  const submitFilters = () => replaceQuery({
    current: 1,
    size,
    status: statusDraft,
    keyword: keywordDraft.trim() || undefined,
    analysisType: analysisTypeDraft,
  })

  const resetFilters = () => {
    setStatusDraft(undefined)
    setKeywordDraft('')
    setAnalysisTypeDraft(undefined)
    setSearchParams(new URLSearchParams())
  }

  const handleRetried = (task: AnalysisTaskRecord) => {
    applyTaskUpdate(task)
    refresh()
  }

  const handlePageChange = (nextPage: number, nextSize: number) => {
    replaceQuery({
      current: nextSize !== size ? 1 : nextPage,
      size: nextSize,
      status,
      keyword,
      analysisType,
    })
  }

  const columns: ColumnsType<AnalysisTaskListItem> = [
    {
      title: 'taskId',
      dataIndex: 'taskId',
      width: 170,
      render: (value: string) => <TaskIdValue value={value} />,
    },
    {
      title: '文件名称',
      dataIndex: 'fileName',
      width: 220,
      ellipsis: { showTitle: false },
      render: (value?: string) => <FileNameValue value={value} />,
    },
    ...(!isCompactTable ? [{
      title: '分析类型',
      dataIndex: 'analysisType' as const,
      width: 90,
      render: (value?: string) => value || '—',
    }] : []),
    {
      title: '任务状态',
      key: 'status',
      width: 150,
      render: (_, record) => <TaskStatusValue task={record} />,
    },
    {
      title: '执行进度',
      key: 'progress',
      width: 120,
      render: (_, record) => <TaskProgressValue task={record} />,
    },
    {
      title: '重试次数',
      key: 'retry',
      width: 90,
      render: (_, record) => (
        <span className="workbench-mono">{record.retryCount ?? 0} / {record.maxRetryCount ?? '—'}</span>
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 160,
      render: (value?: string) => <span className="analysis-task-list__date">{formatCompactDateTime(value)}</span>,
    },
    {
      title: '操作',
      key: 'actions',
      fixed: 'right',
      width: isCompactTable ? 144 : 210,
      render: (_, record) => (
        <TaskActions
          task={record}
          compact={isCompactTable}
          refreshingTaskId={refreshingTaskId}
          onRefresh={(taskId) => void refreshTask(taskId)}
          onRetried={handleRetried}
        />
      ),
    },
  ]

  return (
    <PageContainer>
      <PageTitle
        eyebrow="ANALYSIS TASKS"
        title="分析任务"
        description="查询真实分析任务、跟踪执行进度，并处理失败任务。"
        actions={<Link to="/audio/upload"><Button type="primary" icon={<CloudUploadOutlined />}>从上传开始</Button></Link>}
      />

      <section className="workbench-panel analysis-task-filter" aria-labelledby="analysis-task-filter-title">
        <div className="workbench-panel__heading">
          <div><span>FILTERS</span><h3 id="analysis-task-filter-title">筛选任务</h3></div>
          <Button type="text" icon={<ReloadOutlined />} loading={loading} onClick={refresh}>刷新当前页</Button>
        </div>
        <WorkbenchFilterBar
          layout="analysis-tasks"
          onSubmit={(event) => { event.preventDefault(); submitFilters() }}
          actions={(
            <>
              <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>查询</Button>
              <Button icon={<UndoOutlined />} onClick={resetFilters}>重置</Button>
            </>
          )}
        >
          <WorkbenchFilterField label="任务状态">
            <Select value={statusDraft} onChange={setStatusDraft} placeholder="全部状态" allowClear options={TASK_STATUSES} />
          </WorkbenchFilterField>
          <WorkbenchFilterField label="文件名称">
            <Input
              value={keywordDraft}
              onChange={(event) => setKeywordDraft(event.target.value)}
              placeholder="输入文件名称关键词"
              allowClear
            />
          </WorkbenchFilterField>
          <WorkbenchFilterField label="分析类型">
            <Select value={analysisTypeDraft} onChange={setAnalysisTypeDraft} placeholder="全部类型" allowClear options={ANALYSIS_TYPES} />
          </WorkbenchFilterField>
        </WorkbenchFilterBar>
      </section>

      {(error || actionError) && (
        <Alert
          className="resource-detail-alert"
          type="error"
          showIcon
          message={error ? '任务列表查询失败' : '任务状态刷新失败'}
          description={error || actionError}
          action={error ? <Button onClick={refresh}>重试</Button> : undefined}
          closable={Boolean(actionError)}
          onClose={clearActionError}
        />
      )}

      <section className="workbench-panel analysis-task-list-panel">
        <div className="workbench-panel__heading">
          <div><span>REAL DATA</span><h3>任务列表</h3></div>
          <small>共 {data.total.toLocaleString('zh-CN')} 条 · 第 {data.pages === 0 ? 0 : data.current} / {data.pages} 页</small>
        </div>
        {isMobile ? (
          <Spin spinning={loading}>
            <div className="analysis-task-card-list" aria-live="polite">
              {data.records.length > 0 ? data.records.map((task) => (
                <article
                  key={task.taskId}
                  className={`analysis-task-card${task.status === 'FAILED' ? ' analysis-task-card--failed' : ''}`}
                  aria-label={`${task.fileName || task.taskId} 分析任务`}
                >
                  <div className="analysis-task-card__header">
                    <div className="analysis-task-card__identity">
                      <FileNameValue value={task.fileName} />
                      <TaskIdValue value={task.taskId} />
                    </div>
                    <TaskStatusValue task={task} />
                  </div>
                  <div className="analysis-task-card__meta">
                    <div><span>分析类型</span><strong>{task.analysisType || '—'}</strong></div>
                    <div><span>执行进度</span><TaskProgressValue task={task} /></div>
                    <div><span>重试次数</span><strong className="workbench-mono">{task.retryCount ?? 0} / {task.maxRetryCount ?? '—'}</strong></div>
                    <div><span>创建时间</span><strong className="analysis-task-list__date">{formatCompactDateTime(task.createdAt)}</strong></div>
                  </div>
                  <div className="analysis-task-card__footer">
                    <TaskActions
                      task={task}
                      compact
                      refreshingTaskId={refreshingTaskId}
                      onRefresh={(taskId) => void refreshTask(taskId)}
                      onRetried={handleRetried}
                    />
                  </div>
                </article>
              )) : (
                <EmptyState
                  title="暂无分析任务"
                  description="当前筛选条件下没有记录，可以调整条件或先上传音频并创建任务。"
                  action={<Link to="/audio/upload"><Button type="primary">上传音频</Button></Link>}
                />
              )}
            </div>
            {data.total > 0 && (
              <Pagination
                className="analysis-task-card-pagination"
                current={data.current}
                pageSize={data.size}
                total={data.total}
                disabled={loading}
                showLessItems
                showSizeChanger
                pageSizeOptions={PAGE_SIZES.map(String)}
                showTotal={(total) => `共 ${total} 条`}
                onChange={handlePageChange}
              />
            )}
          </Spin>
        ) : (
          <Table<AnalysisTaskListItem>
            rowKey="taskId"
            columns={columns}
            dataSource={data.records}
            loading={loading}
            tableLayout="fixed"
            scroll={{ x: isCompactTable ? 1054 : 1210 }}
            rowClassName={(record) => record.status === 'FAILED' ? 'analysis-task-row--failed' : ''}
            locale={{
              emptyText: (
                <EmptyState
                  title="暂无分析任务"
                  description="当前筛选条件下没有记录，可以调整条件或先上传音频并创建任务。"
                  action={<Link to="/audio/upload"><Button type="primary">上传音频</Button></Link>}
                />
              ),
            }}
            pagination={{
              current: data.current,
              pageSize: data.size,
              total: data.total,
              showSizeChanger: true,
              pageSizeOptions: PAGE_SIZES.map(String),
              showTotal: (total, range) => `${range[0]}-${range[1]} / ${total} 条`,
              onChange: handlePageChange,
            }}
          />
        )}
      </section>
    </PageContainer>
  )
}
