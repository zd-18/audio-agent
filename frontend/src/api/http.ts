import type { ApiResponse } from '../types/api'
import { getStoredToken } from '../auth/tokenStorage'

const SUCCESS_CODE = 0
const AUTH_REQUIRED_CODE = 40501
const AUTH_USER_DISABLED_CODE = 40504
const AUTH_USER_NOT_FOUND_CODE = 40507
const LONG_ID_PATTERN = /(\"(?:id|[A-Za-z_$][\w$]*Id)\"\s*:\s*)(-?\d+)/g
let unauthorizedHandler: (() => void) | null = null
let handlingUnauthorized = false

export class ApiError extends Error {
  readonly code?: number
  readonly requestId?: string
  readonly status?: number

  constructor(message: string, code?: number, requestId?: string, status?: number) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.requestId = requestId
    this.status = status
  }
}

export function setUnauthorizedHandler(handler: (() => void) | null) {
  unauthorizedHandler = handler
}

export function resetUnauthorizedHandling() {
  handlingUnauthorized = false
}

function notifyUnauthorized() {
  if (handlingUnauthorized) return
  handlingUnauthorized = true
  unauthorizedHandler?.()
}

function withAuthorization(headers?: HeadersInit) {
  const result = new Headers(headers)
  const token = getStoredToken()
  if (token && !result.has('Authorization')) {
    result.set('Authorization', `Bearer ${token}`)
  }
  return result
}

export function setXmlHttpRequestAuthHeader(request: XMLHttpRequest) {
  const token = getStoredToken()
  if (token) request.setRequestHeader('Authorization', `Bearer ${token}`)
}

export function handleUnauthorizedStatus(status: number) {
  if (status === 401) notifyUnauthorized()
}

export async function authorizedFetch(input: RequestInfo | URL, init: RequestInit = {}) {
  const response = await fetch(input, { ...init, headers: withAuthorization(init.headers) })
  handleUnauthorizedStatus(response.status)
  return response
}

export function isValidResourceId(value: string | null | undefined): value is string {
  if (!value || !/^\d+$/.test(value)) return false
  try {
    return BigInt(value) > 0n
  } catch {
    return false
  }
}

export function parseApiResponse<T>(source: string): ApiResponse<T> {
  try {
    return JSON.parse(source.replace(LONG_ID_PATTERN, '$1\"$2\"')) as ApiResponse<T>
  } catch {
    throw new ApiError('服务器返回了无法识别的响应')
  }
}

export function unwrapApiResponse<T>(response: ApiResponse<T>): T {
  if (response.code !== SUCCESS_CODE) {
    if ([AUTH_REQUIRED_CODE, AUTH_USER_DISABLED_CODE, AUTH_USER_NOT_FOUND_CODE].includes(response.code)) {
      notifyUnauthorized()
    }
    throw new ApiError(response.message || '请求失败，请稍后重试', response.code, response.requestId)
  }

  return response.data as T
}

export async function apiRequest<T>(url: string, init: RequestInit = {}) {
  let response: Response
  try {
    response = await authorizedFetch(url, init)
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new ApiError('网络连接失败，请确认服务已启动后重试')
  }

  const body = await response.text()
  const parsed = parseApiResponse<T>(body)
  if (response.status === 401 || [AUTH_REQUIRED_CODE, AUTH_USER_DISABLED_CODE, AUTH_USER_NOT_FOUND_CODE].includes(parsed.code)) {
    notifyUnauthorized()
  }
  if (!response.ok) {
    throw new ApiError(parsed.message || `请求失败（${response.status}）`, parsed.code, parsed.requestId, response.status)
  }
  return unwrapApiResponse(parsed)
}
