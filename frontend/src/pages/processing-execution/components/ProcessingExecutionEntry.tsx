import {
  ArrowRightOutlined,
  CheckCircleOutlined,
  RocketOutlined,
} from '@ant-design/icons'
import { Alert, Button, Modal, Skeleton } from 'antd'
import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { createProcessingExecution } from '../../../api/processingExecution'
import { ApiError } from '../../../api/http'
import { useProcessingExecution } from '../../../hooks/useProcessingExecution'
import type { ProcessingConfirmation } from '../../../types/processingConfirmation'
import {
  getProcessingExecutionErrorMessage,
  PROCESSING_EXECUTION_ALREADY_EXISTS_CODE,
} from '../../../utils/processingExecutionDisplay'
import '../processing-execution.css'

interface ProcessingExecutionEntryProps {
  confirmation: ProcessingConfirmation
}

export default function ProcessingExecutionEntry({ confirmation }: ProcessingExecutionEntryProps) {
  const navigate = useNavigate()
  const [modal, modalContext] = Modal.useModal()
  const [creating, setCreating] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)
  const createLockedRef = useRef(false)
  const dialogOpenRef = useRef(false)
  const mountedRef = useRef(true)
  const createControllerRef = useRef<AbortController | null>(null)
  const state = useProcessingExecution({
    taskId: confirmation.taskId,
    autoPoll: true,
    notFoundIsEmpty: true,
  })
  const executionPath = `/analysis/tasks/${encodeURIComponent(confirmation.taskId)}/processing-execution`

  useEffect(() => {
    mountedRef.current = true
    return () => {
      mountedRef.current = false
      createControllerRef.current?.abort()
    }
  }, [])

  if (confirmation.confirmationStatus !== 'CONFIRMED') return null

  if (confirmation.acceptedStepCount === 0) {
    return (
      <section className="processing-execution-entry is-empty">
        <CheckCircleOutlined aria-hidden="true" />
        <div>
          <span>PROCESSING</span>
          <h2>无需创建处理任务</h2>
          <p>未接受任何处理建议，原始音频将保持不变。</p>
        </div>
      </section>
    )
  }

  const openExecution = () => navigate(executionPath)

  const create = async () => {
    if (createLockedRef.current) return
    createLockedRef.current = true
    const controller = new AbortController()
    createControllerRef.current = controller
    setCreating(true)
    setActionError(null)
    try {
      await createProcessingExecution(confirmation.confirmationId, controller.signal)
      navigate(executionPath)
    } catch (error) {
      if (error instanceof ApiError && error.code === PROCESSING_EXECUTION_ALREADY_EXISTS_CODE) {
        navigate(executionPath)
        return
      }
      if (controller.signal.aborted) return
      if (mountedRef.current) setActionError(getProcessingExecutionErrorMessage(error))
      throw error
    } finally {
      createLockedRef.current = false
      if (createControllerRef.current === controller) createControllerRef.current = null
      if (mountedRef.current && !controller.signal.aborted) setCreating(false)
    }
  }

  const requestCreate = () => {
    if (dialogOpenRef.current || createLockedRef.current) return
    dialogOpenRef.current = true
    modal.confirm({
      title: '开始处理',
      content: '系统将按照已确认的处理步骤生成新的结果音频。原始音频会被完整保留。',
      okText: '确认开始处理',
      cancelText: '返回',
      centered: true,
      afterClose: () => { dialogOpenRef.current = false },
      onOk: create,
    })
  }

  if (state.loading && !state.execution && !state.notFound) {
    return (
      <section className="processing-execution-entry is-loading" aria-label="正在查询处理任务">
        <Skeleton active paragraph={{ rows: 1 }} />
      </section>
    )
  }

  return (
    <section className={`processing-execution-entry ${state.execution ? 'has-execution' : ''}`}>
      {modalContext}
      <div className="processing-execution-entry__icon" aria-hidden="true">
        {state.execution ? <CheckCircleOutlined /> : <RocketOutlined />}
      </div>
      <div className="processing-execution-entry__copy">
        <span>PROCESSING</span>
        <h2>{state.execution ? '音频处理任务已创建' : '开始处理已确认方案'}</h2>
        <p>
          {state.execution
            ? '可以继续查看整体进度、当前阶段和每个处理步骤。'
            : '系统将根据已确认步骤创建新的修复结果，不会覆盖原始音频。'}
        </p>
      </div>
      <div className="processing-execution-entry__actions">
        {state.execution ? (
          <Button type="primary" size="large" icon={<ArrowRightOutlined />} onClick={openExecution}>
            {state.execution.executionStatus === 'SUCCESS' ? '查看处理结果' : '查看处理进度'}
          </Button>
        ) : (
          <Button
            type="primary"
            size="large"
            icon={<RocketOutlined />}
            loading={creating}
            disabled={creating || (!state.notFound && Boolean(state.error))}
            onClick={requestCreate}
          >
            开始处理
          </Button>
        )}
        {state.error && !state.execution && (
          <Alert
            type="warning"
            showIcon
            message="处理任务状态暂时无法确认"
            description={state.error}
            action={<Button onClick={state.refresh}>重新查询</Button>}
          />
        )}
        {actionError && <Alert type="error" showIcon message={actionError} />}
      </div>
    </section>
  )
}
