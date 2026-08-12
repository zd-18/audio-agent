import { apiRequest } from './http'

export interface CurrentUser {
  id: string
  username: string
  displayName: string
  avatarUrl: string | null
}

export interface LoginResult {
  token: string
  tokenName: string
  user: CurrentUser
}

export interface RegisterPayload {
  username: string
  password: string
  displayName: string
}

export function loginRequest(username: string, password: string) {
  return apiRequest<LoginResult>('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
}

export function registerRequest(payload: RegisterPayload) {
  return apiRequest<CurrentUser>('/api/auth/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export function getCurrentUserRequest() {
  return apiRequest<CurrentUser>('/api/auth/me')
}

export function logoutRequest() {
  return apiRequest<void>('/api/auth/logout', { method: 'POST' })
}

export function changePasswordRequest(oldPassword: string, newPassword: string) {
  return apiRequest<void>('/api/auth/password', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ oldPassword, newPassword }),
  })
}
