import { useCallback, useEffect, useRef, useState } from 'react'
import { App } from 'antd'
import {
  getProcessingExecution,
  getProcessingExecutionByTaskId,
  retryProcessingExecution,
} from '../api/processingExecution'
import { ApiError } from '../api/http'
import type { ProcessingExecution } from '../types/processingExecution'
import { useUserSettings } from '../settings/UserSettingsContext'
import {
  getProcessingExecutionErrorMessage,
  isActiveExecutionStatus,
  PROCESSING_EXECUTION_NOT_FOUND_CODE,
} from '../utils/processingExecutionDisplay'

const FAST_POLL_MS = 1_800
const SLOW_POLL_MS = 4_000
const HIDDEN_POLL_MS = 12_000
const MAX_CONSECUTIVE_FAILURES = 5

interface UseProcessingExecutionOptions {
  executionId?: string
  taskId?: string
  autoPoll?: boolean
  notFoundIsEmpty?: boolean
}

function isAbortError(error: unknown) {
  return error instanceof DOMException && error.name === 'AbortError'
}

export function useProcessingExecution({
  executionId,
  taskId,
  autoPoll = true,
  notFoundIsEmpty = false,
}: UseProcessingExecutionOptions) {
  const { message } = App.useApp()
  const { settings } = useUserSettings()
  const resourceKey = executionId ? `execution:${executionId}` : taskId ? `task:${taskId}` : undefined
  const [execution, setExecution] = useState<ProcessingExecution | null>(null)
  const [loading, setLoading] = useState(Boolean(resourceKey))
  const [refreshing, setRefreshing] = useState(false)
  const [retrying, setRetrying] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notFound, setNotFound] = useState(false)
  const [version, setVersion] = useState(0)
  const retryLockedRef = useRef(false)
  const retryControllerRef = useRef<AbortController | null>(null)
  const notifiedTerminalRef = useRef<Set<string>>(new Set())
  const notifyOnTaskCompleteRef = useRef(false)
  notifyOnTaskCompleteRef.current = Boolean(settings?.notifyOnTaskComplete)

  const refresh = useCallback(() => setVersion((value) => value + 1), [])

  useEffect(() => {
    setExecution(null)
    setError(null)
    setNotFound(false)
  }, [resourceKey])

  useEffect(() => {
    if (!resourceKey || (!executionId && !taskId)) {
      setLoading(false)
      setRefreshing(false)
      return undefined
    }

    let active = true
    let requestInFlight = false
    let timer: number | undefined
    let requestController: AbortController | null = null
    let successfulPolls = 0
    let consecutiveFailures = 0
    let lastStatus: ProcessingExecution['executionStatus'] | undefined

    const clearTimer = () => {
      if (timer !== undefined) window.clearTimeout(timer)
      timer = undefined
    }

    const schedule = (delay: number) => {
      clearTimer()
      if (!active) return
      timer = window.setTimeout(() => { void load(false) }, delay)
    }

    const nextPollDelay = () => {
      if (document.visibilityState !== 'visible') return HIDDEN_POLL_MS
      return successfulPolls < 5 ? FAST_POLL_MS : SLOW_POLL_MS
    }

    const load = async (initial: boolean) => {
      if (!active || requestInFlight) return
      requestInFlight = true
      requestController = new AbortController()
      if (initial) setLoading(true)
      else setRefreshing(true)

      try {
        const nextExecution = executionId
          ? await getProcessingExecution(executionId, requestController.signal)
          : await getProcessingExecutionByTaskId(taskId!, requestController.signal)
        if (!active || requestController.signal.aborted) return
        if (lastStatus === 'PROCESSING'
          && (nextExecution.executionStatus === 'SUCCESS'
            || nextExecution.executionStatus === 'FAILED'
            || nextExecution.executionStatus === 'DEAD_LETTER')
          && notifyOnTaskCompleteRef.current) {
          const notificationKey = `${nextExecution.executionId}:${nextExecution.executionStatus}`
          if (!notifiedTerminalRef.current.has(notificationKey)) {
            notifiedTerminalRef.current.add(notificationKey)
            if (nextExecution.executionStatus === 'SUCCESS') message.success('音频处理任务已完成')
            else message.error('音频处理任务执行失败，请查看失败原因')
          }
        }
        lastStatus = nextExecution.executionStatus
        setExecution(nextExecution)
        setError(null)
        setNotFound(false)
        consecutiveFailures = 0
        successfulPolls += 1
        if (autoPoll && isActiveExecutionStatus(nextExecution.executionStatus)) {
          schedule(nextPollDelay())
        }
      } catch (requestError) {
        if (!active || isAbortError(requestError)) return
        const missing = notFoundIsEmpty
          && requestError instanceof ApiError
          && requestError.code === PROCESSING_EXECUTION_NOT_FOUND_CODE
        if (missing) {
          setNotFound(true)
          setError(null)
          return
        }
        setError(getProcessingExecutionErrorMessage(requestError))
        consecutiveFailures += 1
        if (autoPoll && consecutiveFailures < MAX_CONSECUTIVE_FAILURES) {
          schedule(Math.min(15_000, FAST_POLL_MS * 2 ** consecutiveFailures))
        }
      } finally {
        requestInFlight = false
        requestController = null
        if (active) {
          setLoading(false)
          setRefreshing(false)
        }
      }
    }

    const handleVisibilityChange = () => {
      if (!active) return
      if (document.visibilityState === 'visible') {
        clearTimer()
        void load(false)
      } else if (timer !== undefined) {
        schedule(HIDDEN_POLL_MS)
      }
    }

    document.addEventListener('visibilitychange', handleVisibilityChange)
    void load(true)

    return () => {
      active = false
      clearTimer()
      requestController?.abort()
      document.removeEventListener('visibilitychange', handleVisibilityChange)
    }
  }, [autoPoll, executionId, message, notFoundIsEmpty, resourceKey, taskId, version])

  useEffect(() => () => retryControllerRef.current?.abort(), [])

  const retry = useCallback(async () => {
    if (!execution || retryLockedRef.current) return null
    retryLockedRef.current = true
    const controller = new AbortController()
    retryControllerRef.current?.abort()
    retryControllerRef.current = controller
    setRetrying(true)
    setError(null)
    try {
      const nextExecution = await retryProcessingExecution(execution.executionId, controller.signal)
      if (!controller.signal.aborted) {
        setExecution(nextExecution)
        setVersion((value) => value + 1)
      }
      return nextExecution
    } catch (requestError) {
      if (!isAbortError(requestError)) setError(getProcessingExecutionErrorMessage(requestError))
      throw requestError
    } finally {
      if (retryControllerRef.current === controller) retryControllerRef.current = null
      retryLockedRef.current = false
      if (!controller.signal.aborted) setRetrying(false)
    }
  }, [execution])

  return {
    execution,
    loading,
    refreshing,
    retrying,
    error,
    notFound,
    refresh,
    retry,
  }
}
