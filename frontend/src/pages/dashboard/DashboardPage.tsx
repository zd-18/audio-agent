import {
  AudioOutlined,
  CheckCircleOutlined,
  CloudUploadOutlined,
  FileSearchOutlined,
  PlusCircleOutlined,
  FileTextOutlined,
  ReloadOutlined,
  SearchOutlined,
  SyncOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import { Button, Progress, Table, Tag } from 'antd'
import { Link } from 'react-router-dom'
import MetricCard from '../../components/workbench/MetricCard'
import PageContainer from '../../components/workbench/PageContainer'
import PageTitle from '../../components/workbench/PageTitle'
import QuickActionCard from '../../components/workbench/QuickActionCard'
import TaskStatusBadge from '../../components/workbench/TaskStatusBadge'
import { dashboardMetrics, recentAudioMock, recentTasksMock, taskStatusOverview } from '../../mocks/dashboardMockData'

const metricIcons = [<AudioOutlined />, <SyncOutlined />, <CheckCircleOutlined />, <WarningOutlined />]

export default function DashboardPage() {
  return (
    <PageContainer>
      <PageTitle
        eyebrow="WORKSPACE OVERVIEW"
        title="AudioAgent 工作台"
        description="管理音频文件、分析任务与处理结果。"
        actions={<><Link to="/audio/upload"><Button type="primary" icon={<CloudUploadOutlined />}>上传新音频</Button></Link><Link to="/analysis/tasks"><Button>查看分析任务</Button></Link></>}
      />

      <div className="workbench-demo-notice"><Tag color="purple">演示数据</Tag><span>当前后端暂无列表与统计接口，以下数据仅用于展示工作台布局。</span></div>

      <section className="audio-dashboard-metrics" aria-label="统计概览">
        {dashboardMetrics.map((item, index) => <MetricCard key={item.label} {...item} icon={metricIcons[index]} />)}
      </section>

      <section className="workbench-section">
        <div className="workbench-section__heading"><div><span>QUICK ACTIONS</span><h3>快捷操作</h3></div></div>
        <div className="audio-dashboard-actions">
          <QuickActionCard title="上传音频" description="选择本地音频文件" icon={<CloudUploadOutlined />} to="/audio/upload" />
          <QuickActionCard title="查询文件" description="按文件 ID 查看信息" icon={<FileSearchOutlined />} to="/audio/files" />
          <QuickActionCard title="创建分析任务" description="为已上传文件发起分析" icon={<PlusCircleOutlined />} to="/analysis/tasks?action=create" />
          <QuickActionCard title="查询任务" description="查看任务处理进度" icon={<SearchOutlined />} to="/analysis/tasks" />
          <QuickActionCard title="音频转写" description="生成文字稿与时间片段" icon={<FileTextOutlined />} to="/transcriptions" />
          <QuickActionCard title="查看失败任务" description="定位错误并人工重试" icon={<ReloadOutlined />} to="/analysis/tasks?status=FAILED" />
        </div>
      </section>

      <div className="audio-dashboard-grid">
        <section className="workbench-panel audio-dashboard-table-panel">
          <div className="workbench-panel__heading"><div><span>RECENT AUDIO</span><h3>最近上传音频</h3></div><Link to="/audio/files">查看全部</Link></div>
          <Table
            dataSource={recentAudioMock}
            pagination={false}
            scroll={{ x: 580 }}
            columns={[
              { title: '文件名', dataIndex: 'name', ellipsis: true },
              { title: '格式', dataIndex: 'format', width: 76 },
              { title: '大小', dataIndex: 'size', width: 100 },
              { title: '时长', dataIndex: 'duration', width: 88 },
              { title: '上传时间', dataIndex: 'createdAt', width: 112 },
            ]}
          />
        </section>

        <section className="workbench-panel audio-dashboard-status-panel">
          <div className="workbench-panel__heading"><div><span>TASK STATUS</span><h3>任务状态概览</h3></div></div>
          <div className="audio-dashboard-status-list">
            {taskStatusOverview.map((item) => (
              <div key={item.status}><TaskStatusBadge status={item.status} /><strong>{item.value}</strong><Progress percent={Math.round((item.value / 24) * 100)} showInfo={false} size="small" /></div>
            ))}
          </div>
        </section>
      </div>

      <section className="workbench-panel audio-dashboard-table-panel">
        <div className="workbench-panel__heading"><div><span>RECENT TASKS</span><h3>最近分析任务</h3></div><Link to="/analysis/tasks">查看全部</Link></div>
        <Table
          dataSource={recentTasksMock}
          pagination={false}
          scroll={{ x: 720 }}
          columns={[
            { title: '任务 ID', dataIndex: 'id', width: 120 },
            { title: '音频文件', dataIndex: 'file', ellipsis: true },
            { title: '状态', dataIndex: 'status', width: 110, render: (status) => <TaskStatusBadge status={status} /> },
            { title: '进度', dataIndex: 'progress', width: 150, render: (value) => <Progress percent={value} size="small" /> },
            { title: '创建时间', dataIndex: 'createdAt', width: 120 },
          ]}
        />
      </section>
    </PageContainer>
  )
}
