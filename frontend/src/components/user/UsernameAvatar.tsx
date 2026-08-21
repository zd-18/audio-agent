import { Avatar } from 'antd'
import type { ComponentProps } from 'react'
import { getUsernameInitial } from '../../utils/userAvatar'
import './username-avatar.css'

interface UsernameAvatarProps {
  username?: string | null
  size?: ComponentProps<typeof Avatar>['size']
  className?: string
}

export default function UsernameAvatar({ username, size, className }: UsernameAvatarProps) {
  const normalizedUsername = username?.trim() || '用户'

  return (
    <span
      className="username-avatar-label"
      role="img"
      aria-label={`${normalizedUsername}的默认头像`}
    >
      <Avatar
        className={`username-avatar${className ? ` ${className}` : ''}`}
        size={size}
        aria-hidden="true"
      >
        {getUsernameInitial(username)}
      </Avatar>
    </span>
  )
}
