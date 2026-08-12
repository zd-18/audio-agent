import { useCallback, useEffect, useRef, useState } from 'react'
import type { RefObject } from 'react'
import { getAudioPlaybackUrl } from '../api/audioFiles'
import { ApiError } from '../api/http'
import type { AudioPlaybackUrlResponse } from '../types/audioFile'
import { useUserSettings } from '../settings/UserSettingsContext'

const EXPIRY_REFRESH_THRESHOLD_MS = 30_000
const AUDIO_FILE_NOT_AVAILABLE_CODE = 40006
const SUPPORTED_MIME_TYPES = new Set([
  'audio/mpeg',
  'audio/wav',
  'audio/x-wav',
  'audio/ogg',
  'audio/mp4',
  'audio/webm',
])
const EXTENSION_MIME_TYPES: Record<string, string> = {
  mp3: 'audio/mpeg',
  wav: 'audio/wav',
  ogg: 'audio/ogg',
  oga: 'audio/ogg',
  m4a: 'audio/mp4',
  mp4: 'audio/mp4',
  webm: 'audio/webm',
}
const MIME_TYPE_ALIASES: Record<string, string> = {
  'audio/x-wav': 'audio/wav',
  'audio/wave': 'audio/wav',
  'audio/x-m4a': 'audio/mp4',
  'audio/m4a': 'audio/mp4',
  'video/mp4': 'audio/mp4',
  'video/webm': 'audio/webm',
}

type PlaybackRequestReason = 'initial' | 'manual' | 'expiry'

interface PlaybackRequestOptions {
  reason: PlaybackRequestReason
  positionSeconds?: number
  resumePlayback?: boolean
}

interface SeekOptions {
  play?: boolean
  endSeconds?: number
}

export interface AudioPlaybackRange {
  startSeconds: number
  endSeconds: number
}

export interface AudioPlaybackController {
  audioRef: RefObject<HTMLAudioElement>
  sourceUrl?: string
  playback: AudioPlaybackUrlResponse | null
  loading: boolean
  refreshing: boolean
  error: string | null
  unsupported: boolean
  currentTimeSeconds: number
  durationSeconds: number
  isPlaying: boolean
  isWaiting: boolean
  playbackRange: AudioPlaybackRange | null
  volume: number
  muted: boolean
  playbackRate: number
  pause: () => void
  resume: () => Promise<void>
  togglePlayback: () => Promise<void>
  seek: (seconds: number) => void
  seekTo: (seconds: number, options?: SeekOptions) => Promise<void>
  setVolume: (volume: number) => void
  toggleMuted: () => void
  setPlaybackRate: (rate: number) => void
  reload: () => Promise<boolean>
}

function getMimeTypes(playback: AudioPlaybackUrlResponse) {
  const declared = playback.mimeType?.split(';')[0].trim().toLowerCase()
  const extension = playback.fileName.split('.').pop()?.toLowerCase()
  const candidates = [
    declared && declared !== 'application/octet-stream'
      ? (MIME_TYPE_ALIASES[declared] || declared)
      : undefined,
    extension ? EXTENSION_MIME_TYPES[extension] : undefined,
  ]
  return [...new Set(candidates.filter((candidate): candidate is string => Boolean(candidate)))]
}

function canPlay(audio: HTMLAudioElement | null, playback: AudioPlaybackUrlResponse) {
  if (!audio) return false
  return getMimeTypes(playback).some((mimeType) => (
    SUPPORTED_MIME_TYPES.has(mimeType) && audio.canPlayType(mimeType) !== ''
  ))
}

function safeMediaDuration(audio: HTMLAudioElement) {
  return Number.isFinite(audio.duration) && audio.duration > 0 ? audio.duration : 0
}

function friendlyError(error: unknown, reason: PlaybackRequestReason) {
  if (error instanceof ApiError && error.code === AUDIO_FILE_NOT_AVAILABLE_CODE) {
    return '当前音频暂时无法播放。'
  }
  return reason === 'expiry'
    ? '播放地址已失效，请重新加载。'
    : '音频加载失败，请稍后重试。'
}

