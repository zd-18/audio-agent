import { LockOutlined } from '@ant-design/icons'
import { Alert, Form, Input, Modal } from 'antd'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError } from '../../api/http'
import { useAuth } from '../../auth/AuthContext'

interface ChangePasswordModalProps {
  open: boolean
  onClose: () => void
}

interface PasswordValues {
  oldPassword: string
  newPassword: string
  confirmPassword: string
}

export default function ChangePasswordModal({ open, onClose }: ChangePasswordModalProps) {
  const navigate = useNavigate()
  const { changePassword } = useAuth()
  const [form] = Form.useForm<PasswordValues>()
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const submit = async (values: PasswordValues) => {
    if (submitting) return
    setSubmitting(true)
    setError(null)
    try {
      await changePassword(values.oldPassword, values.newPassword)
      form.resetFields()
      onClose()
      navigate('/login', {
        replace: true,
        state: { notice: '密码已修改，请使用新密码重新登录' },
      })
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : '密码修改失败，请稍后重试')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal
      title="修改密码"
      open={open}
      okText="确认修改"
      cancelText="取消"
      confirmLoading={submitting}
      onOk={() => form.submit()}
      onCancel={() => {
        if (submitting) return
        form.resetFields()
        setError(null)
        onClose()
      }}
    >
      {error && <Alert className="workbench-password-error" type="error" showIcon message={error} role="alert" />}
      <Form form={form} layout="vertical" requiredMark={false} onFinish={submit}>
        <Form.Item label="旧密码" name="oldPassword" rules={[{ required: true, message: '请输入旧密码' }]}>
          <Input.Password autoComplete="current-password" prefix={<LockOutlined />} />
        </Form.Item>
        <Form.Item label="新密码" name="newPassword" rules={[{ required: true, message: '请输入新密码' }, { min: 8, max: 64, message: '密码长度必须为 8～64 个字符' }]}>
          <Input.Password autoComplete="new-password" prefix={<LockOutlined />} />
        </Form.Item>
        <Form.Item
          label="确认新密码"
          name="confirmPassword"
          dependencies={['newPassword']}
          rules={[
            { required: true, message: '请再次输入新密码' },
            ({ getFieldValue }) => ({
              validator: (_, value) => !value || value === getFieldValue('newPassword')
                ? Promise.resolve()
                : Promise.reject(new Error('两次输入的新密码不一致')),
            }),
          ]}
        >
          <Input.Password autoComplete="new-password" prefix={<LockOutlined />} />
        </Form.Item>
      </Form>
      <p className="workbench-password-hint">修改成功后，当前及其他已登录会话都会失效。</p>
    </Modal>
  )
}
