import { useCallback, useEffect, useRef, useState } from 'react'
import { getAnalysisTaskDetail, getAnalysisTaskList } from '../api/analysisTasks'
import type { AnalysisTaskListItem, AnalysisTaskRecord, PageResult } from '../types/api'

export interface AnalysisTaskListQuery {
  current: number
  size: number
  status?: string
  keyword?: string
  audioFileId?: string
  analysisType?: string
}

const EMPTY_PAGE: PageResult<AnalysisTaskListItem> = {
  records: [],
  current: 1,
  size: 10,
  total: 0,
  pages: 0,
}

export function useAnalysisTaskList(query: AnalysisTaskListQuery) {
  const [data, setData] = useState<PageResult<AnalysisTaskListItem>>(EMPTY_PAGE)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [refreshingTaskId, setRefreshingTaskId] = useState<string | null>(null)
  const [version, setVersion] = useState(0)
  const rowControllerRef = useRef<AbortController | null>(null)
  const rowRequestInFlightRef = useRef(false)

  const refresh = useCallback(() => setVersion((value) => value + 1), [])
  const clearActionError = useCallback(() => setActionError(null), [])

  const applyTaskUpdate = useCallback((task: AnalysisTaskRecord) => {
    setData((current) => ({
      ...current,
      records: current.records.map((record) => (
        record.taskId === task.taskId
          ? { ...record, ...task, fileName: record.fileName }
          : record
      )),
    }))
  }, [])

  const refreshTask = useCallback(async (taskId: string) => {
    if (rowRequestInFlightRef.current) return
    rowRequestInFlightRef.current = true
    const controller = new AbortController()
    rowControllerRef.current = controller
    setRefreshingTaskId(taskId)
    setActionError(null)

    try {
      const task = await getAnalysisTaskDetail(taskId, controller.signal)
      if (!controller.signal.aborted) applyTaskUpdate(task)
    } catch (requestError) {
      if (!(requestError instanceof DOMException && requestError.name === 'AbortError')) {
        setActionError(requestError instanceof Error ? requestError.message : '任务状态刷新失败')
      }
    } finally {
      if (rowControllerRef.current === controller) rowControllerRef.current = null
      rowRequestInFlightRef.current = false
      if (!controller.signal.aborted) setRefreshingTaskId(null)
    }
  }, [applyTaskUpdate])

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setError(null)

    getAnalysisTaskList(query, controller.signal)
      .then(setData)
      .catch((requestError: unknown) => {
        if (requestError instanceof DOMException && requestError.name === 'AbortError') return
        setError(requestError instanceof Error ? requestError.message : '分析任务列表查询失败')
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })

    return () => controller.abort()
  }, [query.current, query.size, query.status, query.keyword, query.audioFileId, query.analysisType, version])

  useEffect(() => () => rowControllerRef.current?.abort(), [])

  return {
    data,
    loading,
    error,
    actionError,
    refreshingTaskId,
    refresh,
    refreshTask,
    applyTaskUpdate,
    clearActionError,
  }
}
