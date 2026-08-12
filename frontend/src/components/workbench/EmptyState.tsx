import { InboxOutlined } from '@ant-design/icons'
import type { ReactNode } from 'react'

interface EmptyStateProps {
  title: string
  description: string
  action?: ReactNode
}

export default function EmptyState({ title, description, action }: EmptyStateProps) {
  return (
    <div className="workbench-empty">
      <span className="workbench-empty__icon"><InboxOutlined /></span>
      <h3>{title}</h3>
      <p>{description}</p>
      {action}
    </div>
  )
}

