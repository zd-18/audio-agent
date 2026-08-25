import { CloudUploadOutlined, DeleteOutlined, DownloadOutlined, DownOutlined, InboxOutlined, ReloadOutlined, RollbackOutlined, SearchOutlined, ToolOutlined, UndoOutlined } from '@ant-design/icons'
import { Alert, App as AntdApp, Button, Dropdown, Input, Select, Space, Table, Tooltip } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { downloadAudioFile, moveAudioFileToRecycleBin, purgeAudioFile, restoreAudioFileFromRecycleBin } from '../../api/audioFiles'
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
  { value: 'ARCHIVED', label: '已归档' },
]

const PAGE_SIZES = [10, 20, 50]

/** 所有列宽之和（scroll.x 必须等于该值，fixed 列才能精确对齐） */
const TABLE_MIN_WIDTH = 1210

const MIME_FORMATS: Record<string, string> = {
  'audio/aac': 'AAC',
  'audio/flac': 'FLAC',
  'audio/m4a': 'M4A',
  'audio/mp4': 'M4A',
  'audio/mpeg': 'MP3',
  'audio/ogg': 'OGG',
  'audio/wav': 'WAV',
  'audio/wave': 'WAV',
  'audio/x-m4a': 'M4A',
  'audio/x-wav': 'WAV',
  'video/mp4': 'MP4',
}

const EXTENSION_FORMATS: Record<string, string> = {
  mpeg: 'MP3',
  mpga: 'MP3',
  wave: 'WAV',
}

function parsePageNumber(value: string | null, fallback: number) {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback
}

function audioFileFormat(record: AudioFileListItem) {
  const extension = record.originalFileName?.match(/\.([a-z0-9]{1,8})$/i)?.[1].toLowerCase()
  if (extension) return EXTENSION_FORMATS[extension] || extension.toUpperCase()
  return MIME_FORMATS[record.contentType?.toLowerCase() || ''] || '音频'
}

