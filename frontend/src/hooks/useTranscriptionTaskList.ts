import { useCallback, useEffect, useState } from 'react'
import { getTranscriptionTasks } from '../api/transcriptions'
import type { PageResult } from '../types/api'
import type { TranscriptionTask, TranscriptionTaskListParams } from '../types/transcription'

const EMPTY_PAGE: PageResult<TranscriptionTask> = {
  records: [], current: 1, size: 10, total: 0, pages: 0,
}

export function useTranscriptionTaskList(query: TranscriptionTaskListParams) {
  const [data, setData] = useState<PageResult<TranscriptionTask>>(EMPTY_PAGE)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [version, setVersion] = useState(0)
  const refresh = useCallback(() => setVersion((value) => value + 1), [])

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setError(null)
    getTranscriptionTasks(query, controller.signal)
      .then(setData)
      .catch((requestError: unknown) => {
        if (requestError instanceof DOMException && requestError.name === 'AbortError') return
        setError(requestError instanceof Error ? requestError.message : '转写任务列表加载失败')
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [query.current, query.size, query.status, query.audioFileId, version])

  return { data, loading, error, refresh }
}
