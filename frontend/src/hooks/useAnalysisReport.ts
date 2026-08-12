import { useCallback, useEffect, useState } from 'react'
import { getAnalysisReport } from '../api/analysisReport'
import { getAnalysisTaskDetail } from '../api/analysisTasks'
import { ApiError } from '../api/http'
import type { AudioAnalysisReport } from '../types/analysisReport'
import type { AnalysisTaskRecord } from '../types/api'

const REPORT_NOT_READY_CODE = 40205

export function useAnalysisReport(taskId?: string) {
  const [report, setReport] = useState<AudioAnalysisReport | null>(null)
  const [relatedTask, setRelatedTask] = useState<AnalysisTaskRecord | null>(null)
  const [error, setError] = useState<ApiError | null>(null)
  const [loading, setLoading] = useState(Boolean(taskId))
  const [refreshing, setRefreshing] = useState(false)
  const [version, setVersion] = useState(0)

  const refresh = useCallback(() => setVersion((value) => value + 1), [])

  useEffect(() => {
    setReport(null)
    setRelatedTask(null)
    setError(null)
    setLoading(Boolean(taskId))
  }, [taskId])

  useEffect(() => {
    if (!taskId) {
      setLoading(false)
      return
    }

    const controller = new AbortController()
    setRefreshing(true)
    setError(null)

    getAnalysisReport(taskId, controller.signal)
      .then((nextReport) => {
        setReport(nextReport)
        setRelatedTask(null)
      })
      .catch(async (requestError: unknown) => {
        if (requestError instanceof DOMException && requestError.name === 'AbortError') return

        const nextError = requestError instanceof ApiError
          ? requestError
          : new ApiError(requestError instanceof Error ? requestError.message : '报告加载失败，请稍后重试')

        console.error('[analysis-report] report request failed', requestError)

        if (nextError.code === REPORT_NOT_READY_CODE) {
          try {
            const task = await getAnalysisTaskDetail(taskId, controller.signal)
            if (!controller.signal.aborted) setRelatedTask(task)
          } catch (taskError) {
            if (!(taskError instanceof DOMException && taskError.name === 'AbortError')) {
              console.error('[analysis-report] task status request failed', taskError)
            }
          }
        }

        if (!controller.signal.aborted) setError(nextError)
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setLoading(false)
          setRefreshing(false)
        }
      })

    return () => controller.abort()
  }, [taskId, version])

  return { report, relatedTask, error, loading, refreshing, refresh }
}
