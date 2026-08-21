import { useCallback, useEffect, useRef, useState } from 'react'
import {
  completeMultipartUpload,
  getMultipartUploadProgress,
  initializeMultipartUpload,
  uploadMultipartChunk,
} from '../api/audioFiles'
import type { AudioFileRecord } from '../types/api'
import { sha256File } from '../utils/sha256File'

const MAX_FILE_SIZE = 20 * 1024 * 1024 * 1024
const CHUNK_SIZE = 8 * 1024 * 1024
const SUPPORTED_EXTENSIONS = new Set(['mp3', 'wav', 'm4a', 'mp4'])

function getExtension(fileName: string) {
  return fileName.split('.').pop()?.toLowerCase() || ''
}

export function validateAudioFile(file: File) {
  if (!SUPPORTED_EXTENSIONS.has(getExtension(file.name))) {
    return '当前仅支持 MP3、WAV、M4A 和 MP4 文件'
  }
  if (file.size <= 0) {
    return '不能上传空文件'
  }
  if (file.size > MAX_FILE_SIZE) {
    return '单个文件不能超过 20GB'
  }
  return null
}

export type UploadStatus =
  | 'idle'
  | 'ready'
  | 'hashing'
  | 'uploading'
  | 'paused'
  | 'merging'
  | 'success'
  | 'error'

export function useAudioUpload() {
  const [file, setFile] = useState<File | null>(null)
  const [status, setStatus] = useState<UploadStatus>('idle')
  const [progress, setProgress] = useState(0)
  const [hashProgress, setHashProgress] = useState(0)
  const [uploadedCount, setUploadedCount] = useState(0)
  const [totalChunks, setTotalChunks] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<AudioFileRecord | null>(null)
  const [instantUpload, setInstantUpload] = useState(false)
  const [resumed, setResumed] = useState(false)
  const controllerRef = useRef<AbortController | null>(null)
  const hashRef = useRef<string | null>(null)
  const runSequenceRef = useRef(0)
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
    runSequenceRef.current += 1
    controllerRef.current?.abort()
    controllerRef.current = null
    hashRef.current = null
    setError(validationError)
    setFile(validationError ? null : nextFile)
    setStatus(validationError ? 'error' : 'ready')
    setProgress(0)
    setHashProgress(0)
    setUploadedCount(0)
    setTotalChunks(0)
    setResult(null)
    setInstantUpload(false)
    setResumed(false)
    return !validationError
  }, [])

  const reset = useCallback(() => {
    runSequenceRef.current += 1
    controllerRef.current?.abort()
    controllerRef.current = null
    hashRef.current = null
    setFile(null)
    setStatus('idle')
    setProgress(0)
    setHashProgress(0)
    setUploadedCount(0)
    setTotalChunks(0)
    setError(null)
    setResult(null)
    setInstantUpload(false)
    setResumed(false)
  }, [])

  const pause = useCallback(() => {
    if (!controllerRef.current) return
    runSequenceRef.current += 1
    controllerRef.current.abort()
    controllerRef.current = null
    setStatus('paused')
    setError(null)
  }, [])

  const upload = useCallback(async () => {
    if (!file || controllerRef.current) return

    const controller = new AbortController()
    const runSequence = ++runSequenceRef.current
    controllerRef.current = controller
    setError(null)
    setInstantUpload(false)
    setResumed(false)

    try {
      let sha256 = hashRef.current
      if (!sha256) {
        setStatus('hashing')
        setHashProgress(0)
        sha256 = await sha256File(file, {
          signal: controller.signal,
          onProgress: (value) => mountedRef.current
            && runSequenceRef.current === runSequence
            && setHashProgress(value),
        })
        if (runSequenceRef.current !== runSequence) return
        hashRef.current = sha256
      }

      const expectedTotalChunks = Math.ceil(file.size / CHUNK_SIZE)
      setTotalChunks(expectedTotalChunks)
      setStatus('uploading')
      const initialized = await initializeMultipartUpload({
        originalName: file.name,
        mimeType: file.type || 'application/octet-stream',
        sizeBytes: file.size,
        sha256,
        chunkSize: CHUNK_SIZE,
        totalChunks: expectedTotalChunks,
      }, controller.signal)

      if (initialized.instantUpload && initialized.audioFile) {
        if (!mountedRef.current || runSequenceRef.current !== runSequence) return
        setResult(initialized.audioFile)
        setInstantUpload(true)
        setProgress(100)
        setUploadedCount(initialized.totalChunks)
        setTotalChunks(initialized.totalChunks)
        setStatus('success')
        return
      }
      if (!initialized.uploadId) {
        throw new Error('服务端未返回有效的上传任务，请重试')
      }

      const serverProgress = await getMultipartUploadProgress(
        initialized.uploadId,
        controller.signal,
      )
      if (runSequenceRef.current !== runSequence) return
      const uploaded = new Set(serverProgress.uploadedChunks)
      setResumed(initialized.resumed)
      setUploadedCount(uploaded.size)
      setTotalChunks(serverProgress.totalChunks)
      setProgress(Math.round((uploaded.size / serverProgress.totalChunks) * 100))

      for (let chunkIndex = 0; chunkIndex < serverProgress.totalChunks; chunkIndex += 1) {
        if (uploaded.has(chunkIndex)) continue
        if (controller.signal.aborted) throw new DOMException('上传已暂停', 'AbortError')
        const start = chunkIndex * CHUNK_SIZE
        const chunk = file.slice(start, Math.min(file.size, start + CHUNK_SIZE))
        const chunkResult = await uploadMultipartChunk(
          initialized.uploadId,
          chunkIndex,
          chunk,
          controller.signal,
        )
        uploaded.add(chunkIndex)
        if (!mountedRef.current || runSequenceRef.current !== runSequence) return
        setUploadedCount(chunkResult.uploadedCount)
        setProgress(Math.round((chunkResult.uploadedCount / chunkResult.totalChunks) * 100))
      }

      setStatus('merging')
      const completed = await completeMultipartUpload(
        initialized.uploadId,
        controller.signal,
      )
      if (!mountedRef.current || runSequenceRef.current !== runSequence) return
      setResult(completed.audioFile)
      setInstantUpload(completed.instantUpload)
      setProgress(100)
      setUploadedCount(serverProgress.totalChunks)
      setStatus('success')
    } catch (uploadError) {
      if (!mountedRef.current || runSequenceRef.current !== runSequence) return
      if (uploadError instanceof DOMException && uploadError.name === 'AbortError') {
        setStatus('paused')
        return
      }
      setError(uploadError instanceof Error ? uploadError.message : '上传失败，请稍后重试')
      setStatus('error')
    } finally {
      if (controllerRef.current === controller) controllerRef.current = null
    }
  }, [file])

  return {
    file,
    status,
    progress,
    hashProgress,
    uploadedCount,
    totalChunks,
    error,
    result,
    instantUpload,
    resumed,
    selectFile,
    upload,
    pause,
    reset,
  }
}
