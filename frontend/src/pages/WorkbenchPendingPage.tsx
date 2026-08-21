import { Button } from 'antd'
import { Link, useLocation } from 'react-router-dom'
import EmptyState from '../components/workbench/EmptyState'
import PageContainer from '../components/workbench/PageContainer'
import PageTitle from '../components/workbench/PageTitle'
import { routeMeta } from '../components/workbench/navigation'

export default function WorkbenchPendingPage() {
  const location = useLocation()
  const title = routeMeta[location.pathname]?.title || '功能页面'
  return (
    <PageContainer>
      <PageTitle eyebrow="COMING NEXT" title={title} />
      <section className="workbench-panel">
        <EmptyState
          title={`${title}将在后续迭代接入`}
          description="入口与工作台导航已经保留，当前没有新增虚构接口或覆盖既有后端协议。"
          action={<Link to="/dashboard"><Button type="primary">返回工作台</Button></Link>}
        />
      </section>
    </PageContainer>
  )
}
