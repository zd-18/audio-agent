import { useCallback, useEffect, useRef, useState } from 'react'
import { getUserTasks } from '../api/userTasks'
import type { PageResult } from '../types/api'
import type { UserTaskProgress } from '../types/userTask'

const POLL_INTERVAL_MS = 4000
const EMPTY_PAGE: PageResult<UserTaskProgress> = {
  records: [],
  current: 1,
  size: 100,
  total: 0,
  pages: 0,
}

export function useUserTasks() {
  const [data, setData] = useState(EMPTY_PAGE)
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [version, setVersion] = useState(0)
  const dataRef = useRef(data)
  dataRef.current = data

  const refresh = useCallback(() => setVersion((value) => value + 1), [])

  useEffect(() => {
    let disposed = false
    let controller: AbortController | null = null
    let timer: number | undefined

    const load = async (background = false) => {
      controller?.abort()
      controller = new AbortController()
      if (background) setRefreshing(true)
      else setLoading(true)
      try {
        const page = await getUserTasks(1, 100, controller.signal)
        if (disposed) return
        setData(page)
        setError(null)
        const hasRunningTask = page.records.some((task) => task.status === 'PROCESSING')
        if (hasRunningTask) timer = window.setTimeout(() => void load(true), POLL_INTERVAL_MS)
      } catch (requestError) {
        if (disposed || (requestError instanceof DOMException && requestError.name === 'AbortError')) return
        setError(requestError instanceof Error
          ? requestError.message
          : '任务进度加载失败，请稍后重试。')
      } finally {
        if (!disposed) {
          setLoading(false)
          setRefreshing(false)
        }
      }
    }

    void load(dataRef.current.records.length > 0)
    return () => {
      disposed = true
      controller?.abort()
      if (timer) window.clearTimeout(timer)
    }
  }, [version])

  return { data, loading, refreshing, error, refresh }
}
