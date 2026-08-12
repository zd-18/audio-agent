import { BulbOutlined } from '@ant-design/icons'
import { Button, Modal, Tooltip } from 'antd'
import type { ButtonProps } from 'antd'
import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { generateProcessingPlan, getProcessingPlan } from '../../api/processingPlan'
import { ApiError } from '../../api/http'
import type { AnalysisTaskStatus } from '../../types/api'

const PROCESSING_PLAN_NOT_FOUND_CODE = 40209

type Availability = 'idle' | 'checking' | 'exists' | 'missing' | 'unknown'

interface ProcessingPlanAccessButtonProps {
  taskId: string
  status: AnalysisTaskStatus
  type?: ButtonProps['type']
}

function generationErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    if (error.code === 40210) return '分析结果尚未准备完成，暂时无法生成处理方案。'
    if (error.code === 40101) return '未找到对应分析任务。'
    return error.message || '处理方案生成失败，请稍后重试。'
  }
  return error instanceof Error ? error.message : '处理方案生成失败，请稍后重试。'
}

export default function ProcessingPlanAccessButton({
  taskId,
  status,
  type = 'default',
}: ProcessingPlanAccessButtonProps) {
  const navigate = useNavigate()
  const [modal, modalContext] = Modal.useModal()
  const [availability, setAvailability] = useState<Availability>('idle')
  const [generating, setGenerating] = useState(false)
  const generateControllerRef = useRef<AbortController | null>(null)
  const generationDialogOpenRef = useRef(false)
  const generationLockedRef = useRef(false)

  useEffect(() => {
    if (status !== 'SUCCESS') {
      setAvailability('idle')
      return undefined
    }

    const controller = new AbortController()
    setAvailability('checking')
    getProcessingPlan(taskId, controller.signal)
      .then(() => {
        if (!controller.signal.aborted) setAvailability('exists')
      })
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') return
        if (!controller.signal.aborted) {
          setAvailability(error instanceof ApiError && error.code === PROCESSING_PLAN_NOT_FOUND_CODE
            ? 'missing'
            : 'unknown')
        }
      })
    return () => controller.abort()
  }, [status, taskId])

  useEffect(() => () => generateControllerRef.current?.abort(), [])

  const planPath = `/analysis/tasks/${encodeURIComponent(taskId)}/processing-plan`
  const disabledReason = status === 'FAILED'
    ? '本次分析未完成，暂时无法生成处理方案。'
    : status === 'PENDING' || status === 'PROCESSING'
      ? '分析完成后才能生成处理方案。'
      : undefined

  const generate = () => {
    if (generationDialogOpenRef.current || generationLockedRef.current) return
    generationDialogOpenRef.current = true
    modal.confirm({
      title: '生成处理方案',
      content: '系统将根据当前分析报告生成建议步骤，不会直接修改原始音频。',
      okText: '确认生成',
      cancelText: '取消',
      centered: true,
      afterClose: () => {
        generationDialogOpenRef.current = false
      },
      onOk: async () => {
        if (generationLockedRef.current) return
        generationLockedRef.current = true
        const controller = new AbortController()
        generateControllerRef.current?.abort()
        generateControllerRef.current = controller
        setGenerating(true)
        try {
          await generateProcessingPlan(taskId, controller.signal)
          if (!controller.signal.aborted) navigate(planPath)
        } catch (error) {
          if (!(error instanceof DOMException && error.name === 'AbortError')) {
            modal.error({
              title: '处理方案生成失败',
              content: generationErrorMessage(error),
              centered: true,
            })
          }
        } finally {
          if (generateControllerRef.current === controller) {
            generateControllerRef.current = null
            if (!controller.signal.aborted) setGenerating(false)
          }
          generationLockedRef.current = false
        }
      },
    })
  }

  const openOrGenerate = () => {
    if (availability === 'missing') generate()
    else navigate(planPath)
  }

  const label = availability === 'missing'
    ? '生成处理方案'
    : availability === 'exists'
      ? '查看处理方案'
      : availability === 'checking'
        ? '检查处理方案'
        : '打开处理方案'
  const lookupHint = availability === 'unknown'
    ? '暂时无法确认方案状态，可进入处理方案页重试。报告内容不受影响。'
    : disabledReason

  const button = (
    <Button
      type={type}
      icon={<BulbOutlined />}
      disabled={Boolean(disabledReason)}
      loading={availability === 'checking' || generating}
      onClick={openOrGenerate}
    >
      {label}
    </Button>
  )

  return (
    <>
      {modalContext}
      {lookupHint ? <Tooltip title={lookupHint}><span>{button}</span></Tooltip> : button}
    </>
  )
}
