import { CheckCircleOutlined, ClockCircleOutlined, CloseCircleOutlined, SyncOutlined } from '@ant-design/icons'
import TaskStatusBadge from '../workbench/TaskStatusBadge'
import type { AnalysisTaskRecord } from '../../types/api'

const content = {
  PENDING: { title: '等待调度', description: '任务已进入异步队列，正在等待执行节点领取。', icon: <ClockCircleOutlined /> },
  PROCESSING: { title: '正在分析', description: '执行节点正在提取音频元数据并写入分析结果。', icon: <SyncOutlined /> },
  SUCCESS: { title: '分析完成', description: 'ffprobe 元数据已经生成，可以查看完整结果。', icon: <CheckCircleOutlined /> },
  FAILED: { title: '分析失败', description: '处理未能完成，请检查错误信息或发起人工重试。', icon: <CloseCircleOutlined /> },
}

export default function TaskOverviewCard({ task }: { task: AnalysisTaskRecord }) {
  const state = content[task.status]
  return (
    <section className={`analysis-overview analysis-overview--${task.status.toLowerCase()}`}>
      <div className="analysis-overview__icon">{state.icon}</div>
      <div><TaskStatusBadge status={task.status} /><h3>{state.title}</h3><p>{state.description}</p></div>
      <div className="analysis-overview__progress"><strong>{task.progress ?? 0}%</strong><span>当前进度</span></div>
    </section>
  )
}
