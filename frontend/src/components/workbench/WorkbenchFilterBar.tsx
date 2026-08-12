import type { FormEventHandler, ReactNode } from 'react'

type WorkbenchFilterLayout = 'audio-files' | 'analysis-tasks'

interface WorkbenchFilterBarProps {
  layout: WorkbenchFilterLayout
  children: ReactNode
  actions: ReactNode
  onSubmit: FormEventHandler<HTMLFormElement>
}

interface WorkbenchFilterFieldProps {
  label: ReactNode
  children: ReactNode
}

export function WorkbenchFilterField({ label, children }: WorkbenchFilterFieldProps) {
  return (
    <label className="workbench-filter-bar__field">
      <span className="workbench-filter-bar__label">{label}</span>
      {children}
    </label>
  )
}

export default function WorkbenchFilterBar({ layout, children, actions, onSubmit }: WorkbenchFilterBarProps) {
  return (
    <form className={`workbench-filter-bar workbench-filter-bar--${layout}`} onSubmit={onSubmit}>
      {children}
      <div className="workbench-filter-bar__actions">{actions}</div>
    </form>
  )
}
