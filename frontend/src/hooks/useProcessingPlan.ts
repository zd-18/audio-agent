import { useCallback, useEffect, useState } from 'react'
import { getProcessingPlan } from '../api/processingPlan'
import { ApiError } from '../api/http'
import type { ProcessingPlan } from '../types/processingPlan'

export function useProcessingPlan(taskId?: string) {
  const [plan, setPlan] = useState<ProcessingPlan | null>(null)
  const [error, setError] = useState<ApiError | null>(null)
  const [loading, setLoading] = useState(Boolean(taskId))
  const [refreshing, setRefreshing] = useState(false)
  const [version, setVersion] = useState(0)

  const reload = useCallback(() => setVersion((value) => value + 1), [])
  const applyPlan = useCallback((nextPlan: ProcessingPlan) => {
    setPlan(nextPlan)
    setError(null)
    setLoading(false)
    setRefreshing(false)
  }, [])

  useEffect(() => {
    setPlan(null)
    setError(null)
    setLoading(Boolean(taskId))
    setRefreshing(false)
  }, [taskId])

  useEffect(() => {
    if (!taskId) {
      setLoading(false)
      return undefined
    }

    const controller = new AbortController()
    setRefreshing(true)
    setError(null)

    getProcessingPlan(taskId, controller.signal)
      .then((nextPlan) => {
        if (!controller.signal.aborted) setPlan(nextPlan)
      })
      .catch((requestError: unknown) => {
        if (requestError instanceof DOMException && requestError.name === 'AbortError') return
        const nextError = requestError instanceof ApiError
          ? requestError
          : new ApiError(requestError instanceof Error ? requestError.message : '处理方案加载失败，请稍后重试')
        console.error('[processing-plan] request failed', requestError)
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

  return { plan, error, loading, refreshing, reload, applyPlan }
}
