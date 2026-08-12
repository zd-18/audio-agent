import { useCallback, useEffect, useState } from 'react'
import { getAudioFileList } from '../api/audioFiles'
import type { AudioFileListItem, PageResult } from '../types/api'

export interface AudioFileListQuery {
  current: number
  size: number
  keyword?: string
  status?: string
}

const EMPTY_PAGE: PageResult<AudioFileListItem> = {
  records: [],
  current: 1,
  size: 10,
  total: 0,
  pages: 0,
}

export function useAudioFileList(query: AudioFileListQuery) {
  const [data, setData] = useState<PageResult<AudioFileListItem>>(EMPTY_PAGE)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [version, setVersion] = useState(0)
  const refresh = useCallback(() => setVersion((value) => value + 1), [])

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setError(null)
    getAudioFileList(query, controller.signal)
      .then(setData)
      .catch((requestError: unknown) => {
        if (requestError instanceof DOMException && requestError.name === 'AbortError') return
        setError(requestError instanceof Error ? requestError.message : '音频文件列表查询失败')
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })

    return () => controller.abort()
  }, [query.current, query.size, query.keyword, query.status, version])

  return { data, loading, error, refresh }
}
