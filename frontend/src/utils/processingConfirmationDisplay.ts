import { ApiError } from '../api/http'
import type {
  ProcessingConfirmationStatus,
  ProcessingStepDecision,
} from '../types/processingConfirmation'

export const PROCESSING_CONFIRMATION_NOT_FOUND_CODE = 40213

export const CONFIRMATION_STATUS_META: Record<
  ProcessingConfirmationStatus,
  { label: string; description: string }
> = {
  DRAFT: {
    label: '确认草稿',
    description: '可以逐项接受、暂不处理或保留为待决定。',
  },
  CONFIRMED: {
    label: '处理方案已确认',
    description: '当前决定已经锁定，页面已切换为只读模式。',
  },
  STALE: {
    label: '方案已更新，当前草稿已失效',
    description: '该草稿基于旧版处理方案，不能继续编辑或提交。',
  },
  CANCELLED: {
    label: '确认草稿已取消',
    description: '已保存的决定不可继续编辑，原处理方案仍然保留。',
  },
}

export const STEP_DECISION_META: Record<
  ProcessingStepDecision,
  { label: string; shortLabel: string }
> = {
  ACCEPTED: { label: '接受建议', shortLabel: '已接受' },
  REJECTED: { label: '暂不处理', shortLabel: '已拒绝' },
  PENDING: { label: '待决定', shortLabel: '待决定' },
}

const ERROR_MESSAGES: Record<number, string> = {
  40213: '当前尚未创建确认草稿。',
  40214: '当前确认单不可编辑，请刷新页面查看最新状态。',
  40215: '当前草稿已经失效，请基于最新处理方案重新创建。',
  40216: '还有未决定的步骤，请逐项保存决定后再提交。',
  40217: '存在需要明确试听确认的步骤，请完成勾选并保存。',
  40218: '参数不符合要求，请检查标出的字段后重试。',
  40219: '最终方案已经提交，无需重复操作。',
  40220: '确认草稿已经取消，不能继续编辑。',
  40915: '暂时无法获取当前音频的处理信息，请返回上一页重新进入后重试。',
}

export function getProcessingConfirmationErrorMessage(
  error: unknown,
  fallback = '确认操作失败，请稍后重试。',
) {
  if (error instanceof ApiError) {
    if (error.code !== undefined && ERROR_MESSAGES[error.code]) {
      return ERROR_MESSAGES[error.code]
    }
    return /[\u3400-\u9fff]/.test(error.message) ? error.message : fallback
  }
  return error instanceof Error && /[\u3400-\u9fff]/.test(error.message) ? error.message : fallback
}
