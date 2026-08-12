import { ReloadOutlined } from '@ant-design/icons'
import { Alert, Button, Modal } from 'antd'
import type { ButtonProps } from 'antd'
import { useEffect, useRef, useState } from 'react'
import { retryAnalysisTask } from '../../api/analysisTasks'
import type { AnalysisTaskRecord } from '../../types/api'

interface ManualRetryButtonProps {
  taskId: string
  onRetried: (task: AnalysisTaskRecord) => void
  buttonType?: ButtonProps['type']
  size?: ButtonProps['size']
  label?: string
  open?: boolean
  hideTrigger?: boolean
  onOpenChange?: (open: boolean) => void
}

export default function ManualRetryButton({
  taskId,
  onRetried,
  buttonType,
  size,
  label = '人工重试',
  open: controlledOpen,
  hideTrigger = false,
  onOpenChange,
}: ManualRetryButtonProps) {
  const [internalOpen, setInternalOpen] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const controllerRef = useRef<AbortController | null>(null)
  const inFlightRef = useRef(false)
  const mountedRef = useRef(true)
  const open = controlledOpen ?? internalOpen

  const setOpen = (nextOpen: boolean) => {
    if (controlledOpen === undefined) setInternalOpen(nextOpen)
    onOpenChange?.(nextOpen)
  }

  useEffect(() => {
    mountedRef.current = true
    return () => {
      mountedRef.current = false
      controllerRef.current?.abort()
    }
  }, [])

  const confirm = async () => {
    if (inFlightRef.current) return
    inFlightRef.current = true
    const controller = new AbortController()
    controllerRef.current = controller
    setLoading(true)
    setError(null)
    try {
      const nextTask = await retryAnalysisTask(taskId, controller.signal)
      onRetried(nextTask)
      setOpen(false)
    } catch (requestError) {
      if (!(requestError instanceof DOMException && requestError.name === 'AbortError')) {
        if (mountedRef.current) setError(requestError instanceof Error ? requestError.message : '人工重试失败')
      }
    } finally {
      if (controllerRef.current === controller) controllerRef.current = null
      inFlightRef.current = false
      if (mountedRef.current) setLoading(false)
    }
  }

  return (
    <>
      {!hideTrigger && (
        <Button danger type={buttonType} size={size} icon={<ReloadOutlined />} onClick={() => { setError(null); setOpen(true) }}>{label}</Button>
      )}
      <Modal
        title="确认人工重试"
        open={open}
        okText="重新进入队列"
        cancelText="取消"
        okButtonProps={{ danger: true }}
        confirmLoading={loading}
        closable={!loading}
        maskClosable={!loading}
        keyboard={!loading}
        onOk={confirm}
        onCancel={() => { if (!loading) setOpen(false) }}
      >
        <p className="analysis-confirm-description">任务状态将重置为待处理，并重新进入分析流程。任务原有失败信息会被清理。</p>
        <p className="workbench-mono">taskId: {taskId}</p>
        {error && <Alert className="analysis-confirm-error" type="error" showIcon message="重试失败" description={error} />}
      </Modal>
    </>
  )
}
