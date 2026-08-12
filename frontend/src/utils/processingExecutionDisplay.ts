import type {
  ProcessingExecutionStage,
  ProcessingExecutionStatus,
  ProcessingExecutionStepStatus,
} from '../types/processingExecution'
import type { ProcessingOperationType } from '../types/processingPlan'
import { ApiError } from '../api/http'

export const PROCESSING_EXECUTION_NOT_FOUND_CODE = 40221
export const PROCESSING_EXECUTION_ALREADY_EXISTS_CODE = 40224

export const EXECUTION_STATUS_META: Record<
  ProcessingExecutionStatus,
  { label: string; description: string; tone: string }
> = {
  PENDING: { label: '待处理', description: '执行任务已登记，正在准备进入处理队列。', tone: 'pending' },
  QUEUED: { label: '待处理（已排队）', description: '任务已进入队列，将按顺序开始处理。', tone: 'queued' },
  PROCESSING: { label: '正在处理', description: '正在按照已确认的步骤生成新的结果音频。', tone: 'processing' },
  SUCCESS: { label: '处理完成', description: '结果音频已经生成，可以试听或下载。', tone: 'success' },
  FAILED: { label: '处理失败', description: '本次处理未能完成，可以查看原因并重新尝试。', tone: 'failed' },
  CANCELLED: { label: '处理已取消', description: '任务已停止，原始音频未受到影响。', tone: 'cancelled' },
  DEAD_LETTER: { label: '多次尝试后仍未完成', description: '自动尝试已经停止，可以手动重新处理。', tone: 'failed' },
}

export const STAGE_LABELS: Record<ProcessingExecutionStage, string> = {
  PREPARING: '正在准备音频',
  LOCAL_PROCESSING: '正在处理局部音量和噪声',
  TRIMMING: '正在整理静音片段',
  LOUDNESS_NORMALIZING: '正在统一整体响度',
  PEAK_LIMITING: '正在控制音频峰值',
  UPLOADING: '正在保存处理结果',
  METADATA_EXTRACTING: '正在读取结果信息',
  COMPLETED: '处理已完成',
}

export const STEP_STATUS_META: Record<
  ProcessingExecutionStepStatus,
  { label: string; tone: string }
> = {
  PENDING: { label: '等待处理', tone: 'pending' },
  PROCESSING: { label: '正在处理', tone: 'processing' },
  SUCCESS: { label: '处理完成', tone: 'success' },
  FAILED: { label: '处理失败', tone: 'failed' },
  SKIPPED: { label: '已跳过', tone: 'skipped' },
}

export const OPERATION_LABELS: Record<ProcessingOperationType, string> = {
  NORMALIZE_VOLUME: '整段音量标准化',
  TRIM_SEGMENT: '裁剪指定片段',
  REVIEW_SILENCE: '检查静音片段',
  TRIM_SILENCE: '缩短较长静音',
  INCREASE_GAIN: '提升局部音量',
  DECREASE_GAIN: '降低突发音量',
  DENOISE_REVIEW: '处理疑似背景噪声',
  NORMALIZE_LOUDNESS: '统一整体响度',
  LIMIT_PEAK: '控制过高峰值',
}

const BUSINESS_ERROR_MESSAGES: Record<number, string> = {
  40000: '请求参数无效，请返回上一页重新操作。',
  40005: '无权访问该音频处理任务。',
  40101: '未找到对应的分析任务。',
  40221: '未找到对应的音频处理任务。',
  40222: '处理方案尚未完成最终确认。',
  40223: '未接受任何处理建议，无需创建处理任务。',
  40224: '该确认方案已经存在处理任务。',
  40225: '当前任务状态不允许重新处理，请先刷新页面。',
  40226: '未找到原始音频文件。',
  40227: '确认方案中包含暂不支持的处理操作。',
  40228: '处理参数无效，请重新生成处理方案。',
  40229: '音频处理未能完成。',
  40230: '生成的音频结果不可用。',
  40231: '处理结果保存失败。',
  40232: '音频处理失败，请稍后重试。',
}

const FAILURE_MESSAGES: Record<string, string> = {
  PROCESSING_EXECUTION_SOURCE_FILE_NOT_FOUND: '未找到原始音频文件。',
  PROCESSING_EXECUTION_UNSUPPORTED_OPERATION: '确认方案中包含暂不支持的处理操作。',
  PROCESSING_EXECUTION_INVALID_PARAMETER: '处理参数无效，请重新生成处理方案。',
  PROCESSING_EXECUTION_FFMPEG_FAILED: '音频处理未能完成。',
  PROCESSING_EXECUTION_OUTPUT_INVALID: '生成的音频结果不可用。',
  PROCESSING_EXECUTION_UPLOAD_FAILED: '处理结果保存失败。',
  PROCESSING_EXECUTION_FAILED: '音频处理失败，请稍后重试。',
}

export function getStageLabel(stage: ProcessingExecutionStage | null) {
  return stage ? STAGE_LABELS[stage] || '正在处理音频' : '正在处理音频'
}

export function getExecutionProgress(status: ProcessingExecutionStatus, value: number) {
  if (status === 'SUCCESS') return 100
  if (!Number.isFinite(value)) return 0
  return Math.min(100, Math.max(0, value))
}

export function getSkipReason(reason: string | null) {
  return reason === 'REVIEW_ONLY_OPERATION'
    ? '该建议只需要人工检查，无需自动处理。'
    : '该步骤未执行。'
}

export function getExecutionFailureMessage(code: string | null) {
  return code ? FAILURE_MESSAGES[code] || '音频处理失败，请稍后重试。' : '音频处理失败，请稍后重试。'
}

export function getProcessingExecutionErrorMessage(error: unknown) {
  if (error instanceof ApiError && error.code) {
    return BUSINESS_ERROR_MESSAGES[error.code] || '请求失败，请稍后重试。'
  }
  return error instanceof Error ? error.message : '请求失败，请稍后重试。'
}

export function isActiveExecutionStatus(status: ProcessingExecutionStatus) {
  return status === 'PENDING' || status === 'QUEUED' || status === 'PROCESSING'
}
