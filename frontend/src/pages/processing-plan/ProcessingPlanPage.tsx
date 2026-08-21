import {
  ArrowLeftOutlined,
  CheckCircleOutlined,
} from '@ant-design/icons'
import { Alert, Button, Modal, Tooltip } from 'antd'
import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { downloadAudioFile } from '../../api/audioFiles'
import { ApiError, isValidResourceId } from '../../api/http'
import { generateProcessingPlan, getProcessingPlan } from '../../api/processingPlan'
import { createProcessingExecution } from '../../api/processingExecution'
import ReportAudioPlayer from '../../components/audio/ReportAudioPlayer'
import EmptyState from '../../components/workbench/EmptyState'
import PageContainer from '../../components/workbench/PageContainer'
import PageTitle from '../../components/workbench/PageTitle'
import { useAudioPlayback } from '../../hooks/useAudioPlayback'
import { useProcessingConfirmation } from '../../hooks/useProcessingConfirmation'
import { useProcessingPlan } from '../../hooks/useProcessingPlan'
import { useUserSettings } from '../../settings/UserSettingsContext'
import type { ProcessingStep } from '../../types/processingPlan'
import { formatDuration } from '../../utils/audioTime'
import { getProcessingConfirmationErrorMessage } from '../../utils/processingConfirmationDisplay'
import {
  getProcessingExecutionErrorMessage,
  PROCESSING_EXECUTION_ALREADY_EXISTS_CODE,
} from '../../utils/processingExecutionDisplay'
import { getProcessingPlanErrorMessage, hasSegmentRange, isWholeAudioOperation } from '../../utils/processingPlanDisplay'
import '../analysis/analysis-report.css'
import './processing-plan.css'
import PlanSummary from './components/PlanSummary'
import ProcessingExecutionEntry from '../processing-execution/components/ProcessingExecutionEntry'
import ProcessingPlanConfirmationModal from './components/ProcessingPlanConfirmationModal'
import ProcessingPlanErrorState from './components/ProcessingPlanErrorState'
import ProcessingPlanSkeleton from './components/ProcessingPlanSkeleton'
import ProcessingStepDetail from './components/ProcessingStepDetail'
import ProcessingStepList from './components/ProcessingStepList'

function compactId(value: string) {
  return value.length > 18 ? `${value.slice(0, 8)}…${value.slice(-6)}` : value
}

function scrollToPlayer() {
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  document.getElementById('report-audio-player')?.scrollIntoView({
    behavior: reduceMotion ? 'auto' : 'smooth',
    block: 'center',
  })
}

