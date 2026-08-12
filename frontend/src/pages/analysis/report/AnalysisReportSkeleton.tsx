import { Skeleton } from 'antd'

export default function AnalysisReportSkeleton() {
  return (
    <div className="report-skeleton" aria-label="正在加载音频分析报告">
      <section className="report-skeleton__hero">
        <Skeleton.Avatar active size={180} shape="circle" />
        <div><Skeleton active title={{ width: '42%' }} paragraph={{ rows: 3, width: ['78%', '96%', '64%'] }} /></div>
      </section>
      <section className="report-skeleton__overview">
        <div><Skeleton active title={{ width: '36%' }} paragraph={{ rows: 5 }} /></div>
        <div><Skeleton active title={{ width: '36%' }} paragraph={{ rows: 5 }} /></div>
      </section>
      <section className="report-skeleton__strip"><Skeleton active title={false} paragraph={{ rows: 2 }} /></section>
      <section className="report-skeleton__timeline"><Skeleton active title={{ width: '28%' }} paragraph={{ rows: 7 }} /></section>
      <section className="report-skeleton__recommendations"><Skeleton active title={{ width: '24%' }} paragraph={{ rows: 4 }} /></section>
    </div>
  )
}
