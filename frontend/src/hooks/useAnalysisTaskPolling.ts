import { useCallback, useEffect, useRef, useState } from 'react'
import { App } from 'antd'
import { getAnalysisTaskDetail } from '../api/analysisTasks'
import { useUserSettings } from '../settings/UserSettingsContext'
import type { AnalysisTaskRecord, AnalysisTaskStatus } from '../types/api'

const ACTIVE_STATUSES: AnalysisTaskStatus[] = ['PENDING', 'PROCESSING']
const FOREGROUND_INTERVAL = 2500
const BACKGROUND_INTERVAL = 10000

export function useAnalysisTaskPolling(taskId?: string) {
  const { message } = App.useApp()
  const { settings } = useUserSettings()
  const [task, setTask] = useState<AnalysisTaskRecord | null>(null)
  const [loading, setLoading] = useState(Boolean(taskId))
  const [refreshing, setRefreshing] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [lastUpdatedAt, setLastUpdatedAt] = useState<Date | null>(null)
  const [version, setVersion] = useState(0)
  const taskIdForDataRef = useRef<string | undefined>(undefined)
  const notifiedTerminalRef = useRef<Set<string>>(new Set())
  const notifyOnTaskCompleteRef = useRef(false)
  notifyOnTaskCompleteRef.current = Boolean(settings?.notifyOnTaskComplete)

  const refresh = useCallback(() => setVersion((value) => value + 1), [])
  const resumeWith = useCallback((nextTask: AnalysisTaskRecord) => {
    taskIdForDataRef.current = nextTask.taskId
    setTask(nextTask)
    setError(null)
    setLastUpdatedAt(new Date())
    setVersion((value) => value + 1)
  }, [])

  useEffect(() => {
    taskIdForDataRef.current = undefined
    setTask(null)
    setError(null)
    setLastUpdatedAt(null)
    setLoading(Boolean(taskId))
  }, [taskId])

  useEffect(() => {
    if (!taskId) {
      setLoading(false)
      return
    }

    let disposed = false
    let running = false
    let timer: number | undefined
    let controller: AbortController | undefined
    let lastStatus: AnalysisTaskStatus | undefined = taskIdForDataRef.current === taskId ? task?.status : undefined
    let failureCount = 0

    const clearTimer = () => {
      if (timer !== undefined) window.clearTimeout(timer)
      timer = undefined
    }

    const schedule = (delay: number) => {
      clearTimer()
      if (!disposed) timer = window.setTimeout(run, delay)
    }

    const run = async () => {
      if (disposed || running) return
      running = true
      clearTimer()
      controller = new AbortController()
      setRefreshing(true)
      setError(null)
      try {
        const nextTask = await getAnalysisTaskDetail(taskId, controller.signal)
        if (disposed) return
        if (lastStatus === 'PROCESSING'
          && (nextTask.status === 'SUCCESS' || nextTask.status === 'FAILED')
          && notifyOnTaskCompleteRef.current) {
          const notificationKey = `${nextTask.taskId}:${nextTask.status}`
          if (!notifiedTerminalRef.current.has(notificationKey)) {
            notifiedTerminalRef.current.add(notificationKey)
            if (nextTask.status === 'SUCCESS') message.success('音频分析任务已完成')
            else message.error('音频分析任务执行失败，请查看失败原因')
          }
        }
        lastStatus = nextTask.status
        taskIdForDataRef.current = taskId
        failureCount = 0
        setTask(nextTask)
        setLastUpdatedAt(new Date())
        if (ACTIVE_STATUSES.includes(nextTask.status)) {
          schedule(document.hidden ? BACKGROUND_INTERVAL : FOREGROUND_INTERVAL)
        }
      } catch (requestError) {
        if (disposed || (requestError instanceof DOMException && requestError.name === 'AbortError')) return
        failureCount += 1
        setError(requestError instanceof Error ? requestError.message : '任务信息查询失败')
        if (!lastStatus || ACTIVE_STATUSES.includes(lastStatus)) {
          const retryDelay = Math.min(FOREGROUND_INTERVAL * 2 ** failureCount, 30000)
          schedule(document.hidden ? Math.max(retryDelay, BACKGROUND_INTERVAL) : retryDelay)
        }
      } finally {
        if (!disposed) {
          running = false
          setLoading(false)
          setRefreshing(false)
        }
      }
    }

    const handleVisibilityChange = () => {
      if (disposed || !lastStatus || !ACTIVE_STATUSES.includes(lastStatus)) return
      if (document.hidden) {
        schedule(BACKGROUND_INTERVAL)
      } else if (!running) {
        schedule(0)
      }
    }

    setLoading((current) => current || task === null)
    void run()
    document.addEventListener('visibilitychange', handleVisibilityChange)

    return () => {
      disposed = true
      clearTimer()
      controller?.abort()
      document.removeEventListener('visibilitychange', handleVisibilityChange)
    }
  }, [message, taskId, version])

  return { task, loading, refreshing, error, lastUpdatedAt, refresh, resumeWith }
}
