import { useCallback, useEffect, useState } from 'react'
import { getAudioVersionsByTask } from '../api/audioVersions'
import type { AudioVersion } from '../types/audioVersion'

export function useAudioVersions(taskId?: string) {
  const [versions, setVersions] = useState<AudioVersion[]>([])
  const [loading, setLoading] = useState(Boolean(taskId))
  const [error, setError] = useState<string | null>(null)
  const [refreshKey, setRefreshKey] = useState(0)
  const refresh = useCallback(() => setRefreshKey((value) => value + 1), [])

  useEffect(() => {
    setVersions([])
    setError(null)
  }, [taskId])

  useEffect(() => {
    if (!taskId) {
      setLoading(false)
      return
    }
    const controller = new AbortController()
    setLoading(true)
    setError(null)
    getAudioVersionsByTask(taskId, controller.signal)
      .then((result) => setVersions(result.versions))
      .catch((requestError: unknown) => {
        if (requestError instanceof DOMException
          && requestError.name === 'AbortError') return
        setError(requestError instanceof Error
          ? requestError.message : '版本记录加载失败，请稍后重试')
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [refreshKey, taskId])

  return { versions, loading, error, refresh }
}
