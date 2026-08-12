import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import {
  changePasswordRequest,
  getCurrentUserRequest,
  loginRequest,
  logoutRequest,
  registerRequest,
} from '../api/auth'
import type { CurrentUser, RegisterPayload } from '../api/auth'
import { resetUnauthorizedHandling, setUnauthorizedHandler } from '../api/http'
import { clearStoredToken, getStoredToken, storeToken } from './tokenStorage'

export type AuthStatus = 'initializing' | 'authenticated' | 'anonymous'

interface AuthContextValue {
  token: string | null
  currentUser: CurrentUser | null
  authStatus: AuthStatus
  login: (username: string, password: string) => Promise<void>
  register: (payload: RegisterPayload) => Promise<void>
  logout: () => Promise<void>
  changePassword: (oldPassword: string, newPassword: string) => Promise<void>
  loadCurrentUser: () => Promise<void>
  updateCurrentUser: (user: CurrentUser) => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [token, setToken] = useState<string | null>(() => getStoredToken())
  const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null)
  const [authStatus, setAuthStatus] = useState<AuthStatus>('initializing')

  const clearSession = useCallback(() => {
    clearStoredToken()
    setToken(null)
    setCurrentUser(null)
    setAuthStatus('anonymous')
  }, [])

  const loadCurrentUser = useCallback(async () => {
    if (!getStoredToken()) {
      setAuthStatus('anonymous')
      setCurrentUser(null)
      return
    }
    try {
      const user = await getCurrentUserRequest()
      setCurrentUser(user)
      setToken(getStoredToken())
      setAuthStatus('authenticated')
    } catch {
      clearSession()
    }
  }, [clearSession])

  useEffect(() => {
    setUnauthorizedHandler(clearSession)
    void loadCurrentUser()
    return () => setUnauthorizedHandler(null)
  }, [clearSession, loadCurrentUser])

  const login = useCallback(async (username: string, password: string) => {
    const result = await loginRequest(username, password)
    storeToken(result.token)
    resetUnauthorizedHandling()
    setToken(result.token)
    setCurrentUser(result.user)
    setAuthStatus('authenticated')
  }, [])

  const register = useCallback(async (payload: RegisterPayload) => {
    await registerRequest(payload)
    await login(payload.username, payload.password)
  }, [login])

  const logout = useCallback(async () => {
    try {
      await logoutRequest()
    } finally {
      clearSession()
    }
  }, [clearSession])

  const changePassword = useCallback(async (oldPassword: string, newPassword: string) => {
    await changePasswordRequest(oldPassword, newPassword)
    clearSession()
  }, [clearSession])

  const updateCurrentUser = useCallback((user: CurrentUser) => {
    setCurrentUser(user)
  }, [])

  const value = useMemo<AuthContextValue>(() => ({
    token,
    currentUser,
    authStatus,
    login,
    register,
    logout,
    changePassword,
    loadCurrentUser,
    updateCurrentUser,
  }), [authStatus, changePassword, currentUser, loadCurrentUser, login, logout, register, token, updateCurrentUser])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside AuthProvider')
  return context
}
