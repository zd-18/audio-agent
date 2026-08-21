import { LockOutlined, LogoutOutlined, MenuOutlined, ProfileOutlined } from '@ant-design/icons'
import { Breadcrumb, Button, Descriptions, Dropdown, Modal, Space } from 'antd'
import type { MenuProps } from 'antd'
import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'
import ChangePasswordModal from '../settings/ChangePasswordModal'
import UsernameAvatar from '../user/UsernameAvatar'
import { getRouteMeta } from './navigation'

interface WorkbenchHeaderProps {
  onOpenMenu: () => void
}

export default function WorkbenchHeader({ onOpenMenu }: WorkbenchHeaderProps) {
  const location = useLocation()
  const navigate = useNavigate()
  const { currentUser, logout } = useAuth()
  const [accountOpen, setAccountOpen] = useState(false)
  const [passwordOpen, setPasswordOpen] = useState(false)
  const meta = getRouteMeta(location.pathname)
  const userItems: MenuProps['items'] = [
    { key: 'account', icon: <ProfileOutlined />, label: '账号信息' },
    { key: 'password', icon: <LockOutlined />, label: '修改密码' },
    { type: 'divider' },
    { key: 'logout', icon: <LogoutOutlined />, label: '退出登录', danger: true },
  ]

  const handleUserMenu: MenuProps['onClick'] = ({ key }) => {
    if (key === 'account') setAccountOpen(true)
    if (key === 'password') setPasswordOpen(true)
    if (key === 'logout') {
      void logout().finally(() => navigate('/login', { replace: true }))
    }
  }

  return (
    <header className="workbench-header">
      <Button className="workbench-header__menu" type="text" icon={<MenuOutlined />} onClick={onOpenMenu} aria-label="打开导航菜单" />
      <div className="workbench-header__context">
        <Breadcrumb items={meta.breadcrumb.map((title) => ({ title }))} />
        <h1>{meta.title}</h1>
      </div>
      <Dropdown
        menu={{
          items: userItems,
          onClick: handleUserMenu,
        }}
        placement="bottomRight"
        trigger={['click']}
      >
        <Button className="workbench-user" type="text" aria-label="打开用户菜单">
          <Space>
            <UsernameAvatar username={currentUser?.username} size={32} />
            <span className="workbench-user__copy"><strong>{currentUser?.displayName || currentUser?.username}</strong><small>个人工作区</small></span>
          </Space>
        </Button>
      </Dropdown>

      <Modal title="账号信息" open={accountOpen} onCancel={() => setAccountOpen(false)} footer={<Button onClick={() => setAccountOpen(false)}>关闭</Button>}>
        <Descriptions column={1} size="small" bordered>
          <Descriptions.Item label="显示名称">{currentUser?.displayName}</Descriptions.Item>
          <Descriptions.Item label="用户名">{currentUser?.username}</Descriptions.Item>
          <Descriptions.Item label="工作区">个人工作区</Descriptions.Item>
        </Descriptions>
      </Modal>

      <ChangePasswordModal open={passwordOpen} onClose={() => setPasswordOpen(false)} />
    </header>
  )
}
