import { useCallback, useEffect, useState } from 'react'
import { getAudioFileDetail } from '../api/audioFiles'
import type { AudioFileRecord } from '../types/api'

export function useAudioFileDetail(audioFileId?: string) {
  const [data, setData] = useState<AudioFileRecord | null>(null)
  const [loading, setLoading] = useState(Boolean(audioFileId))
  const [error, setError] = useState<string | null>(null)
  const [version, setVersion] = useState(0)

  const refresh = useCallback(() => setVersion((value) => value + 1), [])

  useEffect(() => {
    setData(null)
    setError(null)
  }, [audioFileId])

  useEffect(() => {
    if (!audioFileId) {
      setLoading(false)
      return
    }

    const controller = new AbortController()
    setLoading(true)
    setError(null)
    getAudioFileDetail(audioFileId, controller.signal)
      .then(setData)
      .catch((requestError: unknown) => {
        if (requestError instanceof DOMException && requestError.name === 'AbortError') return
        setError(requestError instanceof Error ? requestError.message : '文件信息查询失败')
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })

    return () => controller.abort()
  }, [audioFileId, version])

  return { data, loading, error, refresh }
}
