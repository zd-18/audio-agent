import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react'
import {
  getCurrentUserSetting,
  updateCurrentUserSetting,
} from '../api/settings'
import { useAuth } from '../auth/AuthContext'
import type { UpdateUserSettingPayload, UserSetting } from '../types/settings'

interface UserSettingsContextValue {
  settings: UserSetting | null
  loading: boolean
  saving: boolean
  error: string | null
  refresh: () => void
  save: (payload: UpdateUserSettingPayload) => Promise<UserSetting>
}

const UserSettingsContext = createContext<UserSettingsContextValue | null>(null)

export function UserSettingsProvider({ children }: { children: React.ReactNode }) {
  const { authStatus, currentUser } = useAuth()
  const [settings, setSettings] = useState<UserSetting | null>(null)
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [version, setVersion] = useState(0)

  const refresh = useCallback(() => setVersion((value) => value + 1), [])

  useEffect(() => {
    if (authStatus !== 'authenticated' || !currentUser) {
      setSettings(null)
      setLoading(false)
      setSaving(false)
      setError(null)
      return undefined
    }

    const controller = new AbortController()
    setLoading(true)
    setError(null)
    getCurrentUserSetting(controller.signal)
      .then((nextSettings) => {
        if (!controller.signal.aborted) setSettings(nextSettings)
      })
      .catch((requestError: unknown) => {
        if (requestError instanceof DOMException && requestError.name === 'AbortError') return
        if (!controller.signal.aborted) {
          setError(requestError instanceof Error
            ? requestError.message
            : '用户设置加载失败，请稍后重试')
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })

    return () => controller.abort()
  }, [authStatus, currentUser?.id, version])

  const save = useCallback(async (payload: UpdateUserSettingPayload) => {
    if (saving) throw new Error('用户设置正在保存，请稍候')
    setSaving(true)
    setError(null)
    try {
      const saved = await updateCurrentUserSetting(payload)
      setSettings(saved)
      return saved
    } catch (requestError) {
      setError(requestError instanceof Error
        ? requestError.message
        : '用户设置保存失败，请稍后重试')
      throw requestError
    } finally {
      setSaving(false)
    }
  }, [saving])

  const value = useMemo<UserSettingsContextValue>(() => ({
    settings,
    loading,
    saving,
    error,
    refresh,
    save,
  }), [error, loading, refresh, save, saving, settings])

  return (
    <UserSettingsContext.Provider value={value}>
      {children}
    </UserSettingsContext.Provider>
  )
}

export function useUserSettings() {
  const context = useContext(UserSettingsContext)
  if (!context) throw new Error('useUserSettings must be used inside UserSettingsProvider')
  return context
}
