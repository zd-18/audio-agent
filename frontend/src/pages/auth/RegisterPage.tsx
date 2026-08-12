import { IdcardOutlined, LockOutlined, UserOutlined } from '@ant-design/icons'
import { Alert, Button, Form, Input } from 'antd'
import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { ApiError } from '../../api/http'
import { useAuth } from '../../auth/AuthContext'
import AuthFrame from './AuthFrame'

interface RegisterValues {
  username: string
  displayName: string
  password: string
  confirmPassword: string
}

export default function RegisterPage() {
  const { register } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const from = (location.state as { from?: string } | null)?.from
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const submit = async (values: RegisterValues) => {
    if (submitting) return
    setSubmitting(true)
    setError(null)
    try {
      await register({
        username: values.username.trim(),
        displayName: values.displayName.trim(),
        password: values.password,
      })
      const destination = from?.startsWith('/') && !from.startsWith('//')
        && from !== '/login' && from !== '/register' ? from : '/dashboard'
      navigate(destination, { replace: true })
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : '注册失败，请稍后重试')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthFrame eyebrow="CREATE WORKSPACE" title="创建 AudioAgent 账号" description="注册后将自动登录，并进入只属于你的个人工作区。">
      {error && <Alert className="auth-alert" type="error" showIcon message={error} role="alert" />}
      <Form<RegisterValues> className="auth-form" layout="vertical" requiredMark={false} onFinish={submit}>
        <div className="auth-form__row">
          <Form.Item label="用户名" name="username" rules={[{ required: true, whitespace: true, message: '请输入用户名' }, { max: 64, message: '用户名不能超过 64 个字符' }]}>
            <Input autoFocus autoComplete="username" size="large" prefix={<UserOutlined />} placeholder="用于登录" />
          </Form.Item>
          <Form.Item label="显示名称" name="displayName" rules={[{ required: true, whitespace: true, message: '请输入显示名称' }, { max: 100, message: '显示名称不能超过 100 个字符' }]}>
            <Input autoComplete="name" size="large" prefix={<IdcardOutlined />} placeholder="工作区展示名称" />
          </Form.Item>
        </div>
        <Form.Item
          label="密码"
          name="password"
          dependencies={['username']}
          rules={[
            { required: true, message: '请输入密码' },
            { min: 8, max: 64, message: '密码长度必须为 8～64 个字符' },
            ({ getFieldValue }) => ({ validator: (_, value) => {
              if (!value || (value.trim() && value !== getFieldValue('username')?.trim())) return Promise.resolve()
              return Promise.reject(new Error(value.trim() ? '密码不能与用户名相同' : '密码不能全为空格'))
            } }),
          ]}
        >
          <Input.Password autoComplete="new-password" size="large" prefix={<LockOutlined />} placeholder="8～64 个字符" />
        </Form.Item>
        <Form.Item
          label="确认密码"
          name="confirmPassword"
          dependencies={['password']}
          rules={[
            { required: true, message: '请再次输入密码' },
            ({ getFieldValue }) => ({ validator: (_, value) => !value || value === getFieldValue('password') ? Promise.resolve() : Promise.reject(new Error('两次输入的密码不一致')) }),
          ]}
        >
          <Input.Password autoComplete="new-password" size="large" prefix={<LockOutlined />} placeholder="再次输入密码" />
        </Form.Item>
        <p className="auth-password-hint">密码仅用于登录，不会以明文形式保存。</p>
        <Button className="auth-submit" type="primary" size="large" htmlType="submit" loading={submitting} disabled={submitting} block>
          {submitting ? '正在创建账号' : '创建并进入工作区'}
        </Button>
      </Form>
      <p className="auth-switch">已有账号？ <Link to="/login" state={{ from }}>直接登录</Link></p>
    </AuthFrame>
  )
}
