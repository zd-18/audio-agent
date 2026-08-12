import { useCallback, useEffect, useRef, useState } from 'react'
import { getTranscriptionTask } from '../api/transcriptions'
import type { TranscriptionTask } from '../types/transcription'

const FOREGROUND_INTERVAL = 2000
const BACKGROUND_INTERVAL = 10000
const NETWORK_RETRY_INTERVAL = 5000
export const TRANSCRIPTION_POLLING_TIMEOUT_MS = 15 * 60 * 1000

export function useTranscriptionTaskPolling(taskId?: string) {
  const [task, setTask] = useState<TranscriptionTask | null>(null)
  const [loading, setLoading] = useState(Boolean(taskId))
  const [refreshing, setRefreshing] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [timedOut, setTimedOut] = useState(false)
  const [version, setVersion] = useState(0)
  const currentStatusRef = useRef<TranscriptionTask['status'] | undefined>()
  const refresh = useCallback(() => {
    setTimedOut(false)
    setVersion((value) => value + 1)
  }, [])
  const resumeWith = useCallback((next: TranscriptionTask) => {
    currentStatusRef.current = next.status
    setTask(next)
    setError(null)
    setTimedOut(false)
    setVersion((value) => value + 1)
  }, [])

  useEffect(() => {
    currentStatusRef.current = undefined
    setTask(null)
    setError(null)
    setTimedOut(false)
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
    const pollingStartedAt = Date.now()

    const active = (status?: TranscriptionTask['status']) => status === 'PENDING' || status === 'RUNNING'
    const stopForTimeout = () => {
      if (disposed) return
      if (timer !== undefined) window.clearTimeout(timer)
      timer = undefined
      setTimedOut(true)
      setRefreshing(false)
      setLoading(false)
    }
    const schedule = (delay: number) => {
      if (timer !== undefined) window.clearTimeout(timer)
      const remaining = TRANSCRIPTION_POLLING_TIMEOUT_MS - (Date.now() - pollingStartedAt)
      if (remaining <= 0) {
        stopForTimeout()
        return
      }
      if (!disposed) timer = window.setTimeout(run, Math.min(delay, remaining))
    }
    const run = async () => {
      if (disposed || running) return
      if (Date.now() - pollingStartedAt >= TRANSCRIPTION_POLLING_TIMEOUT_MS) {
        stopForTimeout()
        return
      }
      running = true
      controller = new AbortController()
      setRefreshing(true)
      try {
        const next = await getTranscriptionTask(taskId, controller.signal)
        if (disposed) return
        currentStatusRef.current = next.status
        setTask(next)
        setError(null)
        if (active(next.status)) {
          schedule(document.hidden ? BACKGROUND_INTERVAL : FOREGROUND_INTERVAL)
        }
      } catch (requestError) {
        if (disposed || (requestError instanceof DOMException && requestError.name === 'AbortError')) return
        setError(requestError instanceof Error ? requestError.message : '转写任务加载失败')
        if (active(currentStatusRef.current) || currentStatusRef.current === undefined) {
          schedule(document.hidden ? BACKGROUND_INTERVAL : NETWORK_RETRY_INTERVAL)
        }
      } finally {
        if (!disposed) {
          running = false
          setLoading(false)
          setRefreshing(false)
        }
      }
    }
    const visibility = () => {
      if (
        (active(currentStatusRef.current) || currentStatusRef.current === undefined)
        && !running
      ) {
        schedule(document.hidden ? BACKGROUND_INTERVAL : 0)
      }
    }
    void run()
    document.addEventListener('visibilitychange', visibility)
    return () => {
      disposed = true
      if (timer !== undefined) window.clearTimeout(timer)
      controller?.abort()
      document.removeEventListener('visibilitychange', visibility)
    }
  }, [taskId, version])

  return { task, loading, refreshing, error, timedOut, refresh, resumeWith }
}
