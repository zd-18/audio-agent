import type { AudioVersion, AudioVersionChain } from '../types/audioVersion'
import { apiRequest, isValidResourceId } from './http'

interface AudioVersionPayload extends Omit<AudioVersion, 'parentAudioFileId' | 'versionSummary' | 'fileName' | 'extension' | 'mimeType' | 'sizeBytes' | 'durationMs' | 'createdAt'> {
  parentAudioFileId?: string | null
  versionSummary?: string | null
  fileName?: string | null
  extension?: string | null
  mimeType?: string | null
  sizeBytes?: number | null
  durationMs?: number | null
  createdAt?: string | null
}

interface AudioVersionChainPayload {
  versions?: AudioVersionPayload[] | null
}

function normalizeVersion(version: AudioVersionPayload): AudioVersion {
  return {
    ...version,
    parentAudioFileId: version.parentAudioFileId ?? null,
    versionSummary: version.versionSummary ?? null,
    fileName: version.fileName ?? null,
    extension: version.extension ?? null,
    mimeType: version.mimeType ?? null,
    sizeBytes: version.sizeBytes ?? null,
    durationMs: version.durationMs ?? null,
    createdAt: version.createdAt ?? null,
  }
}

export async function getAudioVersionsByTask(
  taskId: string,
  signal?: AbortSignal,
): Promise<AudioVersionChain> {
  if (!isValidResourceId(taskId)) throw new Error('分析任务无效')
  const payload = await apiRequest<AudioVersionChainPayload>(
    `/api/audio-analysis/tasks/${encodeURIComponent(taskId)}/audio-versions`,
    { signal },
  )
  return { versions: (payload.versions ?? []).map(normalizeVersion) }
}
