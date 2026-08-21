import {
  BellOutlined,
  LockOutlined,
  LogoutOutlined,
  SaveOutlined,
  SettingOutlined,
} from '@ant-design/icons'
import {
  Alert,
  App,
  Button,
  Form,
  Input,
  Radio,
  Skeleton,
  Switch,
} from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { updateCurrentUserProfile } from '../../api/settings'
import { useAuth } from '../../auth/AuthContext'
import ChangePasswordModal from '../../components/settings/ChangePasswordModal'
import UsernameAvatar from '../../components/user/UsernameAvatar'
import PageContainer from '../../components/workbench/PageContainer'
import PageTitle from '../../components/workbench/PageTitle'
import { useUserSettings } from '../../settings/UserSettingsContext'
import type { UpdateUserSettingPayload, UserSetting } from '../../types/settings'
import './settings.css'

type ProcessingMode = 'LIGHT' | 'BALANCED' | 'DEEP'
type SettingsFormValues = {
  defaultProcessingMode: ProcessingMode
  notifyOnTaskComplete: boolean
}

const DEFAULT_PROCESSING_MODE: ProcessingMode = 'BALANCED'
const PROCESSING_MODE_OPTIONS = [
  { label: '轻度优化', value: 'LIGHT' },
  { label: '平衡优化', value: 'BALANCED' },
  { label: '深度优化', value: 'DEEP' },
]

function processingModeStorageKey(userId?: number | string) {
  return `audioagent:processing-mode:${userId ?? 'current'}`
}

function readProcessingMode(userId?: number | string): ProcessingMode {
  const stored = window.localStorage.getItem(processingModeStorageKey(userId))
  return stored === 'LIGHT' || stored === 'BALANCED' || stored === 'DEEP'
    ? stored
    : DEFAULT_PROCESSING_MODE
}

function applyProcessingMode(settings: UserSetting, mode: ProcessingMode): UserSetting {
  if (mode === 'LIGHT') {
    return {
      ...settings,
      defaultDenoiseStrength: 'LIGHT',
      processingStrategy: 'CONSERVATIVE',
      autoLimitPeak: false,
    }
  }
  if (mode === 'DEEP') {
    return {
      ...settings,
      defaultDenoiseStrength: 'MEDIUM',
      processingStrategy: 'BALANCED',
      autoLimitPeak: true,
    }
  }
  return {
    ...settings,
    defaultDenoiseStrength: 'LIGHT',
    processingStrategy: 'BALANCED',
    autoLimitPeak: true,
  }
}

function useUnsavedChanges(enabled: boolean) {
  useEffect(() => {
    if (!enabled) return undefined
    const warnBeforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault()
      event.returnValue = ''
    }
    const guardLinkNavigation = (event: MouseEvent) => {
      if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return
      const link = (event.target as HTMLElement | null)
        ?.closest('a[href]') as HTMLAnchorElement | null
      if (!link) return
      const destination = new URL(link.href, window.location.href)
      if (destination.href === window.location.href) return
      if (!window.confirm('当前页面有未保存的设置，确定要离开吗？')) {
        event.preventDefault()
        event.stopPropagation()
      }
    }
    window.addEventListener('beforeunload', warnBeforeUnload)
    document.addEventListener('click', guardLinkNavigation, true)
    return () => {
      window.removeEventListener('beforeunload', warnBeforeUnload)
      document.removeEventListener('click', guardLinkNavigation, true)
    }
  }, [enabled])
}

function SettingRow({
  title,
  children,
}: {
  title: string
  children: React.ReactNode
}) {
  return (
    <div className="settings-row">
      <div className="settings-row__copy">
        <strong>{title}</strong>
      </div>
      <div className="settings-row__control">{children}</div>
    </div>
  )
}

