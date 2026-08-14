import type { PageResult } from '../types/api'
import type { UserTaskProgress } from '../types/userTask'
import { apiRequest, isValidResourceId } from './http'

export function getUserTasks(current = 1, size = 100, signal?: AbortSignal) {
  const query = new URLSearchParams({ current: String(current), size: String(size) })
  return apiRequest<PageResult<UserTaskProgress>>(`/api/user-tasks?${query}`, { signal })
}

export function getUserTask(taskId: string, signal?: AbortSignal) {
  if (!isValidResourceId(taskId)) throw new Error('任务 ID 无效')
  return apiRequest<UserTaskProgress>(
    `/api/user-tasks/${encodeURIComponent(taskId)}`,
    { signal },
  )
}
