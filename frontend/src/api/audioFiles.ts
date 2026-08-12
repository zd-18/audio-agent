import {
  ApiError,
  apiRequest,
  authorizedFetch,
  handleUnauthorizedStatus,
  parseApiResponse,
  setXmlHttpRequestAuthHeader,
  unwrapApiResponse,
} from './http'
import type { AudioFileListItem, AudioFileRecord, PageResult } from '../types/api'
import type { AudioPlaybackUrlResponse } from '../types/audioFile'

interface UploadAudioOptions {
  signal?: AbortSignal
  onProgress?: (percent: number) => void
}

export function uploadAudioFile(file: File, options: UploadAudioOptions = {}) {
  return new Promise<AudioFileRecord>((resolve, reject) => {
    const request = new XMLHttpRequest()
    const formData = new FormData()
    formData.append('file', file)

    const abort = () => request.abort()
    options.signal?.addEventListener('abort', abort, { once: true })

    request.open('POST', '/api/v1/files')
    setXmlHttpRequestAuthHeader(request)

    request.upload.addEventListener('progress', (event) => {
      if (event.lengthComputable) {
        options.onProgress?.(Math.round((event.loaded / event.total) * 100))
      }
    })

    request.addEventListener('load', () => {
      options.signal?.removeEventListener('abort', abort)
      handleUnauthorizedStatus(request.status)
      try {
        const response = parseApiResponse<AudioFileRecord>(request.responseText)
        if (request.status < 200 || request.status >= 300) {
          reject(new ApiError(response.message || `上传失败（${request.status}）`, response.code, response.requestId))
          return
        }
        resolve(unwrapApiResponse(response))
      } catch (error) {
        reject(error instanceof ApiError ? error : new ApiError('服务器返回了无法识别的响应'))
      }
    })

    request.addEventListener('error', () => {
      options.signal?.removeEventListener('abort', abort)
      reject(new ApiError('网络连接失败，请确认服务已启动后重试'))
    })

    request.addEventListener('abort', () => {
      options.signal?.removeEventListener('abort', abort)
      reject(new DOMException('上传已取消', 'AbortError'))
    })

    request.send(formData)
  })
}

export function getAudioFileDetail(audioFileId: string, signal?: AbortSignal) {
  return apiRequest<AudioFileRecord>(`/api/v1/files/${encodeURIComponent(audioFileId)}`, {
    signal,
  })
}

export function getAudioPlaybackUrl(audioFileId: string, signal?: AbortSignal) {
  return apiRequest<AudioPlaybackUrlResponse>(
    `/api/v1/files/${encodeURIComponent(audioFileId)}/playback-url`,
    {
      signal,
    },
  )
}

export interface AudioFileListParams {
  current?: number
  size?: number
  keyword?: string
  status?: string
}

export function getAudioFileList(params: AudioFileListParams = {}, signal?: AbortSignal) {
  const search = new URLSearchParams({
    current: String(params.current ?? 1),
    size: String(params.size ?? 10),
  })
  const keyword = params.keyword?.trim()
  if (keyword) search.set('keyword', keyword)
  if (params.status) search.set('status', params.status)

  return apiRequest<PageResult<AudioFileListItem>>(`/api/v1/files?${search.toString()}`, {
    signal,
  })
}

function decodeMimeEncodedWord(value: string) {
  const match = value.match(/^=\?UTF-8\?([BQ])\?(.+)\?=$/i)
  if (!match) return value
  try {
    if (match[1].toUpperCase() === 'B') {
      const binary = window.atob(match[2])
      const bytes = Uint8Array.from(binary, (character) => character.charCodeAt(0))
      return new TextDecoder('utf-8').decode(bytes)
    }
    const source = match[2].replace(/_/g, ' ')
    const bytes: number[] = []
    for (let index = 0; index < source.length; index += 1) {
      if (source[index] === '=' && /^[0-9A-F]{2}$/i.test(source.slice(index + 1, index + 3))) {
        bytes.push(Number.parseInt(source.slice(index + 1, index + 3), 16))
        index += 2
      } else {
        bytes.push(source.charCodeAt(index))
      }
    }
    return new TextDecoder('utf-8').decode(Uint8Array.from(bytes))
  } catch {
    return value
  }
}

function sanitizeDownloadFileName(value: string) {
  const sanitized = value
    .replace(/[\\/:*?"<>|\u0000-\u001f]/g, '_')
    .replace(/[. ]+$/g, '')
    .trim()
  return sanitized || 'audio-download.wav'
}

function getDownloadFileName(contentDisposition: string | null, fallback: string) {
  if (!contentDisposition) return sanitizeDownloadFileName(fallback)
  const encoded = contentDisposition.match(/filename\*\s*=\s*(?:UTF-8'')?([^;]+)/i)?.[1]
  if (encoded) {
    try {
      return sanitizeDownloadFileName(decodeURIComponent(encoded.trim().replace(/^"|"$/g, '')))
    } catch {
      // Continue with the regular filename parameter.
    }
  }
  const regular = contentDisposition.match(/filename\s*=\s*(?:"([^"]*)"|([^;]+))/i)
  const value = (regular?.[1] || regular?.[2])?.trim()
  return sanitizeDownloadFileName(value ? decodeMimeEncodedWord(value) : fallback)
}

export async function downloadAudioFile(audioFileId: string, fileName: string, signal?: AbortSignal) {
  let response: Response
  try {
    response = await authorizedFetch(`/api/v1/files/${encodeURIComponent(audioFileId)}/download`, {
      signal,
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new ApiError('下载请求失败，请检查网络连接后重试')
  }

  if (!response.ok) {
    const body = await response.text()
    try {
      const parsed = parseApiResponse<never>(body)
      throw new ApiError(parsed.message || `下载失败（${response.status}）`, parsed.code, parsed.requestId)
    } catch (error) {
      if (error instanceof ApiError && error.message !== '服务器返回了无法识别的响应') throw error
      throw new ApiError(`下载失败（${response.status}）`)
    }
  }

  const blobUrl = URL.createObjectURL(await response.blob())
  const link = document.createElement('a')
  try {
    link.href = blobUrl
    link.download = getDownloadFileName(
      response.headers.get('Content-Disposition'),
      fileName || `audio-${audioFileId}`,
    )
    document.body.appendChild(link)
    link.click()
  } finally {
    link.remove()
    window.setTimeout(() => URL.revokeObjectURL(blobUrl), 1000)
  }
}
