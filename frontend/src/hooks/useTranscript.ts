import { useCallback, useEffect, useState } from 'react'
import { getTranscript } from '../api/transcriptions'
import type { Transcript } from '../types/transcription'

export function useTranscript(taskId?: string, ready = false) {
  const [transcript, setTranscript] = useState<Transcript | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [version, setVersion] = useState(0)
  const refresh = useCallback(() => setVersion((value) => value + 1), [])

  useEffect(() => {
    setTranscript(null)
    setError(null)
  }, [taskId])

  useEffect(() => {
    if (!taskId || !ready) {
      setLoading(false)
      return
    }
    const controller = new AbortController()
    setLoading(true)
    setError(null)
    getTranscript(taskId, controller.signal)
      .then((nextTranscript) => {
        if (controller.signal.aborted) return
        setTranscript({
          ...nextTranscript,
          fullText: nextTranscript.fullText || '',
          segments: Array.isArray(nextTranscript.segments)
            ? nextTranscript.segments
            : [],
        })
      })
      .catch((requestError: unknown) => {
        if (requestError instanceof DOMException && requestError.name === 'AbortError') return
        setError(requestError instanceof Error ? requestError.message : '文字稿加载失败')
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [taskId, ready, version])

  return { transcript, loading, error, refresh }
}
