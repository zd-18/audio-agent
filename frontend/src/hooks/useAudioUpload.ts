import { useCallback, useEffect, useRef, useState } from 'react'
import { uploadAudioFile } from '../api/audioFiles'
import type { AudioFileRecord } from '../types/api'

const MAX_FILE_SIZE = 500 * 1024 * 1024
const SUPPORTED_EXTENSIONS = new Set(['mp3', 'wav', 'm4a', 'mp4'])

function getExtension(fileName: string) {
  return fileName.split('.').pop()?.toLowerCase() || ''
}

export function validateAudioFile(file: File) {
  if (!SUPPORTED_EXTENSIONS.has(getExtension(file.name))) {
    return '当前仅支持 MP3、WAV、M4A 和 MP4 文件'
  }
  if (file.size > MAX_FILE_SIZE) {
    return '普通上传暂不支持超过 500MB 的文件'
  }
  return null
}

export type UploadStatus = 'idle' | 'ready' | 'uploading' | 'success' | 'error'

export function useAudioUpload() {
  const [file, setFile] = useState<File | null>(null)
  const [status, setStatus] = useState<UploadStatus>('idle')
  const [progress, setProgress] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<AudioFileRecord | null>(null)
  const controllerRef = useRef<AbortController | null>(null)
  const mountedRef = useRef(true)

  useEffect(() => {
    mountedRef.current = true
    return () => {
      mountedRef.current = false
      controllerRef.current?.abort()
    }
  }, [])

  const selectFile = useCallback((nextFile: File) => {
    const validationError = validateAudioFile(nextFile)
    setError(validationError)
    setFile(validationError ? null : nextFile)
    setStatus(validationError ? 'error' : 'ready')
    setProgress(0)
    setResult(null)
    return !validationError
  }, [])

  const reset = useCallback(() => {
    controllerRef.current?.abort()
    controllerRef.current = null
    setFile(null)
    setStatus('idle')
    setProgress(0)
    setError(null)
    setResult(null)
  }, [])

  const upload = useCallback(async () => {
    if (!file || status === 'uploading') return

    const controller = new AbortController()
    controllerRef.current = controller
    setStatus('uploading')
    setError(null)
    setProgress(0)

    try {
      const uploaded = await uploadAudioFile(file, {
        signal: controller.signal,
        onProgress: (value) => mountedRef.current && setProgress(value),
      })
      if (!mountedRef.current) return
      setResult(uploaded)
      setProgress(100)
      setStatus('success')
    } catch (uploadError) {
      if (!mountedRef.current || (uploadError instanceof DOMException && uploadError.name === 'AbortError')) return
      setError(uploadError instanceof Error ? uploadError.message : '上传失败，请稍后重试')
      setStatus('error')
    } finally {
      if (controllerRef.current === controller) controllerRef.current = null
    }
  }, [file, status])

  return { file, status, progress, error, result, selectFile, upload, reset }
}
