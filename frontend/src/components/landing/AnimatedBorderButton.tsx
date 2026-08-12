import { ArrowRightOutlined } from '@ant-design/icons'
import { Link } from 'react-router-dom'

interface AnimatedBorderButtonProps {
  children: string
  to?: string
  href?: string
  variant?: 'primary' | 'secondary' | 'compact'
}

export default function AnimatedBorderButton({
  children,
  to,
  href,
  variant = 'primary',
}: AnimatedBorderButtonProps) {
  const className = `landing-action landing-action--${variant}`
  const content = (
    <>
      <span>{children}</span>
      {variant !== 'secondary' && <ArrowRightOutlined className="landing-action__icon" aria-hidden="true" />}
    </>
  )

  return (
    <span className={`landing-action-border landing-action-border--${variant}`}>
      {to ? (
        <Link className={className} to={to}>
          {content}
        </Link>
      ) : (
        <a className={className} href={href}>
          {content}
        </a>
      )}
    </span>
  )
}