export default function AudioFileLookupPage() {
  const { message, modal } = AntdApp.useApp()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const { settings } = useUserSettings()
  const defaultPageSize = settings?.defaultPageSize ?? 10
  const current = parsePageNumber(searchParams.get('current'), 1)
  const requestedSize = parsePageNumber(searchParams.get('size'), defaultPageSize)
  const size = PAGE_SIZES.includes(requestedSize) ? requestedSize : defaultPageSize
  const keyword = searchParams.get('keyword')?.trim() || undefined
  const requestedStatus = searchParams.get('status') || undefined
  const status = FILE_STATUSES.some((item) => item.value === requestedStatus) ? requestedStatus : undefined
  const scope: 'active' | 'trash' = searchParams.get('scope') === 'trash' ? 'trash' : 'active'
  const [keywordDraft, setKeywordDraft] = useState(keyword || '')
  const [statusDraft, setStatusDraft] = useState<string | undefined>(status)
  const [downloadError, setDownloadError] = useState<string | null>(null)
  const [downloadingId, setDownloadingId] = useState<string | null>(null)
  const [mutatingId, setMutatingId] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [fixedActions, setFixedActions] = useState(true)
  const panelRef = useRef<HTMLElement | null>(null)
  const downloadControllerRef = useRef<AbortController | null>(null)
  const downloadInFlightRef = useRef(false)
  const query = useMemo(() => ({ current, size, keyword, status: scope === 'active' ? status : undefined, scope }), [current, scope, size, keyword, status])
  const { data, loading, error, refresh } = useAudioFileList(query)

  useEffect(() => {
    setKeywordDraft(keyword || '')
    setStatusDraft(status)
  }, [keyword, status])

  useEffect(() => () => downloadControllerRef.current?.abort(), [])

  // 容器放不下整张表（窄窗口）时取消操作列固定，避免固定列在默认滚动位置压住"转写状态/上传时间"；
  // 放得下时保持 fixed: 'right'，列贴右缘、与上传时间无缝邻接。
  useEffect(() => {
    const el = panelRef.current
    if (!el) return
    const observer = new ResizeObserver(() => {
      setFixedActions(el.clientWidth >= TABLE_MIN_WIDTH)
    })
    observer.observe(el)
    return () => observer.disconnect()
  }, [])

  const replaceQuery = (next: { current: number; size: number; keyword?: string; status?: string; scope?: 'active' | 'trash' }) => {
    const params = new URLSearchParams()
    if (next.current !== 1) params.set('current', String(next.current))
    if (next.size !== defaultPageSize) params.set('size', String(next.size))
    if (next.keyword) params.set('keyword', next.keyword)
    if (next.status) params.set('status', next.status)
    if ((next.scope ?? scope) === 'trash') params.set('scope', 'trash')
    setSearchParams(params)
  }

  const submitFilters = () => replaceQuery({ current: 1, size, keyword: keywordDraft.trim() || undefined, status: scope === 'active' ? statusDraft : undefined, scope })
  const resetFilters = () => {
    setKeywordDraft('')
    setStatusDraft(undefined)
    setSearchParams(scope === 'trash' ? new URLSearchParams({ scope: 'trash' }) : new URLSearchParams())
  }

  const switchScope = (nextScope: 'active' | 'trash') => {
    setKeywordDraft('')
    setStatusDraft(undefined)
    setSearchParams(nextScope === 'trash' ? new URLSearchParams({ scope: 'trash' }) : new URLSearchParams())
  }

  const moveToTrash = (record: AudioFileListItem) => {
    modal.confirm({
      title: '移入回收站？',
      content: `${record.originalFileName || '该文件'}将暂停播放、分析和处理，之后仍可从回收站恢复。`,
      okText: '移入回收站',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        setMutatingId(record.audioFileId)
        setActionError(null)
        try {
          await moveAudioFileToRecycleBin(record.audioFileId)
          refresh()
          void message.success('文件已移入回收站')
        } catch (requestError) {
          setActionError(requestError instanceof Error ? requestError.message : '文件删除失败')
          throw requestError
        } finally {
          setMutatingId(null)
        }
      },
    })
  }

  const restoreFromTrash = async (record: AudioFileListItem) => {
    setMutatingId(record.audioFileId)
    setActionError(null)
    try {
      await restoreAudioFileFromRecycleBin(record.audioFileId)
      refresh()
      void message.success('文件已恢复')
    } catch (requestError) {
      setActionError(requestError instanceof Error ? requestError.message : '文件恢复失败')
    } finally {
      setMutatingId(null)
    }
  }

  const purgeFromTrash = (record: AudioFileListItem) => {
    modal.confirm({
      title: '永久清理文件对象？',
      content: 'MinIO 中的文件对象将被删除，历史任务元数据会保留，但此操作无法恢复。',
      okText: '永久清理',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        setMutatingId(record.audioFileId)
        setActionError(null)
        try {
          await purgeAudioFile(record.audioFileId)
          refresh()
          void message.success('文件对象已永久清理')
        } catch (requestError) {
          setActionError(requestError instanceof Error ? requestError.message : '永久清理失败')
          throw requestError
        } finally {
          setMutatingId(null)
        }
      },
    })
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
      width: 360,
      render: (value?: string) => {
        const fileName = value || '未命名音频'
        return (
          <div className="audio-file-list__name">
            <Tooltip title={fileName} placement="topLeft"><strong>{fileName}</strong></Tooltip>
          </div>
        )
      },
    },
    { title: '文件格式', key: 'format', width: 100, render: (_, record) => <span className="audio-file-list__format">{audioFileFormat(record)}</span> },
    { title: '文件大小', dataIndex: 'fileSize', width: 100, render: (value?: number) => <span className="audio-file-list__meta">{formatBytes(value)}</span> },
    { title: '音频时长', dataIndex: 'duration', width: 100, render: (value?: number) => <span className="audio-file-list__meta">{formatDuration(value)}</span> },
    { title: '文件状态', dataIndex: 'status', width: 110, render: (value?: string) => <AudioFileStatusBadge status={value} /> },
    { title: '转写状态', dataIndex: 'transcriptionStatus', width: 130, render: (value?: string | null) => <TranscriptionStatusBadge status={value} /> },
    {
      title: scope === 'trash' ? '删除时间' : '上传时间',
      dataIndex: scope === 'trash' ? 'deletedAt' : 'createdAt',
      width: 180,
      render: (value?: string | null) => (
        <span className="audio-file-list__time">{formatDateTime(value ?? undefined)}</span>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      fixed: fixedActions ? 'right' : undefined,
      width: 120,
      render: (_, record) => (
        <div className="audio-file-list__actions" onClick={(event) => event.stopPropagation()} onKeyDown={(event) => event.stopPropagation()}>
          {scope === 'trash' ? (
            <Space size={4}>
              <Button type="link" size="small" icon={<RollbackOutlined />} loading={mutatingId === record.audioFileId} disabled={mutatingId !== null && mutatingId !== record.audioFileId} onClick={() => { void restoreFromTrash(record) }}>恢复</Button>
              <Button type="link" danger size="small" icon={<DeleteOutlined />} disabled={mutatingId !== null} onClick={() => purgeFromTrash(record)}>清理</Button>
            </Space>
          ) : <Dropdown
            trigger={['click']}
            menu={{
              items: [
                {
                  key: 'agent',
                  icon: <ToolOutlined />,
                  label: <Link to={`/audio/files/${record.audioFileId}/agent`}>智能处理</Link>,
                },
                {
                  key: 'analysis',
                  label: (
                    <span className="audio-file-list__menu-button">
                      <CreateAnalysisTaskButton audioFileId={record.audioFileId} fileName={record.originalFileName} buttonType="text" size="small" label="创建分析任务" block />
                    </span>
                  ),
                },
                {
                  key: 'transcription',
                  label: (
                    <span className="audio-file-list__menu-button">
                      <CreateTranscriptionButton
                        audioFileId={record.audioFileId}
                        taskId={record.transcriptionTaskId}
                        status={record.transcriptionStatus}
                        buttonType="text"
                        size="small"
                        block
                      />
                    </span>
                  ),
                },
                {
                  key: 'download',
                  label: downloadingId === record.audioFileId ? '下载中…' : '下载',
                  icon: <DownloadOutlined />,
                  disabled: downloadingId !== null && downloadingId !== record.audioFileId,
                  onClick: () => download(record),
                },
                { type: 'divider' },
                {
                  key: 'delete',
                  danger: true,
                  icon: <DeleteOutlined />,
                  label: '移入回收站',
                  disabled: ['PROCESSING', 'UPLOADING'].includes(record.status || '') || mutatingId !== null,
                  onClick: () => moveToTrash(record),
                },
              ],
            }}
          >
            <Button type="text" icon={<DownOutlined />} aria-label={`${record.originalFileName || '未命名音频'}更多操作`}>更多</Button>
          </Dropdown>}
        </div>
      ),
    },
  ]

  return (
    <PageContainer>
      <PageTitle eyebrow="AUDIO LIBRARY" title={scope === 'trash' ? '音频回收站' : '音频文件'} actions={<Space wrap><Button icon={scope === 'trash' ? <RollbackOutlined /> : <InboxOutlined />} onClick={() => switchScope(scope === 'trash' ? 'active' : 'trash')}>{scope === 'trash' ? '返回文件列表' : '回收站'}</Button>{scope === 'active' && <Link to="/audio/upload"><Button type="primary" icon={<CloudUploadOutlined />}>上传音频</Button></Link>}</Space>} />

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
          {scope === 'active' && <WorkbenchFilterField label="文件状态">
            <Select value={statusDraft} onChange={setStatusDraft} placeholder="全部状态" allowClear options={FILE_STATUSES} />
          </WorkbenchFilterField>}
        </WorkbenchFilterBar>
      </section>

      {(error || downloadError || actionError) && <Alert className="resource-detail-alert" type="error" showIcon message={error ? '文件列表查询失败' : actionError ? '文件操作失败' : '文件下载失败'} description={error || actionError || downloadError} action={error ? <Button onClick={refresh}>重试</Button> : undefined} closable={Boolean(downloadError || actionError)} onClose={() => { setDownloadError(null); setActionError(null) }} />}

      <section ref={panelRef} className="workbench-panel audio-file-list-panel">
        <div className="workbench-panel__heading"><div><span>REAL DATA</span><h3>文件列表</h3></div><small>共 {data.total.toLocaleString('zh-CN')} 条 · 第 {data.pages === 0 ? 0 : data.current} / {data.pages} 页</small></div>
        <Table<AudioFileListItem>
          rowKey="audioFileId"
          columns={columns}
          dataSource={data.records}
          loading={loading}
          scroll={{ x: TABLE_MIN_WIDTH }}
          tableLayout="fixed"
          locale={{ emptyText: <EmptyState title={scope === 'trash' ? '回收站为空' : '暂无音频文件'} description={scope === 'trash' ? '移入回收站的文件会显示在这里。' : '当前筛选条件下没有记录，可以调整条件或上传新音频。'} action={scope === 'active' ? <Link to="/audio/upload"><Button type="primary">上传音频</Button></Link> : undefined} /> }}
          onRow={(record) => scope === 'trash' ? ({}) : ({
            className: 'audio-file-list__row',
            tabIndex: 0,
            'aria-label': `查看音频详情：${record.originalFileName || '未命名音频'}`,
            onClick: () => navigate(`/audio/files/${encodeURIComponent(record.audioFileId)}`),
            onKeyDown: (event) => {
              if (event.key !== 'Enter' && event.key !== ' ') return
              event.preventDefault()
              navigate(`/audio/files/${encodeURIComponent(record.audioFileId)}`)
            },
          })}
          pagination={{
            current: data.current,
            pageSize: data.size,
            total: data.total,
            showSizeChanger: true,
            pageSizeOptions: PAGE_SIZES.map(String),
            showTotal: (total, range) => `${range[0]}-${range[1]} / ${total} 条`,
            onChange: (nextPage, nextSize) => replaceQuery({ current: nextSize !== size ? 1 : nextPage, size: nextSize, keyword, status: scope === 'active' ? status : undefined, scope }),
          }}
        />
      </section>
    </PageContainer>
  )
}
