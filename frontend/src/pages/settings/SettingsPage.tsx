import {
  AudioOutlined,
  BellOutlined,
  LockOutlined,
  LogoutOutlined,
  SaveOutlined,
  SettingOutlined,
  SoundOutlined,
  UserOutlined,
} from '@ant-design/icons'
import {
  Alert,
  App,
  Button,
  Form,
  Input,
  InputNumber,
  Radio,
  Select,
  Skeleton,
  Slider,
  Switch,
} from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { updateCurrentUserProfile } from '../../api/settings'
import { useAuth } from '../../auth/AuthContext'
import ChangePasswordModal from '../../components/settings/ChangePasswordModal'
import PageContainer from '../../components/workbench/PageContainer'
import PageTitle from '../../components/workbench/PageTitle'
import { useUserSettings } from '../../settings/UserSettingsContext'
import type { UpdateUserSettingPayload, UserSetting } from '../../types/settings'
import './settings.css'

const PAGE_SIZE_OPTIONS = [10, 20, 50].map((value) => ({ value, label: `${value} 条 / 页` }))

function sameSettings(left: UserSetting | null, right: UpdateUserSettingPayload | null) {
  return Boolean(left && right && JSON.stringify(left) === JSON.stringify(right))
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
  description,
  children,
}: {
  title: string
  description: string
  children: React.ReactNode
}) {
  return (
    <div className="settings-row">
      <div className="settings-row__copy">
        <strong>{title}</strong>
        <span>{description}</span>
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
  const [form] = Form.useForm<UpdateUserSettingPayload>()
  const watchedValues = Form.useWatch([], form) as UpdateUserSettingPayload | undefined
  const [profileName, setProfileName] = useState(currentUser?.displayName || '')
  const [profileSaving, setProfileSaving] = useState(false)
  const [profileError, setProfileError] = useState<string | null>(null)
  const [passwordOpen, setPasswordOpen] = useState(false)

  useEffect(() => {
    if (settings) form.setFieldsValue(settings)
  }, [form, settings])

  useEffect(() => {
    setProfileName(currentUser?.displayName || '')
  }, [currentUser?.displayName, currentUser?.id])

  const preferencesDirty = useMemo(
    () => Boolean(watchedValues && !sameSettings(settings, watchedValues)),
    [settings, watchedValues],
  )
  const normalizedProfileName = profileName.trim()
  const profileDirty = normalizedProfileName !== (currentUser?.displayName || '')
  useUnsavedChanges(preferencesDirty || profileDirty)

  const savePreferences = async () => {
    try {
      const values = await form.validateFields()
      const saved = await save(values)
      form.setFieldsValue(saved)
      message.success('系统设置已保存并立即生效')
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
        <PageTitle eyebrow="PERSONAL PREFERENCES" title="系统设置" description="管理当前账号的音频处理、播放和工作台偏好。" />
        <div className="settings-skeleton" aria-label="正在加载系统设置">
          {[0, 1, 2, 3].map((item) => <section key={item}><Skeleton active paragraph={{ rows: 3 }} /></section>)}
        </div>
      </PageContainer>
    )
  }

  if (!settings) {
    return (
      <PageContainer>
        <PageTitle eyebrow="PERSONAL PREFERENCES" title="系统设置" description="管理当前账号的音频处理、播放和工作台偏好。" />
        <Alert type="error" showIcon message="系统设置加载失败" description={error || '暂时无法读取当前账号的设置'} action={<Button onClick={refresh}>重新加载</Button>} />
      </PageContainer>
    )
  }

  return (
    <PageContainer>
      <PageTitle
        eyebrow="PERSONAL PREFERENCES"
        title="系统设置"
        description="所有偏好仅作用于当前账号；保存后无需重新登录即可生效。"
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
            <span className="settings-card__icon"><UserOutlined /></span>
            <div><small>ACCOUNT</small><h2 id="settings-account-title">账号信息</h2><p>管理公开显示信息与登录安全。</p></div>
          </header>
          <div className="settings-account-fields">
            <label>
              <span>用户名</span>
              <Input value={currentUser?.username || ''} readOnly aria-readonly="true" />
              <small>用户名用于登录，当前不可修改。</small>
            </label>
            <label>
              <span>显示名称</span>
              <Input value={profileName} maxLength={50} showCount onChange={(event) => { setProfileName(event.target.value); setProfileError(null) }} />
              <small>保存后工作台右上角会立即更新。</small>
            </label>
            {profileError && <Alert type="error" showIcon message={profileError} />}
            <div className="settings-account-actions">
              <Button type="primary" loading={profileSaving} disabled={!profileDirty || profileSaving} onClick={() => { void saveProfile() }}>保存显示名称</Button>
              <Button icon={<LockOutlined />} onClick={() => setPasswordOpen(true)}>修改密码</Button>
              <Button danger icon={<LogoutOutlined />} onClick={confirmLogout}>退出登录</Button>
            </div>
          </div>
        </section>

        <Form form={form} component={false} initialValues={settings}>
          <section className="settings-card" aria-labelledby="settings-processing-title">
            <header className="settings-card__header">
              <span className="settings-card__icon is-purple"><SettingOutlined /></span>
              <div><small>PROCESSING</small><h2 id="settings-processing-title">音频处理偏好</h2><p>这些选项只影响之后新生成的处理方案。</p></div>
            </header>
            <SettingRow title="默认降噪强度" description="噪声规则未明确要求更强处理时采用。">
              <Form.Item name="defaultDenoiseStrength" noStyle rules={[{ required: true }]}>
                <Radio.Group buttonStyle="solid" options={[{ label: '轻度', value: 'LIGHT' }, { label: '中度', value: 'MEDIUM' }]} optionType="button" />
              </Form.Item>
            </SettingRow>
            <SettingRow title="处理策略" description="保守策略优先保留原始特征；均衡策略在证据充分时允许中度处理。">
              <Form.Item name="processingStrategy" noStyle rules={[{ required: true }]}>
                <Radio.Group buttonStyle="solid" options={[{ label: '保守', value: 'CONSERVATIVE' }, { label: '均衡', value: 'BALANCED' }]} optionType="button" />
              </Form.Item>
            </SettingRow>
            <SettingRow title="自动限制峰值" description="检测到峰值风险时默认加入 LIMIT_PEAK 建议，仍需人工确认。">
              <Form.Item name="autoLimitPeak" valuePropName="checked" noStyle><Switch aria-label="自动限制峰值" /></Form.Item>
            </SettingRow>
            <SettingRow title="默认展开步骤确认" description="关闭后方案页不自动展开首个步骤；最终确认永远不会被跳过。">
              <Form.Item name="requireStepConfirmation" valuePropName="checked" noStyle><Switch aria-label="默认展开步骤确认" /></Form.Item>
            </SettingRow>
          </section>

          <section className="settings-card" aria-labelledby="settings-playback-title">
            <header className="settings-card__header">
              <span className="settings-card__icon is-cyan"><SoundOutlined /></span>
              <div><small>PLAYBACK</small><h2 id="settings-playback-title">播放与试听</h2><p>控制 AudioAgent 内的播放器，不影响其他网站。</p></div>
            </header>
            <SettingRow title="保持播放位置" description="在原音频和处理结果之间切换时保持同一时间点。">
              <Form.Item name="preservePlaybackPosition" valuePropName="checked" noStyle><Switch aria-label="切换音频时保持播放位置" /></Form.Item>
            </SettingRow>
            <SettingRow title="问题片段上下文" description="试听时在问题片段前后额外播放的秒数。">
              <Form.Item name="issueContextSeconds" noStyle rules={[{ required: true }, { type: 'number', min: 0, max: 10, message: '请输入 0～10 秒' }]}>
                <InputNumber min={0} max={10} precision={0} addonAfter="秒" aria-label="问题片段上下文秒数" />
              </Form.Item>
            </SettingRow>
            <SettingRow title="默认播放音量" description="新播放器初始化时使用，可在播放器内临时调整。">
              <Form.Item name="defaultPlaybackVolume" noStyle rules={[{ required: true }]}>
                <Slider min={0} max={1} step={0.05} tooltip={{ formatter: (value) => `${Math.round((value || 0) * 100)}%` }} aria-label="默认播放音量" />
              </Form.Item>
            </SettingRow>
          </section>

          <section className="settings-card" aria-labelledby="settings-workbench-title">
            <header className="settings-card__header">
              <span className="settings-card__icon is-blue"><BellOutlined /></span>
              <div><small>WORKBENCH</small><h2 id="settings-workbench-title">工作台偏好</h2><p>调整列表密度、完成提醒和结果定位方式。</p></div>
            </header>
            <SettingRow title="默认分页数量" description="仅决定文件、分析任务和处理任务列表的初始每页数量。">
              <Form.Item name="defaultPageSize" noStyle rules={[{ required: true }]}>
                <Select options={PAGE_SIZE_OPTIONS} aria-label="默认分页数量" />
              </Form.Item>
            </SettingRow>
            <SettingRow title="任务完成提示" description="仅在本次页面会话观察到任务进入成功或失败时提示一次。">
              <Form.Item name="notifyOnTaskComplete" valuePropName="checked" noStyle><Switch aria-label="任务完成提示" /></Form.Item>
            </SettingRow>
            <SettingRow title="成功后定位结果" description="停留在处理进度页时，成功后自动滚动到结果区域。">
              <Form.Item name="autoOpenResultPage" valuePropName="checked" noStyle><Switch aria-label="处理成功后自动定位结果" /></Form.Item>
            </SettingRow>
          </section>
        </Form>
      </div>

      <footer className="settings-save-bar" aria-live="polite">
        <div><AudioOutlined /><span>{preferencesDirty ? '偏好设置有未保存修改' : '偏好设置已与服务器同步'}</span></div>
        <Button type="primary" icon={<SaveOutlined />} loading={saving} disabled={!preferencesDirty || saving} onClick={() => { void savePreferences() }}>保存偏好设置</Button>
      </footer>

      <ChangePasswordModal open={passwordOpen} onClose={() => setPasswordOpen(false)} />
    </PageContainer>
  )
}
