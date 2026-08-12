import { ArrowRightOutlined } from '@ant-design/icons'
import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'

interface QuickActionCardProps {
  title: string
  description: string
  icon: ReactNode
  to: string
}

export default function QuickActionCard({ title, description, icon, to }: QuickActionCardProps) {
  return (
    <Link className="audio-dashboard-action" to={to}>
      <span className="audio-dashboard-action__icon">{icon}</span>
      <span><strong>{title}</strong><small>{description}</small></span>
      <ArrowRightOutlined className="audio-dashboard-action__arrow" />
    </Link>
  )
}

