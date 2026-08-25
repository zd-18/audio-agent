import { ArrowLeftOutlined, ReloadOutlined } from '@ant-design/icons'
import { Alert, App as AntdApp, Button } from 'antd'
import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { downloadAudioFile } from '../../api/audioFiles'
import { isValidResourceId } from '../../api/http'
import PageContainer from '../../components/workbench/PageContainer'
import PageTitle from '../../components/workbench/PageTitle'
import { useAudioFileDetail } from '../../hooks/useAudioFileDetail'
import { useProcessingExecution } from '../../hooks/useProcessingExecution'
import { useUserSettings } from '../../settings/UserSettingsContext'
import '../analysis/analysis-report.css'
import './processing-execution.css'
import ExecutionFailure from './components/ExecutionFailure'
import ExecutionProgress from './components/ExecutionProgress'
import ExecutionResult from './components/ExecutionResult'
import ExecutionStepList from './components/ExecutionStepList'
import ExecutionSummary from './components/ExecutionSummary'
import ProcessingExecutionSkeleton from './components/ProcessingExecutionSkeleton'

export default function ProcessingExecutionPage() {
  const { message, modal } = AntdApp.useApp()
  const { taskId } = useParams()
  const validTaskId = isValidResourceId(taskId) ? taskId : undefined
  const state = useProcessingExecution({ taskId: validTaskId, autoPoll: true })
  const { settings } = useUserSettings()
  const execution = state.execution?.taskId === validTaskId ? state.execution : null
  const sourceFile = useAudioFileDetail(execution?.audioFileId)
  const resultFile = useAudioFileDetail(execution?.resultFileId || undefined)
  const [downloading, setDownloading] = useState(false)
  const [downloadError, setDownloadError] = useState<string | null>(null)
  const downloadControllerRef = useRef<AbortController | null>(null)
  const previousStatusRef = useRef<string>()

  useEffect(() => () => downloadControllerRef.current?.abort(), [])

  useEffect(() => {
    previousStatusRef.current = undefined
  }, [validTaskId])

  useEffect(() => {
    const nextStatus = execution?.executionStatus
    const previousStatus = previousStatusRef.current
    previousStatusRef.current = nextStatus
    if (previousStatus !== 'PROCESSING' || nextStatus !== 'SUCCESS'
      || !settings?.autoOpenResultPage) return undefined
    const frame = window.requestAnimationFrame(() => {
      const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
      document.getElementById('processing-execution-result')?.scrollIntoView({
        behavior: reduceMotion ? 'auto' : 'smooth',
        block: 'start',
      })
    })
    return () => window.cancelAnimationFrame(frame)
  }, [execution?.executionStatus, settings?.autoOpenResultPage])

  const download = async (fileId: string, fileName: string) => {
    if (downloading) return
    const controller = new AbortController()
    downloadControllerRef.current?.abort()
    downloadControllerRef.current = controller
    setDownloading(true)
    setDownloadError(null)
    try {
      await downloadAudioFile(fileId, fileName, controller.signal)
    } catch (error) {
      if (!(error instanceof DOMException && error.name === 'AbortError')) {
        setDownloadError(error instanceof Error ? error.message : '文件下载失败，请稍后重试。')
      }
    } finally {
      if (downloadControllerRef.current === controller) {
        downloadControllerRef.current = null
        if (!controller.signal.aborted) setDownloading(false)
      }
    }
  }

  if (!validTaskId) {
    return (
      <PageContainer>
        <PageTitle
          eyebrow="AUDIO PROCESSING"
          title="音频处理任务"
        />
        <Alert
          type="error"
          showIcon
          message="任务 ID 无效"
          description="URL 中缺少有效的 taskId，请返回分析任务列表重新选择。"
          action={<Link to="/analysis/tasks"><Button>返回分析任务</Button></Link>}
        />
      </PageContainer>
    )
  }

  const backPath = `/analysis/tasks/${encodeURIComponent(validTaskId)}/processing-plan`
  const cancellable = execution?.executionStatus === 'PENDING'
    || execution?.executionStatus === 'QUEUED'
    || execution?.executionStatus === 'PROCESSING'

  const cancelExecution = () => {
    if (!cancellable || state.cancelling) return
    modal.confirm({
      title: '取消处理任务？',
      content: execution?.executionStatus === 'PROCESSING'
        ? '正在运行的 FFmpeg 进程将被终止，临时文件和未提交结果会被清理，原始音频不会受到影响。'
        : '任务尚未开始执行，可以安全取消；原始音频不会受到影响。',
      okText: '确认取消',
      cancelText: '继续等待',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await state.cancel()
          void message.success('处理任务已取消')
        } catch {
          void message.error('取消失败，任务可能已经开始处理，请刷新状态')
          throw new Error('cancel failed')
        }
      },
    })
  }

  return (
    <PageContainer>
      <PageTitle
        eyebrow="AUDIO PROCESSING"
        title="音频处理任务"
        description="跟踪已确认步骤的真实执行进度；生成的结果不会覆盖原始音频。"
        actions={(
          <>
            <Link to={backPath}><Button className="processing-execution-page-action" icon={<ArrowLeftOutlined />}>返回处理方案</Button></Link>
            <Button
              className="processing-execution-page-action"
              icon={<ReloadOutlined />}
              loading={state.refreshing}
              disabled={state.loading || state.refreshing}
              onClick={state.refresh}
            >
              刷新状态
            </Button>
            {cancellable && (
              <Button danger loading={state.cancelling} onClick={cancelExecution}>取消任务</Button>
            )}
          </>
        )}
      />

      {state.loading && !execution && <ProcessingExecutionSkeleton />}

      {!state.loading && state.error && !execution && (
        <Alert
          className="processing-execution-top-alert"
          type="error"
          showIcon
          message="处理任务加载失败"
          description={state.error}
          action={<Button onClick={state.refresh}>重新加载</Button>}
        />
      )}

      {execution && (
        <div className="processing-execution-content">
          {state.refreshing && (
            <div className="processing-execution-refreshing" role="status">正在获取最新处理状态…</div>
          )}
          {state.error && (
            <Alert
              type="warning"
              showIcon
              message="本次状态刷新未成功"
              description="当前仍显示上一次成功加载的任务数据，你可以稍后再次刷新。"
              action={<Button onClick={state.refresh}>再次刷新</Button>}
            />
          )}
          {sourceFile.error && (
            <Alert type="warning" showIcon message="原始文件名称暂时无法加载，任务进度不受影响。" />
          )}
          {downloadError && (
            <Alert
              type="error"
              showIcon
              closable
              message="文件下载失败"
              description={downloadError}
              onClose={() => setDownloadError(null)}
            />
          )}

          <ExecutionSummary execution={execution} fileName={sourceFile.data?.originalName} />
          <ExecutionProgress execution={execution} />

          {(execution.executionStatus === 'FAILED' || execution.executionStatus === 'DEAD_LETTER') && (
            <ExecutionFailure execution={execution} retrying={state.retrying} onRetry={state.retry} />
          )}
          {execution.executionStatus === 'CANCELLED' && (
            <Alert
              type="info"
              showIcon
              message="处理任务已取消"
              description="任务已停止，原始音频保持不变。"
            />
          )}

          <ExecutionStepList steps={execution.steps} />

          {execution.executionStatus === 'SUCCESS' && execution.resultFileId && (
            <ExecutionResult
              execution={execution}
              sourceFile={sourceFile.data}
              resultFile={resultFile.data}
              resultLoading={resultFile.loading}
              resultError={resultFile.error}
              downloading={downloading}
              onRefreshResult={resultFile.refresh}
              onDownload={(fileId, fileName) => { void download(fileId, fileName) }}
            />
          )}
          {execution.executionStatus === 'SUCCESS' && !execution.resultFileId && (
            <Alert
              type="warning"
              showIcon
              message="处理已完成，结果文件仍在准备"
              description="请稍后手动刷新任务状态。原始音频不会受到影响。"
            />
          )}
        </div>
      )}
    </PageContainer>
  )
}