export function useAudioPlayback(
  fileId?: string,
  reportDurationMs?: number | null,
): AudioPlaybackController {
  const { settings } = useUserSettings()
  const audioRef = useRef<HTMLAudioElement | null>(null)
  const requestControllerRef = useRef<AbortController | null>(null)
  const playbackRef = useRef<AudioPlaybackUrlResponse | null>(null)
  const expiresAtMsRef = useRef(0)
  const pendingSeekRef = useRef<number | null>(null)
  const pendingPlayRef = useRef(false)
  const previewEndRef = useRef<number | null>(null)
  const playIntentRef = useRef(false)
  const autoRefreshUsedRef = useRef(false)
  const requestPlaybackRef = useRef<(options: PlaybackRequestOptions) => Promise<boolean>>(
    async () => false,
  )

  const [playback, setPlayback] = useState<AudioPlaybackUrlResponse | null>(null)
  const [loading, setLoading] = useState(Boolean(fileId))
  const [refreshing, setRefreshing] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [unsupported, setUnsupported] = useState(false)
  const [currentTimeSeconds, setCurrentTimeSeconds] = useState(0)
  const [durationSeconds, setDurationSeconds] = useState(0)
  const [isPlaying, setIsPlaying] = useState(false)
  const [isWaiting, setIsWaiting] = useState(false)
  const [playbackRange, setPlaybackRange] = useState<AudioPlaybackRange | null>(null)
  const [volume, setVolumeState] = useState(settings?.defaultPlaybackVolume ?? 1)
  const [muted, setMuted] = useState(false)
  const [playbackRate, setPlaybackRateState] = useState(1)

  const clearPlaybackRange = useCallback(() => {
    previewEndRef.current = null
    setPlaybackRange(null)
  }, [])

  const requestPlayback = useCallback(async (options: PlaybackRequestOptions) => {
    if (!fileId) {
      clearPlaybackRange()
      return false
    }

    requestControllerRef.current?.abort()
    const controller = new AbortController()
    requestControllerRef.current = controller
    if (options.reason === 'initial') setLoading(true)
    else setRefreshing(true)
    setError(null)

    try {
      const nextPlayback = await getAudioPlaybackUrl(fileId, controller.signal)
      if (controller.signal.aborted) return false

      const audio = audioRef.current
      if (!canPlay(audio, nextPlayback)) {
        clearPlaybackRange()
        playbackRef.current = nextPlayback
        setPlayback(nextPlayback)
        setUnsupported(true)
        setError('当前浏览器不支持直接播放该音频格式，可下载后使用本地播放器打开。')
        return false
      }

      const parsedExpiry = new Date(nextPlayback.expiresAt).getTime()
      expiresAtMsRef.current = Number.isFinite(parsedExpiry)
        ? parsedExpiry
        : Date.now() + nextPlayback.expiresInSeconds * 1000
      pendingSeekRef.current = options.positionSeconds ?? null
      pendingPlayRef.current = Boolean(options.resumePlayback)
      playbackRef.current = nextPlayback
      setPlayback(nextPlayback)
      setUnsupported(false)
      setError(null)
      return true
    } catch (requestError) {
      if (requestError instanceof DOMException && requestError.name === 'AbortError') return false
      console.error('[audio-playback] playback URL request failed', requestError)
      clearPlaybackRange()
      setError(friendlyError(requestError, options.reason))
      return false
    } finally {
      if (requestControllerRef.current === controller) {
        requestControllerRef.current = null
        setLoading(false)
        setRefreshing(false)
      }
    }
  }, [clearPlaybackRange, fileId])

  requestPlaybackRef.current = requestPlayback

  useEffect(() => {
    const configuredVolume = settings?.defaultPlaybackVolume
    if (typeof configuredVolume !== 'number' || !Number.isFinite(configuredVolume)) return
    const safeVolume = Math.min(1, Math.max(0, configuredVolume))
    setVolumeState(safeVolume)
    if (audioRef.current) audioRef.current.volume = safeVolume
  }, [settings?.defaultPlaybackVolume])

  useEffect(() => {
    if (audioRef.current) audioRef.current.volume = volume
  }, [playback?.fileId, playback?.playbackUrl, volume])

  const playElement = useCallback(async (audio: HTMLAudioElement) => {
    playIntentRef.current = true
    try {
      await audio.play()
    } catch (playError) {
      playIntentRef.current = false
      clearPlaybackRange()
      console.warn('[audio-playback] browser rejected play()', playError)
      setError('无法开始播放，请再次点击播放按钮重试。')
    }
  }, [clearPlaybackRange])

  useEffect(() => {
    autoRefreshUsedRef.current = false
    playbackRef.current = null
    expiresAtMsRef.current = 0
    pendingSeekRef.current = null
    pendingPlayRef.current = false
    clearPlaybackRange()
    playIntentRef.current = false
    setPlayback(null)
    setError(null)
    setUnsupported(false)
    setCurrentTimeSeconds(0)
    setDurationSeconds(0)
    setIsPlaying(false)
    setIsWaiting(false)

    if (!fileId) {
      setLoading(false)
      return undefined
    }

    void requestPlayback({ reason: 'initial' })
    return () => requestControllerRef.current?.abort()
  }, [clearPlaybackRange, fileId, requestPlayback])

  useEffect(() => {
    const audio = audioRef.current
    if (!audio) return undefined
    let animationFrame: number | undefined
    let lastRenderedTime = -1

    const stopProgressFrame = () => {
      if (animationFrame !== undefined) cancelAnimationFrame(animationFrame)
      animationFrame = undefined
    }
    const updateProgressFrame = () => {
      const nextTime = Number.isFinite(audio.currentTime) ? audio.currentTime : 0
      if (lastRenderedTime < 0 || Math.abs(nextTime - lastRenderedTime) >= 0.05) {
        lastRenderedTime = nextTime
        setCurrentTimeSeconds(nextTime)
      }
      animationFrame = requestAnimationFrame(updateProgressFrame)
    }
    const updateDuration = () => {
      const nextDuration = safeMediaDuration(audio)
      setDurationSeconds(nextDuration)
      if (
        import.meta.env.DEV
        && nextDuration > 0
        && typeof reportDurationMs === 'number'
        && reportDurationMs > 0
      ) {
        const differenceMs = Math.abs(nextDuration * 1000 - reportDurationMs)
        if (differenceMs > Math.max(1000, reportDurationMs * 0.02)) {
          console.warn('[audio-playback] media/report duration mismatch', {
            mediaDurationMs: Math.round(nextDuration * 1000),
            reportDurationMs,
          })
        }
      }
    }
    const handleLoadedMetadata = () => {
      updateDuration()
      const pendingSeek = pendingSeekRef.current
      if (pendingSeek !== null) {
        const nextTime = Math.min(safeMediaDuration(audio) || pendingSeek, Math.max(0, pendingSeek))
        audio.currentTime = nextTime
        setCurrentTimeSeconds(nextTime)
        pendingSeekRef.current = null
      }
      if (pendingPlayRef.current) {
        pendingPlayRef.current = false
        void playElement(audio)
      }
    }
    const handleTimeUpdate = () => {
      const nextTime = Number.isFinite(audio.currentTime) ? audio.currentTime : 0
      setCurrentTimeSeconds(nextTime)
      if (previewEndRef.current !== null && nextTime >= previewEndRef.current - 0.04) {
        clearPlaybackRange()
        playIntentRef.current = false
        audio.pause()
      }
    }
    const handlePlay = () => {
      setIsPlaying(true)
      setIsWaiting(false)
      stopProgressFrame()
      animationFrame = requestAnimationFrame(updateProgressFrame)
    }
    const handlePause = () => {
      setIsPlaying(false)
      setIsWaiting(false)
      stopProgressFrame()
    }
    const handleEnded = () => {
      playIntentRef.current = false
      clearPlaybackRange()
      setIsPlaying(false)
      stopProgressFrame()
    }
    const handleWaiting = () => setIsWaiting(true)
    const handleCanPlay = () => setIsWaiting(false)
    const handleError = () => {
      if (!audio.error || !playbackRef.current) return
      const positionSeconds = Number.isFinite(audio.currentTime) ? audio.currentTime : 0
      const resumePlayback = playIntentRef.current
      setIsPlaying(false)
      setIsWaiting(false)
      stopProgressFrame()

      if (!autoRefreshUsedRef.current) {
        autoRefreshUsedRef.current = true
        void requestPlaybackRef.current({
          reason: 'expiry',
          positionSeconds,
          resumePlayback,
        })
        return
      }
      playIntentRef.current = false
      clearPlaybackRange()
      setError('播放地址已失效，请重新加载。')
    }

    audio.addEventListener('loadedmetadata', handleLoadedMetadata)
    audio.addEventListener('durationchange', updateDuration)
    audio.addEventListener('timeupdate', handleTimeUpdate)
    audio.addEventListener('play', handlePlay)
    audio.addEventListener('pause', handlePause)
    audio.addEventListener('ended', handleEnded)
    audio.addEventListener('waiting', handleWaiting)
    audio.addEventListener('canplay', handleCanPlay)
    audio.addEventListener('error', handleError)

    return () => {
      stopProgressFrame()
      audio.removeEventListener('loadedmetadata', handleLoadedMetadata)
      audio.removeEventListener('durationchange', updateDuration)
      audio.removeEventListener('timeupdate', handleTimeUpdate)
      audio.removeEventListener('play', handlePlay)
      audio.removeEventListener('pause', handlePause)
      audio.removeEventListener('ended', handleEnded)
      audio.removeEventListener('waiting', handleWaiting)
      audio.removeEventListener('canplay', handleCanPlay)
      audio.removeEventListener('error', handleError)
    }
  }, [clearPlaybackRange, playElement, reportDurationMs])

  const isExpiring = () => (
    !playbackRef.current
    || expiresAtMsRef.current - Date.now() <= EXPIRY_REFRESH_THRESHOLD_MS
  )

  const seek = useCallback((seconds: number) => {
    if (!Number.isFinite(seconds)) return
    clearPlaybackRange()
    const audio = audioRef.current
    if (!audio) return
    const nextTime = Math.min(safeMediaDuration(audio) || seconds, Math.max(0, seconds))
    if (audio.readyState === 0) pendingSeekRef.current = nextTime
    else audio.currentTime = nextTime
    setCurrentTimeSeconds(nextTime)
  }, [clearPlaybackRange])

  const seekTo = useCallback(async (seconds: number, options: SeekOptions = {}) => {
    const audio = audioRef.current
    if (!audio || !Number.isFinite(seconds)) return
    const nextTime = Math.min(safeMediaDuration(audio) || seconds, Math.max(0, seconds))
    const rangeEndSeconds = options.play
      && typeof options.endSeconds === 'number'
      && Number.isFinite(options.endSeconds)
      && options.endSeconds > nextTime
      ? options.endSeconds
      : null
    previewEndRef.current = rangeEndSeconds
    setPlaybackRange(rangeEndSeconds === null ? null : {
      startSeconds: nextTime,
      endSeconds: rangeEndSeconds,
    })

    if (isExpiring()) {
      pendingSeekRef.current = nextTime
      pendingPlayRef.current = Boolean(options.play)
      await requestPlayback({
        reason: playbackRef.current ? 'expiry' : 'manual',
        positionSeconds: nextTime,
        resumePlayback: options.play,
      })
      return
    }

    if (audio.readyState === 0) {
      pendingSeekRef.current = nextTime
      pendingPlayRef.current = Boolean(options.play)
      setCurrentTimeSeconds(nextTime)
      return
    }

    audio.currentTime = nextTime
    setCurrentTimeSeconds(nextTime)
    if (options.play) await playElement(audio)
  }, [playElement, requestPlayback])

  const pause = useCallback(() => {
    const audio = audioRef.current
    pendingPlayRef.current = false
    playIntentRef.current = false
    if (audio && !audio.paused) audio.pause()
    setIsPlaying(false)
    setIsWaiting(false)
  }, [])

  const resume = useCallback(async () => {
    const audio = audioRef.current
    if (!audio) return
    if (previewEndRef.current !== null && audio.currentTime >= previewEndRef.current - 0.04) {
      clearPlaybackRange()
      return
    }
    if (isExpiring()) {
      await requestPlayback({
        reason: playbackRef.current ? 'expiry' : 'manual',
        positionSeconds: audio.currentTime,
        resumePlayback: true,
      })
      return
    }
    await playElement(audio)
  }, [clearPlaybackRange, playElement, requestPlayback])

  const togglePlayback = useCallback(async () => {
    const audio = audioRef.current
    if (!audio) return
    if (!audio.paused) {
      pause()
      return
    }
    await resume()
  }, [pause, resume])

  const setVolume = useCallback((nextVolume: number) => {
    if (!Number.isFinite(nextVolume)) return
    const safeVolume = Math.min(1, Math.max(0, nextVolume))
    if (audioRef.current) audioRef.current.volume = safeVolume
    setVolumeState(safeVolume)
    if (safeVolume > 0) {
      if (audioRef.current) audioRef.current.muted = false
      setMuted(false)
    }
  }, [])

  const toggleMuted = useCallback(() => {
    const audio = audioRef.current
    if (!audio) return
    audio.muted = !audio.muted
    setMuted(audio.muted)
  }, [])

  const setPlaybackRate = useCallback((rate: number) => {
    if (!Number.isFinite(rate)) return
    if (audioRef.current) audioRef.current.playbackRate = rate
    setPlaybackRateState(rate)
  }, [])

  const reload = useCallback(() => {
    autoRefreshUsedRef.current = false
    clearPlaybackRange()
    const audio = audioRef.current
    return requestPlayback({
      reason: 'manual',
      positionSeconds: audio?.currentTime || 0,
      resumePlayback: false,
    })
  }, [clearPlaybackRange, requestPlayback])

  return {
    audioRef,
    sourceUrl: unsupported ? undefined : playback?.playbackUrl,
    playback,
    loading,
    refreshing,
    error,
    unsupported,
    currentTimeSeconds,
    durationSeconds,
    isPlaying,
    isWaiting,
    playbackRange,
    volume,
    muted,
    playbackRate,
    pause,
    resume,
    togglePlayback,
    seek,
    seekTo,
    setVolume,
    toggleMuted,
    setPlaybackRate,
    reload,
  }
}
