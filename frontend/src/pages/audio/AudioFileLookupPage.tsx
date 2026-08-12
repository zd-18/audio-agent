import { CloudUploadOutlined, DownloadOutlined, EyeOutlined, ReloadOutlined, SearchOutlined, UndoOutlined } from '@ant-design/icons'
import { Alert, Button, Input, Select, Space, Table, Tooltip, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { downloadAudioFile } from '../../api/audioFiles'
import CreateAnalysisTaskButton from '../../components/analysis/CreateAnalysisTaskButton'
import CreateTranscriptionButton from '../../components/transcription/CreateTranscriptionButton'
import TranscriptionStatusBadge from '../../components/transcription/TranscriptionStatusBadge'
import AudioFileStatusBadge from '../../components/workbench/AudioFileStatusBadge'
import EmptyState from '../../components/workbench/EmptyState'
import PageContainer from '../../components/workbench/PageContainer'
import PageTitle from '../../components/workbench/PageTitle'
import WorkbenchFilterBar, { WorkbenchFilterField } from '../../components/workbench/WorkbenchFilterBar'
import { useAudioFileList } from '../../hooks/useAudioFileList'
import { useUserSettings } from '../../settings/UserSettingsContext'
import type { AudioFileListItem } from '../../types/api'
import { formatBytes, formatDateTime, formatDuration } from '../../utils/formatters'

const FILE_STATUSES = [
  { value: 'UPLOADING', label: '上传中' },
  { value: 'AVAILABLE', label: '可用' },
  { value: 'PROCESSING', label: '处理中' },
  { value: 'FAILED', label: '失败' },
  { value: 'DELETED', label: '已删除' },
]

const PAGE_SIZES = [10, 20, 50]

function parsePageNumber(value: string | null, fallback: number) {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback
}

function formatResourceId(value: string) {
  return value.length > 15 ? `${value.slice(0, 6)}...${value.slice(-6)}` : value
}

function AudioFileIdValue({ value }: { value: string }) {
  return (
    <Typography.Text
      className="audio-file-list__id"
      copyable={{ text: value, tooltips: ['复制 audioFileId', '已复制'] }}
    >
      <Tooltip title={value}>
        <span className="audio-file-list__id-value">{formatResourceId(value)}</span>
      </Tooltip>
    </Typography.Text>
  )
}

export default function AudioFileLookupPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const { settings } = useUserSettings()
  const defaultPageSize = settings?.defaultPageSize ?? 10
  const current = parsePageNumber(searchParams.get('current'), 1)
  const requestedSize = parsePageNumber(searchParams.get('size'), defaultPageSize)
  const size = PAGE_SIZES.includes(requestedSize) ? requestedSize : defaultPageSize
  const keyword = searchParams.get('keyword')?.trim() || undefined
  const requestedStatus = searchParams.get('status') || undefined
  const status = FILE_STATUSES.some((item) => item.value === requestedStatus) ? requestedStatus : undefined
  const [keywordDraft, setKeywordDraft] = useState(keyword || '')
  const [statusDraft, setStatusDraft] = useState<string | undefined>(status)
  const [downloadError, setDownloadError] = useState<string | null>(null)
  const [downloadingId, setDownloadingId] = useState<string | null>(null)
  const downloadControllerRef = useRef<AbortController | null>(null)
  const downloadInFlightRef = useRef(false)
  const query = useMemo(() => ({ current, size, keyword, status }), [current, size, keyword, status])
  const { data, loading, error, refresh } = useAudioFileList(query)

  useEffect(() => {
    setKeywordDraft(keyword || '')
    setStatusDraft(status)
  }, [keyword, status])

  useEffect(() => () => downloadControllerRef.current?.abort(), [])

  const replaceQuery = (next: { current: number; size: number; keyword?: string; status?: string }) => {
    const params = new URLSearchParams()
    if (next.current !== 1) params.set('current', String(next.current))
    if (next.size !== defaultPageSize) params.set('size', String(next.size))
    if (next.keyword) params.set('keyword', next.keyword)
    if (next.status) params.set('status', next.status)
    setSearchParams(params)
  }

  const submitFilters = () => replaceQuery({ current: 1, size, keyword: keywordDraft.trim() || undefined, status: statusDraft })
  const resetFilters = () => {
    setKeywordDraft('')
    setStatusDraft(undefined)
    setSearchParams(new URLSearchParams())
  }

  const download = async (record: AudioFileListItem) => {
    if (downloadInFlightRef.current) return
    downloadInFlightRef.current = true
    const controller = new AbortController()
    downloadControllerRef.current = controller
    setDownloadingId(record.audioFileId)
    setDownloadError(null)
    try {
      await downloadAudioFile(record.audioFileId, record.originalFileName || `audio-${record.audioFileId}`, controller.signal)
    } catch (requestError) {
      if (!(requestError instanceof DOMException && requestError.name === 'AbortError')) {
        setDownloadError(requestError instanceof Error ? requestError.message : '文件下载失败')
      }
    } finally {
      if (!controller.signal.aborted) {
        setDownloadingId(null)
        downloadInFlightRef.current = false
      }
      if (downloadControllerRef.current === controller) downloadControllerRef.current = null
    }
  }

  const columns: ColumnsType<AudioFileListItem> = [
    {
      title: '文件名称',
      dataIndex: 'originalFileName',
      width: 260,
      render: (value: string | undefined, record) => (
        <div className="audio-file-list__name">
          <strong title={value}>{value || '未命名文件'}</strong>
          <AudioFileIdValue value={record.audioFileId} />
        </div>
      ),
    },
    { title: '文件类型', dataIndex: 'contentType', width: 150, render: (value?: string) => value || '—' },
    { title: '文件大小', dataIndex: 'fileSize', width: 110, render: formatBytes },
    { title: '音频时长', dataIndex: 'duration', width: 110, render: formatDuration },
    { title: '文件状态', dataIndex: 'status', width: 110, render: (value?: string) => <AudioFileStatusBadge status={value} /> },
    { title: '转写状态', dataIndex: 'transcriptionStatus', width: 130, render: (value?: string | null) => <TranscriptionStatusBadge status={value} /> },
    { title: '上传时间', dataIndex: 'createdAt', width: 170, render: formatDateTime },
    {
      title: '操作',
      key: 'actions',
      fixed: 'right',
      width: 430,
      render: (_, record) => (
        <Space size={2} wrap className="audio-file-list__actions">
          <Link to={`/audio/files/${record.audioFileId}`}><Button type="link" size="small" icon={<EyeOutlined />}>详情</Button></Link>
          <CreateAnalysisTaskButton audioFileId={record.audioFileId} fileName={record.originalFileName} buttonType="link" size="small" label="创建任务" />
          <CreateTranscriptionButton audioFileId={record.audioFileId} taskId={record.transcriptionTaskId} status={record.transcriptionStatus} buttonType="link" size="small" />
          <Button type="link" size="small" icon={<DownloadOutlined />} loading={downloadingId === record.audioFileId} disabled={downloadingId !== null && downloadingId !== record.audioFileId} onClick={() => download(record)}>下载</Button>
        </Space>
      ),
    },
  ]

  return (
    <PageContainer>
      <PageTitle eyebrow="AUDIO LIBRARY" title="音频文件" description="查询当前用户上传的真实音频文件，并继续创建分析任务。" actions={<Link to="/audio/upload"><Button type="primary" icon={<CloudUploadOutlined />}>上传音频</Button></Link>} />

      <section className="workbench-panel audio-file-filter" aria-labelledby="audio-file-filter-title">
        <div className="workbench-panel__heading"><div><span>FILTERS</span><h3 id="audio-file-filter-title">筛选文件</h3></div><Button type="text" icon={<ReloadOutlined />} loading={loading} onClick={refresh}>刷新</Button></div>
        <WorkbenchFilterBar
          layout="audio-files"
          onSubmit={(event) => { event.preventDefault(); submitFilters() }}
          actions={<><Button type="primary" htmlType="submit" icon={<SearchOutlined />}>查询</Button><Button icon={<UndoOutlined />} onClick={resetFilters}>重置</Button></>}
        >
          <WorkbenchFilterField label="文件名关键词">
            <Input value={keywordDraft} onChange={(event) => setKeywordDraft(event.target.value)} placeholder="输入文件名称" allowClear />
          </WorkbenchFilterField>
          <WorkbenchFilterField label="文件状态">
            <Select value={statusDraft} onChange={setStatusDraft} placeholder="全部状态" allowClear options={FILE_STATUSES} />
          </WorkbenchFilterField>
        </WorkbenchFilterBar>
      </section>

      {(error || downloadError) && <Alert className="resource-detail-alert" type="error" showIcon message={error ? '文件列表查询失败' : '文件下载失败'} description={error || downloadError} action={error ? <Button onClick={refresh}>重试</Button> : undefined} closable={Boolean(downloadError)} onClose={() => setDownloadError(null)} />}

      <section className="workbench-panel audio-file-list-panel">
        <div className="workbench-panel__heading"><div><span>REAL DATA</span><h3>文件列表</h3></div><small>共 {data.total.toLocaleString('zh-CN')} 条 · 第 {data.pages === 0 ? 0 : data.current} / {data.pages} 页</small></div>
        <Table<AudioFileListItem>
          rowKey="audioFileId"
          columns={columns}
          dataSource={data.records}
          loading={loading}
          scroll={{ x: 1480 }}
          locale={{ emptyText: <EmptyState title="暂无音频文件" description="当前筛选条件下没有记录，可以调整条件或上传新音频。" action={<Link to="/audio/upload"><Button type="primary">上传音频</Button></Link>} /> }}
          pagination={{
            current: data.current,
            pageSize: data.size,
            total: data.total,
            showSizeChanger: true,
            pageSizeOptions: PAGE_SIZES.map(String),
            showTotal: (total, range) => `${range[0]}-${range[1]} / ${total} 条`,
            onChange: (nextPage, nextSize) => replaceQuery({ current: nextSize !== size ? 1 : nextPage, size: nextSize, keyword, status }),
          }}
        />
      </section>
    </PageContainer>
  )
}
