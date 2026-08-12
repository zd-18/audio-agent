import { Skeleton } from 'antd'

export default function ProcessingPlanSkeleton() {
  return (
    <div className="processing-plan-skeleton" aria-label="正在加载处理方案">
      <section><Skeleton active title={{ width: '35%' }} paragraph={{ rows: 4 }} /></section>
      <section><Skeleton active title={{ width: '28%' }} paragraph={{ rows: 3 }} /></section>
      <div className="processing-plan-skeleton__steps">
        <Skeleton active title={{ width: '45%' }} paragraph={{ rows: 6 }} />
        <Skeleton active title={{ width: '55%' }} paragraph={{ rows: 9 }} />
      </div>
    </div>
  )
}
