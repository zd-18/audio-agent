import type { ReactNode } from 'react'

interface AudioToolNodeProps {
  description: string
  color: 'purple' | 'blue' | 'cyan' | 'orange'
  icon: ReactNode
  label: string
  position: number
  size?: 'small' | 'medium' | 'large'
}

export default function AudioToolNode({
  description,
  color,
  icon,
  label,
  position,
  size = 'medium',
}: AudioToolNodeProps) {
  const tooltipId = `landing-tool-node-tooltip-${position}`

  return (
    <div
      className={`landing-tool-node landing-tool-node--${size} landing-tool-node--position-${position}`}
      data-scan-index={position}
      tabIndex={0}
      aria-label={`${label}：${description}`}
      aria-describedby={tooltipId}
      title={label}
    >
      <div className={`landing-tool-node__surface landing-tool-node__surface--${color}`}>
        <span className="landing-tool-node__icon" aria-hidden="true">{icon}</span>
        <span className="landing-tool-node__name">{label}</span>
        <span id={tooltipId} className="landing-tool-node__tooltip" role="tooltip">
          <strong>{label}</strong>
          <small>{description}</small>
        </span>
      </div>
    </div>
  )
}
