import { EyeOutlined, ReloadOutlined, UndoOutlined } from '@ant-design/icons'
import { Alert, Button, Progress, Select, Table, Tag, Tooltip, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { Link, useSearchParams } from 'react-router-dom'
import EmptyState from '../../components/workbench/EmptyState'
import PageContainer from '../../components/workbench/PageContainer'
import PageTitle from '../../components/workbench/PageTitle'
import { useProcessingExecutionList } from '../../hooks/useProcessingExecutionList'
import { useUserSettings } from '../../settings/UserSettingsContext'
import type { ProcessingExecutionListItem, ProcessingExecutionStatus } from '../../types/processingExecution'
import { formatDateTime } from '../../utils/formatters'
import { EXECUTION_STATUS_META, getStageLabel } from '../../utils/processingExecutionDisplay'
import './processing-execution-list.css'

const PAGE_SIZES = [10, 20, 50]
const STATUS_OPTIONS = (Object.keys(EXECUTION_STATUS_META) as ProcessingExecutionStatus[])
  .map((value) => ({ value, label: EXECUTION_STATUS_META[value].label }))

function positiveInteger(value: string | null, fallback: number) {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback
}

function executionStatus(value: string | null): ProcessingExecutionStatus | undefined {
  return STATUS_OPTIONS.some((item) => item.value === value)
    ? value as ProcessingExecutionStatus
    : undefined
}

function compactId(value: string) {
  return value.length > 17 ? `${value.slice(0, 7)}…${value.slice(-6)}` : value
}

export default function ProcessingExecutionListPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const { settings } = useUserSettings()
  const defaultPageSize = settings?.defaultPageSize ?? 10
  const current = positiveInteger(searchParams.get('current'), 1)
  const requestedSize = positiveInteger(searchParams.get('size'), defaultPageSize)
  const size = PAGE_SIZES.includes(requestedSize) ? requestedSize : defaultPageSize
  const status = executionStatus(searchParams.get('status'))
  const { data, loading, error, refresh } = useProcessingExecutionList({ current, size, status })

  const replaceQuery = (next: { current: number; size: number; status?: string }) => {
    const params = new URLSearchParams()
    if (next.current > 1) params.set('current', String(next.current))
    if (next.size !== defaultPageSize) params.set('size', String(next.size))
    if (next.status) params.set('status', next.status)
    setSearchParams(params)
  }

  const columns: ColumnsType<ProcessingExecutionListItem> = [
    {
      title: '处理任务',
      dataIndex: 'executionId',
      width: 180,
      render: (value: string) => (
        <Typography.Text copyable={{ text: value, tooltips: ['复制任务 ID', '已复制'] }}>
          <Tooltip title={value}>{compactId(value)}</Tooltip>
        </Typography.Text>
      ),
    },
    {
      title: '音频文件',
      dataIndex: 'fileName',
      width: 220,
      ellipsis: { showTitle: false },
      render: (value: string | null, record) => (
        <Tooltip title={value || record.audioFileId}>{value || `音频 ${compactId(record.audioFileId)}`}</Tooltip>
      ),
    },
    {
      title: '状态',
      dataIndex: 'executionStatus',
      width: 150,
      render: (value: ProcessingExecutionStatus) => {
        const meta = EXECUTION_STATUS_META[value]
        return <Tag className={`processing-list-status is-${meta.tone}`}>{meta.label}</Tag>
      },
    },
    {
      title: '进度',
      dataIndex: 'progressPercent',
      width: 170,
      render: (value: number, record) => (
        <div className="processing-list-progress">
          <Progress percent={Math.min(100, Math.max(0, value || 0))} size="small" showInfo={false} status={record.executionStatus === 'FAILED' || record.executionStatus === 'DEAD_LETTER' ? 'exception' : record.executionStatus === 'SUCCESS' ? 'success' : 'active'} />
          <span>{Math.round(value || 0)}%</span>
        </div>
      ),
    },
    {
      title: '当前阶段',
      dataIndex: 'currentStage',
      width: 170,
      render: (value: ProcessingExecutionListItem['currentStage']) => value ? getStageLabel(value) : '—',
    },
    { title: '创建时间', dataIndex: 'createdAt', width: 180, render: (value: string | null) => formatDateTime(value || undefined) },
    {
      title: '操作',
      key: 'action',
      fixed: 'right',
      width: 120,
      render: (_, record) => (
        <Link to={`/analysis/tasks/${encodeURIComponent(record.taskId)}/processing-execution`}>
          <Button type="link" icon={<EyeOutlined />}>查看</Button>
        </Link>
      ),
    },
  ]

  return (
    <PageContainer>
      <PageTitle
        eyebrow="PROCESSING TASKS"
        title="处理任务"
        actions={<Button icon={<ReloadOutlined />} loading={loading} onClick={refresh}>刷新列表</Button>}
      />

      <section className="workbench-panel processing-list-filter">
        <div>
          <label htmlFor="processing-status-filter">任务状态</label>
          <Select
            id="processing-status-filter"
            value={status}
            allowClear
            placeholder="全部状态"
            options={STATUS_OPTIONS}
            onChange={(nextStatus) => replaceQuery({ current: 1, size, status: nextStatus })}
          />
        </div>
        <Button icon={<UndoOutlined />} onClick={() => setSearchParams(new URLSearchParams())}>重置筛选</Button>
      </section>

      {error && <Alert className="resource-detail-alert" type="error" showIcon message="处理任务列表加载失败" description={error} action={<Button onClick={refresh}>重试</Button>} />}

      <section className="workbench-panel processing-list-panel">
        <div className="workbench-panel__heading"><div><span>REAL EXECUTIONS</span><h3>处理任务列表</h3></div><small>共 {data.total.toLocaleString('zh-CN')} 条</small></div>
        <Table<ProcessingExecutionListItem>
          rowKey="executionId"
          columns={columns}
          dataSource={data.records}
          loading={loading}
          scroll={{ x: 1190 }}
          locale={{ emptyText: <EmptyState title="暂无处理任务" description="完成处理方案确认并创建任务后，记录会显示在这里。" /> }}
          pagination={{
            current: data.current,
            pageSize: data.size,
            total: data.total,
            showSizeChanger: true,
            pageSizeOptions: PAGE_SIZES.map(String),
            showTotal: (total, range) => `${range[0]}-${range[1]} / ${total} 条`,
            onChange: (nextPage, nextSize) => replaceQuery({ current: nextSize !== size ? 1 : nextPage, size: nextSize, status }),
          }}
        />
      </section>
    </PageContainer>
  )
}
