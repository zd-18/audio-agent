import type { ReactNode } from 'react'

interface PageTitleProps {
  title: string
  description?: string
  eyebrow?: string
  actions?: ReactNode
}

export default function PageTitle({ title, description, eyebrow, actions }: PageTitleProps) {
  return (
    <section className={`workbench-page-title${description ? '' : ' workbench-page-title--compact'}`}>
      <div>
        {eyebrow && <span>{eyebrow}</span>}
        <h2>{title}</h2>
        {description && <p>{description}</p>}
      </div>
      {actions && <div className="workbench-page-title__actions">{actions}</div>}
    </section>
  )
}
