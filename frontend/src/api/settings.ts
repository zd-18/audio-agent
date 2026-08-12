import type { CurrentUser } from './auth'
import { apiRequest } from './http'
import type {
  UpdateUserProfilePayload,
  UpdateUserSettingPayload,
  UserSetting,
} from '../types/settings'

export function getCurrentUserSetting(signal?: AbortSignal) {
  return apiRequest<UserSetting>('/api/settings/me', { signal })
}

export function updateCurrentUserSetting(payload: UpdateUserSettingPayload) {
  return apiRequest<UserSetting>('/api/settings/me', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export function updateCurrentUserProfile(payload: UpdateUserProfilePayload) {
  return apiRequest<CurrentUser>('/api/settings/profile', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}
