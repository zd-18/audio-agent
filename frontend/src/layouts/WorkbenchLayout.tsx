import { ConfigProvider, Drawer, Layout, theme } from 'antd'
import { useEffect, useState } from 'react'
import { Outlet } from 'react-router-dom'
import WorkbenchHeader from '../components/workbench/WorkbenchHeader'
import WorkbenchSidebar from '../components/workbench/WorkbenchSidebar'
import '../styles/workbench.css'
import '../styles/resource-details.css'

const { Sider, Content } = Layout

function useMobileQuery() {
  const [mobile, setMobile] = useState(() => window.matchMedia('(max-width: 767px)').matches)

  useEffect(() => {
    const query = window.matchMedia('(max-width: 767px)')
    const update = (event: MediaQueryListEvent) => setMobile(event.matches)
    query.addEventListener('change', update)
    return () => query.removeEventListener('change', update)
  }, [])

  return mobile
}

export default function WorkbenchLayout() {
  const [collapsed, setCollapsed] = useState(() => window.innerWidth <= 1100)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const mobile = useMobileQuery()

  return (
    <ConfigProvider
      theme={{
        algorithm: theme.darkAlgorithm,
        token: {
          colorPrimary: '#9b6dff',
          colorInfo: '#5b8cff',
          colorSuccess: '#42dcc7',
          colorWarning: '#f3b969',
          colorError: '#ff6b81',
          colorBgBase: '#070b19',
          colorTextBase: '#f3f5ff',
          borderRadius: 12,
        },
        components: {
          Menu: { itemBg: 'transparent', darkItemBg: 'transparent', itemBorderRadius: 10 },
          Layout: { bodyBg: 'transparent', siderBg: 'transparent', headerBg: 'transparent' },
          Table: { headerBg: 'rgba(255,255,255,.035)', rowHoverBg: 'rgba(155,109,255,.07)' },
        },
      }}
    >
      <Layout className="workbench-shell">
        <a className="workbench-skip-link" href="#workbench-main">跳到主要内容</a>
        {!mobile && (
          <Sider className="workbench-sidebar" width={248} collapsedWidth={80} collapsed={collapsed} trigger={null}>
            <WorkbenchSidebar collapsed={collapsed} onCollapse={() => setCollapsed((value) => !value)} />
          </Sider>
        )}
        <Drawer
          className="workbench-drawer"
          width={280}
          placement="left"
          open={mobile && drawerOpen}
          onClose={() => setDrawerOpen(false)}
          closable={false}
          styles={{ body: { padding: 0 } }}
        >
          <WorkbenchSidebar collapsed={false} onCollapse={() => setDrawerOpen(false)} onNavigate={() => setDrawerOpen(false)} />
        </Drawer>
        <Layout className="workbench-main-layout">
          <WorkbenchHeader onOpenMenu={() => setDrawerOpen(true)} />
          <Content className="workbench-content"><Outlet /></Content>
        </Layout>
      </Layout>
    </ConfigProvider>
  )
}
