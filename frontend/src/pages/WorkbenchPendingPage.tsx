import { Button } from 'antd'
import { Link, useLocation } from 'react-router-dom'
import EmptyState from '../components/workbench/EmptyState'
import PageContainer from '../components/workbench/PageContainer'
import PageTitle from '../components/workbench/PageTitle'
import { routeMeta } from '../components/workbench/navigation'

const descriptions: Record<string, string> = {
  '/audio/files': '文件查询功能将在下一轮接入统一工作台，本轮未改写现有业务范围。',
  '/analysis/tasks': '任务创建、查询与人工重试将在下一轮接入，本轮保留路由入口。',
  '/agent': 'Agent 对话页面暂未开发，本轮不接入 Agent 后端能力。',
  '/settings': '系统设置页面暂未开发。',
}

export default function WorkbenchPendingPage() {
  const location = useLocation()
  const title = routeMeta[location.pathname]?.title || '功能页面'
  return (
    <PageContainer>
      <PageTitle eyebrow="COMING NEXT" title={title} description={descriptions[location.pathname]} />
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

