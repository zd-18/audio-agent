export type UserTaskStatus = 'PROCESSING' | 'WAITING_FOR_USER' | 'COMPLETED' | 'FAILED'

export type UserTaskStageStatus =
  | 'PENDING'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'FAILED'
  | 'ACTION_REQUIRED'

export interface UserTaskStage {
  code: string
  label: string
  status: UserTaskStageStatus
  progressPercent: number
  description: string
}

export interface UserTaskAction {
  type: string
  label: string
  path: string
  primary: boolean
}

export interface UserTaskProgress {
  taskId: string
  audioFileId: string
  fileName: string
  status: UserTaskStatus
  statusLabel: string
  currentStage: string
  currentStageLabel: string
  progressPercent: number
  currentActivity: string
  requiresUserAction: boolean
  failureReason: string | null
  stages: UserTaskStage[]
  nextActions: UserTaskAction[]
  completedOperations: string[]
  resultPath: string | null
  createdAt: string | null
  completedAt: string | null
}
