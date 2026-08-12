import { useCallback, useEffect, useRef, useState } from 'react'
import {
  createContentAnalysisTask,
  getContentAnalysisResult,
  getContentAnalysisTask,
  retryContentAnalysisTask,
} from '../api/contentAnalysis'
import type {
  ContentAnalysisResult,
  ContentAnalysisTask,
  ContentAnalysisType,
  SummaryStyle,
} from '../types/contentAnalysis'

const POLLING_INTERVAL_MS = 2000
export const CONTENT_ANALYSIS_POLLING_TIMEOUT_MS = 15 * 60 * 1000

function storageKey(transcriptId: string) {
  return `audio-agent:content-analysis:${transcriptId}`
}

function readStoredTaskId(transcriptId?: string) {
  if (!transcriptId || typeof window === 'undefined') return undefined
  return window.localStorage.getItem(storageKey(transcriptId)) || undefined
}

export function useContentAnalysis(transcriptId?: string) {
  const [taskId, setTaskId] = useState<string | undefined>(
    () => readStoredTaskId(transcriptId),
  )
  const [task, setTask] = useState<ContentAnalysisTask | null>(null)
  const [result, setResult] = useState<ContentAnalysisResult | null>(null)
  const [creating, setCreating] = useState(false)
  const [retrying, setRetrying] = useState(false)
  const [loading, setLoading] = useState(Boolean(taskId))
  const [error, setError] = useState<string | null>(null)
  const [timedOut, setTimedOut] = useState(false)
  const [version, setVersion] = useState(0)
  const mountedRef = useRef(true)
  const creatingRef = useRef(false)
  const retryingRef = useRef(false)

  useEffect(() => {
    mountedRef.current = true
    return () => {
      mountedRef.current = false
    }
  }, [])

  useEffect(() => {
    const stored = readStoredTaskId(transcriptId)
    setTaskId(stored)
    setTask(null)
    setResult(null)
    setError(null)
    setTimedOut(false)
    setLoading(Boolean(stored))
  }, [transcriptId])

  const start = useCallback(async (
    analysisTypes: ContentAnalysisType[],
    summaryStyle: SummaryStyle = 'STANDARD',
  ) => {
    if (!transcriptId || creatingRef.current) return null
    creatingRef.current = true
    setCreating(true)
    setError(null)
    setTimedOut(false)
    try {
      const next = await createContentAnalysisTask({
        transcriptId,
        analysisTypes,
        summaryStyle,
      })
      if (!mountedRef.current) return next
      window.localStorage.setItem(storageKey(transcriptId), next.taskId)
      setTaskId(next.taskId)
      setTask(next)
      setResult(null)
      setLoading(true)
      setVersion((value) => value + 1)
      return next
    } catch (requestError) {
      if (mountedRef.current) {
        setError(requestError instanceof Error ? requestError.message : '智能分析任务创建失败')
      }
      return null
    } finally {
      creatingRef.current = false
      if (mountedRef.current) setCreating(false)
    }
  }, [transcriptId])

  const retry = useCallback(async () => {
    if (!taskId || retryingRef.current) return null
    retryingRef.current = true
    setRetrying(true)
    setError(null)
    setTimedOut(false)
    try {
      const next = await retryContentAnalysisTask(taskId)
      if (!mountedRef.current) return next
      setTask(next)
      setResult(null)
      setLoading(true)
      setVersion((value) => value + 1)
      return next
    } catch (requestError) {
      if (mountedRef.current) {
        setError(requestError instanceof Error ? requestError.message : '智能分析任务重试失败')
      }
      return null
    } finally {
      retryingRef.current = false
      if (mountedRef.current) setRetrying(false)
    }
  }, [taskId])

  const refresh = useCallback(() => {
    setTimedOut(false)
    setVersion((value) => value + 1)
  }, [])

  useEffect(() => {
    if (!taskId) {
      setLoading(false)
      return
    }
    let disposed = false
    let timer: number | undefined
    let controller: AbortController | undefined
    const startedAt = Date.now()

    const schedule = () => {
      if (disposed) return
      const elapsed = Date.now() - startedAt
      if (elapsed >= CONTENT_ANALYSIS_POLLING_TIMEOUT_MS) {
        setTimedOut(true)
        setLoading(false)
        return
      }
      timer = window.setTimeout(run, Math.min(
        POLLING_INTERVAL_MS,
        CONTENT_ANALYSIS_POLLING_TIMEOUT_MS - elapsed,
      ))
    }
    const run = async () => {
      controller = new AbortController()
      try {
        const next = await getContentAnalysisTask(taskId, controller.signal)
        if (disposed) return
        setTask(next)
        setError(null)
        if (next.status === 'SUCCESS') {
          const nextResult = await getContentAnalysisResult(taskId, controller.signal)
          if (!disposed) {
            setResult({
              ...nextResult,
              keyPoints: Array.isArray(nextResult.keyPoints) ? nextResult.keyPoints : [],
              chapters: Array.isArray(nextResult.chapters) ? nextResult.chapters : [],
              speechIssues: Array.isArray(nextResult.speechIssues) ? nextResult.speechIssues : [],
              summary: {
                ...nextResult.summary,
                topics: Array.isArray(nextResult.summary?.topics)
                  ? nextResult.summary.topics
                  : [],
              },
            })
            setLoading(false)
          }
          return
        }
        if (next.status === 'FAILED') {
          setLoading(false)
          return
        }
        schedule()
      } catch (requestError) {
        if (disposed || (requestError instanceof DOMException && requestError.name === 'AbortError')) {
          return
        }
        setError(requestError instanceof Error ? requestError.message : '智能分析状态加载失败')
        schedule()
      }
    }
    setLoading(true)
    void run()
    return () => {
      disposed = true
      if (timer !== undefined) window.clearTimeout(timer)
      controller?.abort()
    }
  }, [taskId, version])

  return {
    taskId,
    task,
    result,
    creating,
    retrying,
    loading,
    error,
    timedOut,
    start,
    retry,
    refresh,
  }
}
