export type DenoiseStrength = 'LIGHT' | 'MEDIUM'
export type ProcessingStrategy = 'CONSERVATIVE' | 'BALANCED'

export interface UserSetting {
  defaultDenoiseStrength: DenoiseStrength
  processingStrategy: ProcessingStrategy
  autoLimitPeak: boolean
  requireStepConfirmation: boolean
  preservePlaybackPosition: boolean
  issueContextSeconds: number
  defaultPlaybackVolume: number
  defaultPageSize: 10 | 20 | 50
  notifyOnTaskComplete: boolean
  autoOpenResultPage: boolean
}

export type UpdateUserSettingPayload = UserSetting

export interface UpdateUserProfilePayload {
  displayName: string
}
