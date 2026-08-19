import { ArrowRightOutlined } from '@ant-design/icons'
import { Link, type LinkProps } from 'react-router-dom'

interface AnimatedBorderButtonProps {
  children: string
  to?: string
  state?: LinkProps['state']
  href?: string
  variant?: 'primary' | 'secondary' | 'compact'
}

export default function AnimatedBorderButton({
  children,
  to,
  state,
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
        <Link className={className} to={to} state={state}>
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
