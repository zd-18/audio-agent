import type { ReactNode } from 'react'

interface MetricCardProps {
  label: string
  value: number
  hint: string
  icon: ReactNode
  tone: 'purple' | 'blue' | 'cyan' | 'red'
}

export default function MetricCard({ label, value, hint, icon, tone }: MetricCardProps) {
  return (
    <article className={`audio-dashboard-metric audio-dashboard-metric--${tone}`}>
      <div className="audio-dashboard-metric__icon">{icon}</div>
      <div><span>{label}</span><strong>{value}</strong><small>{hint}</small></div>
    </article>
  )
}

