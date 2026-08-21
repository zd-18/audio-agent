import { BulbOutlined } from '@ant-design/icons'
import { Button, Modal, Tooltip } from 'antd'
import type { ButtonProps } from 'antd'
import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { generateProcessingPlan, getProcessingPlan } from '../../api/processingPlan'
import { ApiError } from '../../api/http'
import type { AnalysisTaskStatus } from '../../types/api'
import { getProcessingPlanErrorMessage } from '../../utils/processingPlanDisplay'

const PROCESSING_PLAN_NOT_FOUND_CODE = 40209

type Availability = 'idle' | 'checking' | 'exists' | 'missing' | 'unknown'

interface ProcessingPlanAccessButtonProps {
  taskId: string
  status: AnalysisTaskStatus
  type?: ButtonProps['type']
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

  const planPath = `/analysis/tasks/${encodeURIComponent(taskId)}/processing-plan?source=diagnosis`
  const disabledReason = status === 'FAILED'
    ? '本次分析未完成，暂时无法生成处理方案。'
    : status === 'PENDING' || status === 'PROCESSING'
      ? '分析完成后才能生成处理方案。'
      : undefined

  const generate = () => {
    if (generationDialogOpenRef.current || generationLockedRef.current) return
    generationDialogOpenRef.current = true
    modal.confirm({
      title: '按推荐方案处理',
      content: '系统将把本次智能诊断的推荐步骤带入统一处理方案，进入确认页后仍可调整参数，不会直接修改原始音频。',
      okText: '生成并查看方案',
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
              content: getProcessingPlanErrorMessage(error, '处理方案生成失败，请稍后重试。'),
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
      按推荐方案处理
    </Button>
  )

  return (
    <>
      {modalContext}
      {lookupHint ? <Tooltip title={lookupHint}><span>{button}</span></Tooltip> : button}
    </>
  )
}
