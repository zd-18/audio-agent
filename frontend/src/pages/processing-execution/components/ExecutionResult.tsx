import { CheckCircleOutlined, DownloadOutlined } from '@ant-design/icons'
import { Alert, Button, Skeleton } from 'antd'
import { useEffect, useState } from 'react'
import { useAudioVersions } from '../../../hooks/useAudioVersions'
import type { AudioFileRecord } from '../../../types/api'
import type { AudioVersion } from '../../../types/audioVersion'
import type { ProcessingExecution } from '../../../types/processingExecution'
import { formatBytes, formatDuration } from '../../../utils/formatters'
import AudioComparisonPlayer from './AudioComparisonPlayer'
import AudioVersionHistory from './AudioVersionHistory'

function fileFormat(file: AudioFileRecord | null) {
  if (file?.extension) return file.extension.toUpperCase()
  return file?.mimeType || '—'
}

export default function ExecutionResult({
  execution,
  sourceFile,
  resultFile,
  resultLoading,
  resultError,
  downloading,
  onRefreshResult,
  onDownload,
}: {
  execution: ProcessingExecution
  sourceFile: AudioFileRecord | null
  resultFile: AudioFileRecord | null
  resultLoading: boolean
  resultError: string | null
  downloading: boolean
  onRefreshResult: () => void
  onDownload: (fileId: string, fileName: string) => void
}) {
  const resultFileId = execution.resultFileId
  const versionState = useAudioVersions(execution.taskId)
  const [selectedAudioFileId, setSelectedAudioFileId] = useState(resultFileId || '')

  useEffect(() => {
    if (resultFileId) setSelectedAudioFileId(resultFileId)
  }, [resultFileId])

  if (!resultFileId) return null

  const selectedVersion = versionState.versions.find(
    (version) => version.audioFileId === selectedAudioFileId,
  )
  const selectedFileId = selectedVersion?.audioFileId || resultFileId
  const selectedFileName = selectedVersion?.fileName || resultFile?.originalName
  const selectedDurationMs = selectedVersion?.durationMs ?? resultFile?.durationMs
  const selectedVersionLabel = selectedVersion?.originalVersion
    ? '原始版本'
    : selectedVersion ? `版本 ${selectedVersion.versionNo}` : '当前修复结果'

  const previewVersion = (version: AudioVersion) => {
    setSelectedAudioFileId(version.audioFileId)
  }

  return (
    <section id="processing-execution-result" className="processing-execution-result" aria-labelledby="processing-execution-result-title">
      <div className="processing-execution-result__heading">
        <span className="processing-execution-result__icon" aria-hidden="true"><CheckCircleOutlined /></span>
        <div>
          <span>RESULT READY</span>
          <h2 id="processing-execution-result-title">处理完成</h2>
          <p>新的结果音频已经生成，原始音频仍被完整保留。</p>
        </div>
        <Button
          type="primary"
          size="large"
          icon={<DownloadOutlined />}
          loading={downloading}
          disabled={resultLoading || !resultFile}
          onClick={() => onDownload(resultFileId, resultFile?.originalName || `audio-${resultFileId}`)}
        >
          下载修复结果
        </Button>
      </div>

      {resultLoading && !resultFile && (
        <div className="processing-execution-result__skeleton" aria-label="正在加载结果文件信息">
          <Skeleton active paragraph={{ rows: 2 }} />
        </div>
      )}
      {resultError && (
        <Alert
          type="warning"
          showIcon
          message="结果文件信息暂时无法加载"
          description={resultError}
          action={<Button onClick={onRefreshResult}>重新加载</Button>}
        />
      )}
      {resultFile && (
        <dl className="processing-execution-result__metadata">
          <div><dt>结果文件名</dt><dd title={resultFile.originalName}>{resultFile.originalName || '—'}</dd></div>
          <div><dt>格式</dt><dd>{fileFormat(resultFile)}</dd></div>
          <div><dt>文件大小</dt><dd>{formatBytes(resultFile.sizeBytes)}</dd></div>
          <div><dt>时长</dt><dd>{formatDuration(resultFile.durationMs)}</dd></div>
          <div><dt>采样率</dt><dd title="当前文件详情接口未提供该字段">—</dd></div>
          <div><dt>声道</dt><dd title="当前文件详情接口未提供该字段">—</dd></div>
        </dl>
      )}

      <AudioVersionHistory
        versions={versionState.versions}
        loading={versionState.loading}
        error={versionState.error}
        currentAudioFileId={resultFileId}
        selectedAudioFileId={selectedFileId}
        downloading={downloading}
        onRefresh={versionState.refresh}
        onPreview={previewVersion}
        onDownload={onDownload}
      />

      <AudioComparisonPlayer
        sourceFileId={execution.audioFileId}
        resultFileId={selectedFileId}
        sourceFileName={sourceFile?.originalName}
        resultFileName={selectedFileName}
        resultDurationMs={selectedDurationMs}
        resultVersionLabel={selectedVersionLabel}
        downloading={downloading}
        onDownload={onDownload}
      />
    </section>
  )
}
