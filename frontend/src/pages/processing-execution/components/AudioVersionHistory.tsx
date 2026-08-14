import {
  CheckCircleOutlined,
  DownloadOutlined,
  EditOutlined,
  HistoryOutlined,
  PlayCircleOutlined,
} from '@ant-design/icons'
import { Alert, Button, Modal, Skeleton } from 'antd'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useCreateAnalysisTask } from '../../../hooks/useCreateAnalysisTask'
import type { AudioVersion } from '../../../types/audioVersion'
import { formatBytes, formatDateTime, formatDuration } from '../../../utils/formatters'

interface AudioVersionHistoryProps {
  versions: AudioVersion[]
  loading: boolean
  error: string | null
  currentAudioFileId: string
  selectedAudioFileId: string
  downloading: boolean
  onRefresh: () => void
  onPreview: (version: AudioVersion) => void
  onDownload: (fileId: string, fileName: string) => void
}

function versionTitle(version: AudioVersion) {
  return version.originalVersion ? '原始版本' : `版本 ${version.versionNo}`
}

export default function AudioVersionHistory({
  versions,
  loading,
  error,
  currentAudioFileId,
  selectedAudioFileId,
  downloading,
  onRefresh,
  onPreview,
  onDownload,
}: AudioVersionHistoryProps) {
  const navigate = useNavigate()
  const createTask = useCreateAnalysisTask()
  const [continueFrom, setContinueFrom] = useState<AudioVersion | null>(null)

  const confirmContinue = async () => {
    if (!continueFrom) return
    const task = await createTask.create(continueFrom.audioFileId)
    if (task) {
      setContinueFrom(null)
      navigate(`/analysis/tasks/${encodeURIComponent(task.taskId)}`)
    }
  }

  return (
    <section className="processing-version-history" aria-labelledby="processing-version-history-title">
      <div className="processing-execution-section-heading processing-version-history__heading">
        <div>
          <span>VERSION HISTORY</span>
          <h2 id="processing-version-history-title">音频版本</h2>
          <p>每次处理都会保留为独立版本。可试听、下载，或选择任一版本继续修改。</p>
        </div>
        <HistoryOutlined aria-hidden="true" />
      </div>

      {loading && versions.length === 0 && (
        <div className="processing-version-history__loading" aria-label="正在加载音频版本">
          <Skeleton active paragraph={{ rows: 2 }} />
        </div>
      )}
      {error && (
        <Alert
          type="warning"
          showIcon
          message="版本记录暂时无法加载"
          description={error}
          action={<Button onClick={onRefresh}>重新加载</Button>}
        />
      )}
      {!loading && !error && versions.length === 0 && (
        <div className="processing-version-history__empty">暂无可用版本记录。</div>
      )}

      {versions.length > 0 && (
        <ol className="processing-version-list">
          {versions.map((version) => {
            const current = version.audioFileId === currentAudioFileId
            const selected = version.audioFileId === selectedAudioFileId
            return (
              <li
                key={version.audioFileId}
                className={`processing-version-item${current ? ' is-current' : ''}${selected ? ' is-selected' : ''}`}
                aria-current={current ? 'true' : undefined}
              >
                <div className="processing-version-item__rail" aria-hidden="true">
                  <span>{current ? <CheckCircleOutlined /> : version.versionNo}</span>
                </div>
                <div className="processing-version-item__body">
                  <div className="processing-version-item__copy">
                    <div className="processing-version-item__title">
                      <strong>{versionTitle(version)}</strong>
                      {current && <span>当前版本</span>}
                      {selected && !current && <span>正在试听</span>}
                    </div>
                    <p>{version.versionSummary || (version.originalVersion ? '原始版本' : '音频优化')}</p>
                    <small>
                      {formatDuration(version.durationMs ?? undefined)} · {formatBytes(version.sizeBytes ?? undefined)} · {formatDateTime(version.createdAt ?? undefined)}
                    </small>
                  </div>
                  <div className="processing-version-item__actions">
                    <Button
                      icon={<PlayCircleOutlined />}
                      type={selected ? 'primary' : 'default'}
                      onClick={() => onPreview(version)}
                    >
                      {selected ? '已选中试听' : '试听'}
                    </Button>
                    <Button
                      icon={<DownloadOutlined />}
                      loading={downloading}
                      onClick={() => onDownload(
                        version.audioFileId,
                        version.fileName || `${versionTitle(version)}.wav`,
                      )}
                    >
                      下载
                    </Button>
                    <Button
                      icon={<EditOutlined />}
                      onClick={() => {
                        createTask.resetError()
                        setContinueFrom(version)
                      }}
                    >
                      基于此版本继续修改
                    </Button>
                  </div>
                </div>
              </li>
            )
          })}
        </ol>
      )}

      <Modal
        title="基于所选版本继续修改"
        open={continueFrom !== null}
        okText="创建新的修改任务"
        cancelText="取消"
        confirmLoading={createTask.loading}
        closable={!createTask.loading}
        maskClosable={!createTask.loading}
        keyboard={!createTask.loading}
        onOk={() => { void confirmContinue() }}
        onCancel={() => {
          if (!createTask.loading) {
            setContinueFrom(null)
            createTask.resetError()
          }
        }}
      >
        <p>
          将以“{continueFrom ? versionTitle(continueFrom) : ''} · {continueFrom?.versionSummary || '音频版本'}”作为新的输入音频。
          后续分析、处理方案和处理执行都会基于该版本，现有版本不会被修改。
        </p>
        {createTask.error && (
          <Alert type="error" showIcon message="创建修改任务失败" description={createTask.error} />
        )}
      </Modal>
    </section>
  )
}