export default function ProcessingPlanPage() {
  const { taskId } = useParams()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const validTaskId = isValidResourceId(taskId) ? taskId : undefined
  const planSource = searchParams.get('source') === 'processing' ? 'processing' : 'diagnosis'
  const sourceAudioFileId = isValidResourceId(searchParams.get('audioFileId'))
    ? searchParams.get('audioFileId')!
    : undefined
  const backPath = planSource === 'processing' && sourceAudioFileId
    ? `/audio/files/${encodeURIComponent(sourceAudioFileId)}/agent`
    : validTaskId
      ? `/analysis/tasks/${encodeURIComponent(validTaskId)}/report`
      : '/analysis/tasks'
  const backLabel = planSource === 'processing' ? '返回智能处理' : '返回诊断结果'
  const { plan, error, loading, refreshing, reload, applyPlan } = useProcessingPlan(validTaskId)
  const { settings } = useUserSettings()
  const currentPlan = plan?.taskId === validTaskId ? plan : null
  const player = useAudioPlayback(currentPlan?.audioFileId)
  const confirmationState = useProcessingConfirmation(
    validTaskId,
    currentPlan
      ? `${currentPlan.planId}:${currentPlan.generatedAt || currentPlan.planVersion}`
      : undefined,
  )
  const [modal, modalContext] = Modal.useModal()
  const [selectedStepId, setSelectedStepId] = useState<string>()
  const [generating, setGenerating] = useState(false)
  const [generationError, setGenerationError] = useState<string | null>(null)
  const [downloading, setDownloading] = useState(false)
  const [downloadError, setDownloadError] = useState<string | null>(null)
  const [draftInvalidated, setDraftInvalidated] = useState(false)
  const [confirmationDialogOpen, setConfirmationDialogOpen] = useState(false)
  const [starting, setStarting] = useState(false)
  const [startError, setStartError] = useState<string | null>(null)
  const generationControllerRef = useRef<AbortController | null>(null)
  const downloadControllerRef = useRef<AbortController | null>(null)
  const generationDialogOpenRef = useRef(false)
  const generationLockedRef = useRef(false)

  useEffect(() => () => {
    generationControllerRef.current?.abort()
    downloadControllerRef.current?.abort()
  }, [])

  useEffect(() => {
    setSelectedStepId(settings?.requireStepConfirmation
      ? currentPlan?.steps[0]?.stepId
      : undefined)
    setGenerationError(null)
    setDownloadError(null)
    setConfirmationDialogOpen(false)
    setStartError(null)
  }, [currentPlan?.planId, currentPlan?.generatedAt, settings?.requireStepConfirmation])

  const orderedSteps = useMemo(() => (
    currentPlan ? [...currentPlan.steps].sort((first, second) => first.stepOrder - second.stepOrder) : []
  ), [currentPlan])
  const selectedStep = orderedSteps.find((step) => step.stepId === selectedStepId)
    || (settings?.requireStepConfirmation ? orderedSteps[0] : undefined)

  useEffect(() => {
    if (selectedStep && selectedStep.stepId !== selectedStepId) {
      setSelectedStepId(selectedStep.stepId)
    }
  }, [selectedStep, selectedStepId])

  const requestGeneration = (regenerate: boolean) => {
    if (!validTaskId || generationDialogOpenRef.current || generationLockedRef.current) return
    generationDialogOpenRef.current = true
    const hasDraft = confirmationState.confirmation?.confirmationStatus === 'DRAFT'
    modal.confirm({
      title: regenerate ? '重新生成处理方案' : '生成处理方案',
      content: regenerate
        ? hasDraft
          ? '重新生成方案会使当前确认草稿失效，已填写的决定不会自动迁移。系统仍不会修改原始音频。'
          : '重新生成将更新当前建议步骤，但不会修改原始音频。'
        : '系统将根据当前分析报告生成建议步骤，不会直接修改原始音频。',
      okText: regenerate ? '确认重新生成' : '确认生成',
      cancelText: '取消',
      centered: true,
      afterClose: () => {
        generationDialogOpenRef.current = false
      },
      onOk: async () => {
        if (generationLockedRef.current) return
        generationLockedRef.current = true
        const controller = new AbortController()
        generationControllerRef.current?.abort()
        generationControllerRef.current = controller
        setGenerating(true)
        setGenerationError(null)
        try {
          const nextPlan = await generateProcessingPlan(validTaskId, controller.signal)
          if (!controller.signal.aborted) {
            if (hasDraft) setDraftInvalidated(true)
            applyPlan(nextPlan)
          }
        } catch (requestError) {
          if (!(requestError instanceof DOMException && requestError.name === 'AbortError')) {
            setGenerationError(getProcessingPlanErrorMessage(requestError, '处理方案生成失败，请稍后重试。'))
          }
        } finally {
          if (generationControllerRef.current === controller) {
            generationControllerRef.current = null
            if (!controller.signal.aborted) setGenerating(false)
          }
          generationLockedRef.current = false
        }
      },
    })
  }

  const download = async () => {
    if (!currentPlan || downloading) return
    const controller = new AbortController()
    downloadControllerRef.current?.abort()
    downloadControllerRef.current = controller
    setDownloading(true)
    setDownloadError(null)
    try {
      await downloadAudioFile(
        currentPlan.audioFileId,
        player.playback?.fileName || `audio-${currentPlan.audioFileId}`,
        controller.signal,
      )
    } catch (requestError) {
      if (!(requestError instanceof DOMException && requestError.name === 'AbortError')) {
        setDownloadError(requestError instanceof Error ? requestError.message : '文件下载失败')
      }
    } finally {
      if (downloadControllerRef.current === controller) {
        downloadControllerRef.current = null
        if (!controller.signal.aborted) setDownloading(false)
      }
    }
  }

  const locateStep = (step: ProcessingStep, play: boolean) => {
    setSelectedStepId(step.stepId)
    scrollToPlayer()
    if (hasSegmentRange(step)) {
      const contextSeconds = play ? (settings?.issueContextSeconds ?? 0) : 0
      const startSeconds = Math.max(0, step.startMs / 1000 - contextSeconds)
      const rawEndSeconds = step.endMs / 1000 + contextSeconds
      const endSeconds = player.durationSeconds > 0
        ? Math.min(player.durationSeconds, rawEndSeconds)
        : rawEndSeconds
      void player.seekTo(startSeconds, {
        play,
        endSeconds: play ? endSeconds : undefined,
      })
    } else if (play && isWholeAudioOperation(step.operationType)) {
      void player.seekTo(0, { play: true })
    }
  }

  const createConfirmation = async () => {
    const created = await confirmationState.create()
    if (created?.confirmationStatus === 'DRAFT') setDraftInvalidated(false)
    return created
  }

  const createLatestConfirmation = async (status: 'STALE' | 'CANCELLED') => {
    if (!validTaskId || generating) return null
    const controller = new AbortController()
    generationControllerRef.current?.abort()
    generationControllerRef.current = controller
    setGenerating(true)
    setGenerationError(null)
    try {
      const latestPlan = status === 'CANCELLED'
        ? await generateProcessingPlan(validTaskId, controller.signal)
        : await getProcessingPlan(validTaskId, controller.signal)
      if (controller.signal.aborted) return null
      applyPlan(latestPlan)
      const created = await confirmationState.create()
      if (created?.confirmationStatus === 'DRAFT') setDraftInvalidated(false)
      return created
    } catch (requestError) {
      if (!(requestError instanceof DOMException && requestError.name === 'AbortError')) {
        setGenerationError(getProcessingPlanErrorMessage(requestError, '处理方案生成失败，请稍后重试。'))
      }
      throw requestError
    } finally {
      if (generationControllerRef.current === controller) {
        generationControllerRef.current = null
        if (!controller.signal.aborted) setGenerating(false)
      }
    }
  }

  const startProcessing = async (note: string) => {
    if (!currentPlan || starting) return
    setStarting(true)
    setStartError(null)
    try {
      let target = confirmationState.confirmation
      if (!target) {
        target = await createConfirmation()
      } else if (target.confirmationStatus === 'STALE' || target.confirmationStatus === 'CANCELLED') {
        target = await createLatestConfirmation(target.confirmationStatus)
      }
      if (!target) throw new Error('无法创建处理方案确认单')

      const confirmed = target.confirmationStatus === 'CONFIRMED'
        ? target
        : await confirmationState.acceptAllAndConfirm(target, note)
      if (!confirmed) throw new Error('处理方案确认失败')

      try {
        await createProcessingExecution(confirmed.confirmationId)
      } catch (executionError) {
        if (!(executionError instanceof ApiError
          && executionError.code === PROCESSING_EXECUTION_ALREADY_EXISTS_CODE)) {
          throw executionError
        }
      }
      setConfirmationDialogOpen(false)
      navigate(`/analysis/tasks/${encodeURIComponent(confirmed.taskId)}/processing-execution`)
    } catch (actionError) {
      setStartError(actionError instanceof ApiError
        ? getProcessingConfirmationErrorMessage(actionError)
        : getProcessingExecutionErrorMessage(actionError))
    } finally {
      setStarting(false)
    }
  }

  if (!validTaskId) {
    return (
      <PageContainer>
        <PageTitle
          eyebrow="PROCESSING PLAN"
          title="处理方案"
          description="统一展示、调整并确认处理步骤，当前不会自动修改原始音频。"
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

  return (
    <PageContainer>
      {modalContext}
      <PageTitle
        eyebrow="PROCESSING PLAN"
        title="处理方案"
        description="确认后将直接开始处理。"
        actions={(
          <>
            <Link to={backPath}><Button icon={<ArrowLeftOutlined />}>{backLabel}</Button></Link>
            {currentPlan
              && currentPlan.steps.length > 0
              && confirmationState.confirmation?.confirmationStatus !== 'CONFIRMED' && (
              <Button
                type="primary"
                icon={<CheckCircleOutlined />}
                loading={confirmationState.loading || confirmationState.creating || starting}
                disabled={currentPlan.planStatus === 'INVALID' || refreshing || starting}
                onClick={() => {
                  setStartError(null)
                  setConfirmationDialogOpen(true)
                }}
              >
                确认处理方案
              </Button>
            )}
          </>
        )}
      />

      {loading && !currentPlan && <ProcessingPlanSkeleton />}
      {generationError && !currentPlan && (
        <Alert
          className="processing-plan-top-alert"
          type="error"
          showIcon
          closable
          message="处理方案生成失败"
          description={generationError}
          onClose={() => setGenerationError(null)}
        />
      )}
      {!loading && error && !currentPlan && (
        <ProcessingPlanErrorState
          taskId={validTaskId}
          error={error}
          generating={generating}
          onRetry={reload}
          onGenerate={() => requestGeneration(false)}
          allowGenerate={planSource === 'diagnosis'}
          backPath={backPath}
          backLabel={backLabel}
        />
      )}

      {currentPlan && (
        <div className="processing-plan-content">
          <ProcessingPlanConfirmationModal
            open={confirmationDialogOpen}
            audioName={player.playback?.fileName || `音频文件 ${compactId(currentPlan.audioFileId)}`}
            plan={currentPlan}
            starting={starting}
            error={startError}
            onCancel={() => setConfirmationDialogOpen(false)}
            onStart={startProcessing}
          />
          <nav className="processing-plan-flow" aria-label="处理流程">
            <ol>
              {[
                planSource === 'diagnosis' ? '诊断完成' : '方案已生成',
                '方案确认',
                '音频处理',
                '处理完成',
              ].map((label, index) => {
                const confirmed = confirmationState.confirmation?.confirmationStatus === 'CONFIRMED'
                const currentIndex = confirmed ? 2 : 1
                const state = index < currentIndex ? 'is-complete' : index === currentIndex ? 'is-current' : 'is-upcoming'
                return (
                  <li key={label} className={state} aria-current={index === currentIndex ? 'step' : undefined}>
                    <span>{index + 1}</span>
                    <strong>{label}</strong>
                  </li>
                )
              })}
            </ol>
          </nav>

          <header className="processing-plan-meta processing-plan-reveal">
            <div className="processing-plan-meta__file">
              <span>音频名称</span>
              <Tooltip title={player.playback?.fileName || undefined}>
                <strong>
                  {player.playback?.fileName
                    || (player.loading ? '正在获取音频文件信息' : `音频文件 ${compactId(currentPlan.audioFileId)}`)}
                </strong>
              </Tooltip>
            </div>
            <dl>
              <div>
                <dt>时长</dt>
                <dd>{formatDuration(player.durationSeconds > 0 ? player.durationSeconds * 1000 : null)}</dd>
              </div>
            </dl>
          </header>

          {refreshing && <Alert type="info" showIcon message="正在刷新处理方案…" />}
          {error && (
            <Alert
              type="warning"
              showIcon
              message="本次刷新未成功，当前仍显示上一次加载的处理方案。"
              action={<Button onClick={reload}>再次刷新</Button>}
            />
          )}
          {generationError && (
            <Alert
              type="error"
              showIcon
              closable
              message="处理方案生成失败"
              description={generationError}
              onClose={() => setGenerationError(null)}
            />
          )}
          {draftInvalidated && (
            <Alert
              type="warning"
              showIcon
              closable
              message="处理方案已更新，原确认草稿已经失效"
              description="旧草稿中的决定和参数不会自动迁移；请基于当前最新方案创建新的确认草稿。"
              onClose={() => setDraftInvalidated(false)}
            />
          )}
          {confirmationState.error && (
            <Alert
              type="warning"
              showIcon
              message="确认状态暂时无法加载"
              description={getProcessingConfirmationErrorMessage(confirmationState.error)}
              action={<Button loading={confirmationState.refreshing} onClick={confirmationState.reload}>重试</Button>}
            />
          )}
          {downloadError && (
            <Alert
              type="error"
              showIcon
              closable
              message="音频下载失败"
              description={downloadError}
              onClose={() => setDownloadError(null)}
            />
          )}
          {currentPlan.planStatus === 'INVALID' && (
            <Alert
              type="error"
              showIcon
              message="当前处理方案不可用"
              description="可以返回分析报告核对检测结果，或重新生成处理方案。"
            />
          )}

          <div className="processing-plan-hero-grid">
            <PlanSummary plan={currentPlan} />
            <ReportAudioPlayer
              player={player}
              fallbackFileName={`音频文件 ${compactId(currentPlan.audioFileId)}`}
              downloading={downloading}
              onDownload={() => { void download() }}
            />
          </div>

          {orderedSteps.length === 0 ? (
            <section className="processing-plan-empty processing-plan-reveal">
              <EmptyState
                title="当前未发现需要优先处理的问题"
                description="当前方案没有建议步骤，你可以返回分析报告核对结果，或重新生成处理方案。"
                action={(
                  <div className="processing-plan-empty__actions">
                    <Link to={backPath}><Button>{backLabel}</Button></Link>
                    {planSource === 'diagnosis' && <Button type="primary" loading={generating} onClick={() => requestGeneration(true)}>重新生成处理方案</Button>}
                  </div>
                )}
              />
            </section>
          ) : (
            <section className="processing-plan-steps processing-plan-reveal" aria-labelledby="processing-plan-steps-title">
              <div className="processing-plan-section-heading processing-plan-steps__heading">
                <div>
                  <h2 id="processing-plan-steps-title">建议处理步骤</h2>
                </div>
              </div>

              <div className="processing-plan-workspace">
                <ProcessingStepList
                  steps={orderedSteps}
                  selectedStepId={selectedStep?.stepId}
                  onSelect={(step) => setSelectedStepId(step.stepId)}
                />
                {selectedStep && (
                  <ProcessingStepDetail
                    key={selectedStep.stepId}
                    taskId={validTaskId}
                    step={selectedStep}
                    onLocate={(step) => locateStep(step, false)}
                    onPreview={(step) => locateStep(step, true)}
                  />
                )}
              </div>
            </section>
          )}

          {confirmationState.confirmation?.confirmationStatus === 'CONFIRMED' && (
            <ProcessingExecutionEntry
              confirmation={confirmationState.confirmation}
              onRegenerate={() => requestGeneration(true)}
            />
          )}
        </div>
      )}
    </PageContainer>
  )
}
