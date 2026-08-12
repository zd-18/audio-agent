import { Skeleton } from 'antd'

export default function ProcessingExecutionSkeleton() {
  return (
    <div className="processing-execution-skeleton" aria-label="正在加载音频处理任务">
      <section><Skeleton active title paragraph={{ rows: 2 }} /></section>
      <section><Skeleton active title paragraph={{ rows: 3 }} /></section>
      <section><Skeleton active title paragraph={{ rows: 5 }} /></section>
      <section><Skeleton active title paragraph={{ rows: 3 }} /></section>
    </div>
  )
}
