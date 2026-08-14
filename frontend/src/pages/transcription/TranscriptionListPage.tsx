import { EyeOutlined, FileTextOutlined, ReloadOutlined, UndoOutlined } from '@ant-design/icons'
import { Alert, Button, Progress, Select, Table, Tooltip } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import TranscriptionStatusBadge from '../../components/transcription/TranscriptionStatusBadge'
import EmptyState from '../../components/workbench/EmptyState'
import PageContainer from '../../components/workbench/PageContainer'
import PageTitle from '../../components/workbench/PageTitle'
import { useTranscriptionTaskList } from '../../hooks/useTranscriptionTaskList'
import { useUserSettings } from '../../settings/UserSettingsContext'
import type { TranscriptionTask, TranscriptionTaskStatus } from '../../types/transcription'
import { formatDateTime } from '../../utils/formatters'
import {
  clampTranscriptionProgress,
  getTranscriptionProgressText,
} from '../../utils/transcription'
import './transcription.css'

const PAGE_SIZES = [10, 20, 50]

/** 所有列宽之和（scroll.x 必须等于该值，fixed 列才能精确对齐） */
const TABLE_MIN_WIDTH = 1360
const STATUS_OPTIONS: { value: TranscriptionTaskStatus; label: string }[] = [
  { value: 'PENDING', label: '等待处理' },
  { value: 'RUNNING', label: '正在转写' },
  { value: 'SUCCESS', label: '转写完成' },
  { value: 'FAILED', label: '转写失败' },
]

function positiveInt(value: string | null, fallback: number) {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback
}

function parseStatus(value: string | null) {
  return STATUS_OPTIONS.some((item) => item.value === value)
    ? value as TranscriptionTaskStatus
    : undefined
}

export default function TranscriptionListPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const { settings } = useUserSettings()
  const defaultSize = settings?.defaultPageSize ?? 10
  const current = positiveInt(searchParams.get('current'), 1)
  const requestedSize = positiveInt(searchParams.get('size'), defaultSize)
  const size = PAGE_SIZES.includes(requestedSize) ? requestedSize : defaultSize
  const status = parseStatus(searchParams.get('status'))
  const query = useMemo(() => ({ current, size, status }), [current, size, status])
  const { data, loading, error, refresh } = useTranscriptionTaskList(query)
  const [fixedColumns, setFixedColumns] = useState(true)
  const panelRef = useRef<HTMLElement | null>(null)

  // 容器放不下整张表（窄窗口）时取消左右固定列，避免固定列在默认滚动位置压住相邻列；
  // 放得下时保持 fixed: 'left' / 'right'，列贴边缘、与内容区无缝邻接。
  useEffect(() => {
    const el = panelRef.current
    if (!el) return
    const observer = new ResizeObserver(() => {
      setFixedColumns(el.clientWidth >= TABLE_MIN_WIDTH)
    })
    observer.observe(el)
    return () => observer.disconnect()
  }, [])

  const replaceQuery = (next: { current: number; size: number; status?: TranscriptionTaskStatus }) => {
    const params = new URLSearchParams()
    if (next.current > 1) params.set('current', String(next.current))
    if (next.size !== defaultSize) params.set('size', String(next.size))
    if (next.status) params.set('status', next.status)
    setSearchParams(params)
  }

  const columns: ColumnsType<TranscriptionTask> = [
    {
      title: '音频文件', dataIndex: 'audioFileName', width: 280, fixed: fixedColumns ? 'left' : undefined,
      render: (value: string | null, record) => (
        <div className="transcription-list__file">
          <strong title={value || undefined}>{value || '未命名音频'}</strong>
          <Link to={`/audio/files/${encodeURIComponent(record.audioFileId)}`}>查看源文件</Link>
        </div>
      ),
    },
    { title: '状态', dataIndex: 'status', width: 150, render: (value: string) => <TranscriptionStatusBadge status={value} /> },
    {
      title: '进度', dataIndex: 'progressPercent', width: 190,
      render: (value: number, record) => {
        const progress = clampTranscriptionProgress(value)
        return (
          <Tooltip title={getTranscriptionProgressText(progress, record.status)}>
            <div className="transcription-list__progress">
              <Progress
                percent={progress}
                showInfo={false}
                size="small"
                status={record.status === 'FAILED' ? 'exception' : record.status === 'SUCCESS' ? 'success' : 'active'}
              />
              <span>{progress}%</span>
            </div>
          </Tooltip>
        )
      },
    },
    { title: '语言', dataIndex: 'language', width: 100, render: (value: string) => value.toUpperCase() },
    { title: '创建时间', dataIndex: 'createdAt', width: 190, render: formatDateTime },
    {
      title: '失败原因', dataIndex: 'failureMessage', width: 300, ellipsis: { showTitle: false },
      render: (value?: string | null) => (
        <Tooltip title={value}>
          <span className="transcription-list__failure">{value || '—'}</span>
        </Tooltip>
      ),
    },
    {
      title: '操作', key: 'actions', fixed: fixedColumns ? 'right' : undefined, width: 150,
      render: (_, record) => (
        <span className="transcription-list__actions">
          <Link to={`/transcriptions/${encodeURIComponent(record.taskId)}`}>
            <Button type="link" icon={<EyeOutlined />}>{record.status === 'SUCCESS' ? '文字稿' : '查看'}</Button>
          </Link>
        </span>
      ),
    },
  ]

  return (
    <PageContainer>
      <PageTitle
        eyebrow="TRANSCRIPTIONS"
        title="音频转写"
        description="查看真实语音识别任务、全文与时间戳片段。"
        actions={<><Link to="/audio/files"><Button type="primary" icon={<FileTextOutlined />}>选择音频</Button></Link><Button icon={<ReloadOutlined />} loading={loading} onClick={refresh}>刷新</Button></>}
      />

      <section className="workbench-panel transcription-filter" aria-label="转写任务筛选">
        <div>
          <label htmlFor="transcription-status-filter">任务状态</label>
          <Select
            id="transcription-status-filter"
            value={status}
            allowClear
            placeholder="全部状态"
            options={STATUS_OPTIONS}
            onChange={(next) => replaceQuery({ current: 1, size, status: next })}
          />
        </div>
        <Button icon={<UndoOutlined />} onClick={() => setSearchParams(new URLSearchParams())}>重置筛选</Button>
      </section>

      {error && <Alert className="resource-detail-alert" type="error" showIcon message="转写任务列表加载失败" description={error} action={<Button onClick={refresh}>重试</Button>} />}

      <section ref={panelRef} className="workbench-panel transcription-list-panel">
        <div className="workbench-panel__heading"><div><span>TRANSCRIPTION TASKS</span><h3>转写任务</h3></div><small>共 {data.total.toLocaleString('zh-CN')} 条</small></div>
        <Table<TranscriptionTask>
          rowKey="taskId"
          columns={columns}
          dataSource={data.records}
          loading={loading}
          scroll={{ x: TABLE_MIN_WIDTH }}
          tableLayout="fixed"
          locale={{ emptyText: <EmptyState title="暂无转写任务" description="从音频文件页选择一个可用文件并生成文字稿。" action={<Link to="/audio/files"><Button type="primary">选择音频</Button></Link>} /> }}
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
