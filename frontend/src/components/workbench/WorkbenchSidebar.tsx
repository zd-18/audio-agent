import { LeftOutlined, RightOutlined } from '@ant-design/icons'
import { Button, Menu, Tooltip } from 'antd'
import { useLocation, useNavigate } from 'react-router-dom'
import { workbenchNavItems } from './navigation'

interface WorkbenchSidebarProps {
  collapsed: boolean
  onCollapse: () => void
  onNavigate?: () => void
}

function Brand({ collapsed }: { collapsed: boolean }) {
  return (
    <div className="workbench-brand" aria-label="AudioAgent">
      <span className="workbench-brand__mark" aria-hidden="true"><i /><i /><i /><i /></span>
      {!collapsed && <span className="workbench-brand__copy"><strong>AudioAgent</strong><small>智能音频工作台</small></span>}
    </div>
  )
}

export default function WorkbenchSidebar({ collapsed, onCollapse, onNavigate }: WorkbenchSidebarProps) {
  const location = useLocation()
  const navigate = useNavigate()
  const selectedKey = workbenchNavItems.find((item) => item.key !== '/' && location.pathname.startsWith(item.key))?.key || '/dashboard'

  return (
    <div className="workbench-sidebar__inner">
      <Brand collapsed={collapsed} />
      <nav aria-label="工作台主导航">
        <Menu
          mode="inline"
          inlineCollapsed={collapsed}
          selectedKeys={[selectedKey]}
          items={workbenchNavItems.map((item) => ({ ...item, title: item.label }))}
          onClick={({ key }) => {
            navigate(key)
            onNavigate?.()
          }}
        />
      </nav>
      <Tooltip title={collapsed ? '展开侧边栏' : '折叠侧边栏'} placement="right">
        <Button
          className="workbench-sidebar__collapse"
          type="text"
          icon={collapsed ? <RightOutlined /> : <LeftOutlined />}
          onClick={onCollapse}
          aria-label={collapsed ? '展开侧边栏' : '折叠侧边栏'}
        >
          {!collapsed && '折叠菜单'}
        </Button>
      </Tooltip>
    </div>
  )
}