export default function SettingsPage() {
  const navigate = useNavigate()
  const { message, modal } = App.useApp()
  const { currentUser, updateCurrentUser, logout } = useAuth()
  const { settings, loading, saving, error, refresh, save } = useUserSettings()
  const [form] = Form.useForm<SettingsFormValues>()
  const watchedValues = Form.useWatch([], form) as SettingsFormValues | undefined
  const [savedProcessingMode, setSavedProcessingMode] = useState<ProcessingMode>(DEFAULT_PROCESSING_MODE)
  const [profileName, setProfileName] = useState(currentUser?.displayName || '')
  const [profileSaving, setProfileSaving] = useState(false)
  const [profileError, setProfileError] = useState<string | null>(null)
  const [passwordOpen, setPasswordOpen] = useState(false)

  useEffect(() => {
    if (!settings) return
    const mode = readProcessingMode(currentUser?.id)
    setSavedProcessingMode(mode)
    form.setFieldsValue({
      defaultProcessingMode: mode,
      notifyOnTaskComplete: settings.notifyOnTaskComplete,
    })
  }, [currentUser?.id, form, settings])

  useEffect(() => {
    setProfileName(currentUser?.displayName || '')
  }, [currentUser?.displayName, currentUser?.id])

  const preferencesDirty = useMemo(
    () => Boolean(settings && watchedValues && (
      watchedValues.defaultProcessingMode !== savedProcessingMode
      || watchedValues.notifyOnTaskComplete !== settings.notifyOnTaskComplete
    )),
    [savedProcessingMode, settings, watchedValues],
  )
  const normalizedProfileName = profileName.trim()
  const profileDirty = normalizedProfileName !== (currentUser?.displayName || '')
  useUnsavedChanges(preferencesDirty || profileDirty)

  const savePreferences = async () => {
    if (!settings) return
    try {
      const values = await form.validateFields()
      const payload: UpdateUserSettingPayload = {
        ...applyProcessingMode(settings, values.defaultProcessingMode),
        notifyOnTaskComplete: values.notifyOnTaskComplete,
      }
      const saved = await save(payload)
      window.localStorage.setItem(
        processingModeStorageKey(currentUser?.id),
        values.defaultProcessingMode,
      )
      setSavedProcessingMode(values.defaultProcessingMode)
      form.setFieldsValue({
        defaultProcessingMode: values.defaultProcessingMode,
        notifyOnTaskComplete: saved.notifyOnTaskComplete,
      })
      message.success('偏好设置已保存', 2)
    } catch (cause) {
      if (cause instanceof Error) message.error(cause.message)
    }
  }

  const saveProfile = async () => {
    if (profileSaving) return
    if (!normalizedProfileName) {
      setProfileError('显示名称不能为空或全为空格')
      return
    }
    if (normalizedProfileName.length > 50) {
      setProfileError('显示名称不能超过 50 个字符')
      return
    }
    setProfileSaving(true)
    setProfileError(null)
    try {
      const updated = await updateCurrentUserProfile({ displayName: normalizedProfileName })
      updateCurrentUser(updated)
      setProfileName(updated.displayName)
      message.success('显示名称已更新')
    } catch (cause) {
      setProfileError(cause instanceof Error ? cause.message : '显示名称保存失败，请稍后重试')
    } finally {
      setProfileSaving(false)
    }
  }

  const confirmLogout = () => {
    modal.confirm({
      title: '退出当前账号',
      content: preferencesDirty || profileDirty
        ? '当前页面还有未保存的修改。退出后这些修改会丢失。'
        : '确定要退出当前账号吗？',
      okText: '退出登录',
      cancelText: '继续使用',
      okButtonProps: { danger: true },
      centered: true,
      onOk: async () => {
        await logout()
        navigate('/login', { replace: true })
      },
    })
  }

  if (loading && !settings) {
    return (
      <PageContainer>
        <PageTitle title="系统设置" />
        <div className="settings-skeleton" aria-label="正在加载系统设置">
          {[0, 1, 2].map((item) => <section key={item}><Skeleton active paragraph={{ rows: 2 }} /></section>)}
        </div>
      </PageContainer>
    )
  }

  if (!settings) {
    return (
      <PageContainer>
        <PageTitle title="系统设置" />
        <Alert type="error" showIcon message="系统设置加载失败" description={error || '暂时无法读取当前账号的设置'} action={<Button onClick={refresh}>重新加载</Button>} />
      </PageContainer>
    )
  }

  return (
    <PageContainer>
      <PageTitle
        title="系统设置"
        actions={(
          <Button
            type="primary"
            size="large"
            icon={<SaveOutlined />}
            loading={saving}
            disabled={!preferencesDirty || saving}
            onClick={() => { void savePreferences() }}
          >
            保存偏好设置
          </Button>
        )}
      />

      {error && <Alert className="settings-page-alert" type="warning" showIcon message="最近一次设置请求未成功" description={error} action={<Button onClick={refresh}>重新加载</Button>} />}

      <div className="settings-grid">
        <section className="settings-card settings-card--account" aria-labelledby="settings-account-title">
          <header className="settings-card__header">
            <UsernameAvatar username={currentUser?.username} size={44} />
            <h2 id="settings-account-title">账号信息</h2>
          </header>
          <div className="settings-account-fields">
            <label>
              <span>用户名</span>
              <Input value={currentUser?.username || ''} readOnly aria-readonly="true" />
            </label>
            <label>
              <span>显示名称</span>
              <Input value={profileName} maxLength={50} showCount onChange={(event) => { setProfileName(event.target.value); setProfileError(null) }} />
            </label>
            {profileError && <Alert type="error" showIcon message={profileError} />}
            <div className="settings-account-actions">
              <Button type="primary" loading={profileSaving} disabled={!profileDirty || profileSaving} onClick={() => { void saveProfile() }}>保存显示名称</Button>
              <Button icon={<LockOutlined />} onClick={() => setPasswordOpen(true)}>修改密码</Button>
              <Button danger icon={<LogoutOutlined />} onClick={confirmLogout}>退出登录</Button>
            </div>
          </div>
        </section>

        <Form
          form={form}
          component={false}
          initialValues={{
            defaultProcessingMode: DEFAULT_PROCESSING_MODE,
            notifyOnTaskComplete: settings.notifyOnTaskComplete,
          }}
        >
          <section className="settings-card" aria-labelledby="settings-processing-title">
            <header className="settings-card__header">
              <span className="settings-card__icon is-purple"><SettingOutlined /></span>
              <h2 id="settings-processing-title">音频处理偏好</h2>
            </header>
            <SettingRow title="默认处理模式">
              <Form.Item name="defaultProcessingMode" noStyle rules={[{ required: true }]}>
                <Radio.Group
                  className="settings-mode-group"
                  buttonStyle="solid"
                  options={PROCESSING_MODE_OPTIONS}
                  optionType="button"
                  aria-label="默认处理模式"
                />
              </Form.Item>
            </SettingRow>
          </section>

          <section className="settings-card" aria-labelledby="settings-notification-title">
            <header className="settings-card__header">
              <span className="settings-card__icon is-blue"><BellOutlined /></span>
              <h2 id="settings-notification-title">通知设置</h2>
            </header>
            <SettingRow title="任务完成提醒">
              <Form.Item name="notifyOnTaskComplete" valuePropName="checked" noStyle><Switch aria-label="任务完成提醒" /></Form.Item>
            </SettingRow>
          </section>
        </Form>
      </div>

      <footer className="settings-save-bar" aria-live="polite">
        {preferencesDirty && <span>偏好设置有未保存修改</span>}
        <Button type="primary" icon={<SaveOutlined />} loading={saving} disabled={!preferencesDirty || saving} onClick={() => { void savePreferences() }}>保存偏好设置</Button>
      </footer>

      <ChangePasswordModal open={passwordOpen} onClose={() => setPasswordOpen(false)} />
    </PageContainer>
  )
}
