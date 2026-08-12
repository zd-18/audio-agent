import { useCallback, useEffect, useState } from 'react'
import { getProcessingExecutionList } from '../api/processingExecution'
import type { PageResult } from '../types/api'
import type { ProcessingExecutionListItem } from '../types/processingExecution'

interface ProcessingExecutionListQuery {
  current: number
  size: number
  status?: string
}

const EMPTY_PAGE: PageResult<ProcessingExecutionListItem> = {
  records: [],
  current: 1,
  size: 10,
  total: 0,
  pages: 0,
}

export function useProcessingExecutionList(query: ProcessingExecutionListQuery) {
  const [data, setData] = useState<PageResult<ProcessingExecutionListItem>>(EMPTY_PAGE)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [version, setVersion] = useState(0)
  const refresh = useCallback(() => setVersion((value) => value + 1), [])

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setError(null)
    getProcessingExecutionList(query, controller.signal)
      .then((page) => {
        if (!controller.signal.aborted) setData(page)
      })
      .catch((requestError: unknown) => {
        if (requestError instanceof DOMException && requestError.name === 'AbortError') return
        if (!controller.signal.aborted) {
          setError(requestError instanceof Error ? requestError.message : '处理任务列表加载失败')
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [query.current, query.size, query.status, version])

  return { data, loading, error, refresh }
}
