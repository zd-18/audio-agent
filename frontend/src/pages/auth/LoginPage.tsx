import { LockOutlined, UserOutlined } from '@ant-design/icons'
import { Alert, Button, Form, Input } from 'antd'
import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { ApiError } from '../../api/http'
import { useAuth } from '../../auth/AuthContext'
import AuthFrame from './AuthFrame'

interface LoginValues {
  username: string
  password: string
}

interface AuthLocationState {
  from?: string
  notice?: string
}

function safeDestination(from?: string) {
  return from?.startsWith('/') && !from.startsWith('//')
    && from !== '/login' && from !== '/register' ? from : '/dashboard'
}

export default function LoginPage() {
  const { login } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const [form] = Form.useForm<LoginValues>()
  const state = location.state as AuthLocationState | null
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const submit = async (values: LoginValues) => {
    if (submitting) return
    setSubmitting(true)
    setError(null)
    try {
      await login(values.username.trim(), values.password)
      navigate(safeDestination(state?.from), { replace: true })
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : '登录失败，请稍后重试')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthFrame eyebrow="WELCOME BACK" title="登录工作区" description="继续处理你的音频文件、分析任务与修复方案。">
      {state?.notice && <Alert className="auth-alert" type="success" showIcon message={state.notice} />}
      {error && <Alert className="auth-alert" type="error" showIcon message={error} role="alert" />}
      <Form<LoginValues> form={form} className="auth-form" layout="vertical" requiredMark={false} onFinish={submit}>
        <Form.Item label="用户名" name="username" rules={[{ required: true, whitespace: true, message: '请输入用户名' }, { max: 64, message: '用户名不能超过 64 个字符' }]}>
          <Input autoFocus autoComplete="username" size="large" prefix={<UserOutlined />} placeholder="输入你的用户名" />
        </Form.Item>
        <Form.Item label="密码" name="password" rules={[{ required: true, message: '请输入密码' }]}>
          <Input.Password
            autoComplete="current-password"
            size="large"
            prefix={<LockOutlined />}
            placeholder="输入你的密码"
            onPressEnter={(event) => {
              event.preventDefault()
              form.submit()
            }}
          />
        </Form.Item>
        <Button className="auth-submit" type="primary" size="large" htmlType="submit" loading={submitting} disabled={submitting} block>
          {submitting ? '正在安全登录' : '进入工作区'}
        </Button>
      </Form>
      <p className="auth-switch">还没有账号？ <Link to="/register" state={{ from: state?.from }}>创建账号</Link></p>
    </AuthFrame>
  )
}
