import { useCallback, useEffect, useRef, useState } from 'react'
import { createAnalysisTask } from '../api/analysisTasks'
import type { AnalysisTaskRecord } from '../types/api'

export function useCreateAnalysisTask() {
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const controllerRef = useRef<AbortController | null>(null)
  const inFlightRef = useRef(false)
  const mountedRef = useRef(true)

  useEffect(() => {
    mountedRef.current = true
    return () => {
      mountedRef.current = false
      controllerRef.current?.abort()
    }
  }, [])

  const resetError = useCallback(() => setError(null), [])
  const create = useCallback(async (audioFileId: string): Promise<AnalysisTaskRecord | null> => {
    if (inFlightRef.current) return null
    inFlightRef.current = true
    const controller = new AbortController()
    controllerRef.current = controller
    setLoading(true)
    setError(null)
    try {
      return await createAnalysisTask(audioFileId, controller.signal)
    } catch (requestError) {
      if (!(requestError instanceof DOMException && requestError.name === 'AbortError')) {
        if (mountedRef.current) setError(requestError instanceof Error ? requestError.message : '分析任务创建失败')
      }
      return null
    } finally {
      if (controllerRef.current === controller) controllerRef.current = null
      inFlightRef.current = false
      if (mountedRef.current) setLoading(false)
    }
  }, [])

  return { create, loading, error, resetError }
}
