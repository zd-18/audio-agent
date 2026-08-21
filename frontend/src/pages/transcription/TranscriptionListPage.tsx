import {
  EyeOutlined,
  FileTextOutlined,
  LoadingOutlined,
  ReloadOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import { Alert, Button, Progress, Select, Table, Tooltip } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import AudioFilePickerModal from '../../components/transcription/AudioFilePickerModal'
import TranscriptionStatusBadge from '../../components/transcription/TranscriptionStatusBadge'
import EmptyState from '../../components/workbench/EmptyState'
import PageContainer from '../../components/workbench/PageContainer'
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

const STATUS_OPTIONS: { value: TranscriptionTaskStatus; label: string }[] = [
  { value: 'PENDING', label: '等待转写' },
  { value: 'RUNNING', label: '转写中' },
  { value: 'SUCCESS', label: '转写完成' },
  { value: 'FAILED', label: '转写失败' },
]

function userFacingFailure(value?: string | null) {
  const message = value?.trim()
  if (!message) return '—'
  const technicalPattern = /(?:exception|traceback|stack\s*trace|\bat\s+[\w.$]+\(|java\.|org\.|com\.|funasr|grpc|redis)/i
  const containsTechnicalDetail = technicalPattern.test(message)
  return containsTechnicalDetail ? '语音识别服务暂时不可用' : message
}

function taskAction(record: TranscriptionTask) {
  if (record.status === 'SUCCESS') {
    return { label: '查看文字稿', icon: <EyeOutlined /> }
  }
  if (record.status === 'FAILED') {
    return { label: '查看详情', icon: <WarningOutlined /> }
  }
  return { label: '查看进度', icon: <LoadingOutlined /> }
}

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
  const [audioPickerOpen, setAudioPickerOpen] = useState(false)
  const [searchParams, setSearchParams] = useSearchParams()
  const { settings } = useUserSettings()
  const defaultSize = settings?.defaultPageSize ?? 10
  const current = positiveInt(searchParams.get('current'), 1)
  const requestedSize = positiveInt(searchParams.get('size'), defaultSize)
  const size = PAGE_SIZES.includes(requestedSize) ? requestedSize : defaultSize
  const status = parseStatus(searchParams.get('status'))
  const query = useMemo(() => ({ current, size, status }), [current, size, status])
  const { data, loading, error, refresh } = useTranscriptionTaskList(query)
  const replaceQuery = (next: { current: number; size: number; status?: TranscriptionTaskStatus }) => {
    const params = new URLSearchParams()
    if (next.current > 1) params.set('current', String(next.current))
    if (next.size !== defaultSize) params.set('size', String(next.size))
    if (next.status) params.set('status', next.status)
    setSearchParams(params)
  }

  const columns: ColumnsType<TranscriptionTask> = [
    {
      title: '音频文件', dataIndex: 'audioFileName', width: 300,
      render: (value: string | null, record) => (
        <div className="transcription-list__file">
          <Tooltip title={value || undefined} placement="topLeft">
            <strong>{value || '未命名音频'}</strong>
          </Tooltip>
          <div className="transcription-list__file-meta">
            <span>本次转写任务</span>
            <Link to={`/audio/files/${encodeURIComponent(record.audioFileId)}`}>查看源文件</Link>
          </div>
        </div>
      ),
    },
    { title: '状态', dataIndex: 'status', width: 118, render: (value: string) => <TranscriptionStatusBadge status={value} /> },
    {
      title: '进度', dataIndex: 'progressPercent', width: 128, responsive: ['lg'],
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
    { title: '创建时间', dataIndex: 'createdAt', width: 160, responsive: ['md'], render: formatDateTime },
    {
      title: '失败原因', dataIndex: 'failureMessage', width: 210, responsive: ['xl'], ellipsis: { showTitle: false },
      render: (value?: string | null) => {
        const message = userFacingFailure(value)
        return (
          <Tooltip title={message === '—' ? undefined : message}>
            <span className="transcription-list__failure">{message}</span>
          </Tooltip>
        )
      },
    },
    {
      title: '操作', key: 'actions', width: 150,
      render: (_, record) => {
        const action = taskAction(record)
        return (
          <span className="transcription-list__actions">
            <Link to={`/transcriptions/${encodeURIComponent(record.taskId)}`}>
              <Button type="link" icon={action.icon}>{action.label}</Button>
            </Link>
          </span>
        )
      },
    },
  ]

  return (
    <PageContainer>
      {error && <Alert className="resource-detail-alert" type="error" showIcon message="转写任务列表加载失败" description={error} action={<Button onClick={refresh}>重试</Button>} />}

      <section className="workbench-panel transcription-list-panel" aria-labelledby="transcription-list-title">
        <div className="transcription-list-toolbar">
          <div className="transcription-list-toolbar__title">
            <h2 id="transcription-list-title">转写任务</h2>
            <small>共 {data.total.toLocaleString('zh-CN')} 条</small>
          </div>
          <div className="transcription-list-toolbar__controls" aria-label="转写任务操作与筛选">
            <Button type="primary" icon={<FileTextOutlined />} onClick={() => setAudioPickerOpen(true)}>生成文字稿</Button>
            <label className="transcription-list-toolbar__filter" htmlFor="transcription-status-filter">
              <span className="transcription-list-toolbar__filter-label">任务状态</span>
              <Select
                id="transcription-status-filter"
                value={status}
                allowClear
                placeholder="全部状态"
                options={STATUS_OPTIONS}
                onChange={(next) => replaceQuery({ current: 1, size, status: next })}
              />
            </label>
            {status && <Button type="text" onClick={() => replaceQuery({ current: 1, size })}>重置筛选</Button>}
            <Button icon={<ReloadOutlined />} loading={loading} onClick={refresh}>刷新</Button>
          </div>
        </div>
        <Table<TranscriptionTask>
          rowKey="taskId"
          columns={columns}
          dataSource={data.records}
          loading={loading}
          tableLayout="fixed"
          locale={{ emptyText: <EmptyState title="暂无转写任务" description="选择一个已有音频并生成文字稿。" action={<Button type="primary" onClick={() => setAudioPickerOpen(true)}>生成文字稿</Button>} /> }}
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
      {audioPickerOpen && (
        <AudioFilePickerModal
          open
          onCancel={() => setAudioPickerOpen(false)}
          onCreated={refresh}
        />
      )}
    </PageContainer>
  )
}
