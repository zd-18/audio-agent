import { useCallback, useEffect, useRef, useState } from 'react'
import { createTranscriptionTask } from '../api/transcriptions'
import { ApiError } from '../api/http'
import type { TranscriptionTask } from '../types/transcription'

export function useCreateTranscription() {
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const controllerRef = useRef<AbortController | null>(null)
  const inFlightRef = useRef(false)

  useEffect(() => () => controllerRef.current?.abort(), [])

  const create = useCallback(async (audioFileId: string): Promise<TranscriptionTask | null> => {
    if (inFlightRef.current) return null
    inFlightRef.current = true
    const controller = new AbortController()
    controllerRef.current = controller
    setLoading(true)
    setError(null)
    try {
      return await createTranscriptionTask(audioFileId, controller.signal)
    } catch (requestError) {
      if (!(requestError instanceof DOMException && requestError.name === 'AbortError')) {
        setError(
          requestError instanceof ApiError
            ? requestError.message
            : '转写任务创建失败，请稍后重试',
        )
      }
      return null
    } finally {
      if (controllerRef.current === controller) controllerRef.current = null
      inFlightRef.current = false
      if (!controller.signal.aborted) setLoading(false)
    }
  }, [])

  const clearError = useCallback(() => setError(null), [])

  return { create, loading, error, clearError }
}
