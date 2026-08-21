import { CloseOutlined, LogoutOutlined, MenuOutlined } from '@ant-design/icons'
import { Dropdown } from 'antd'
import type { MenuProps } from 'antd'
import { useState, type KeyboardEvent, type MouseEvent } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'
import type { LandingSectionId } from '../../pages/landing/LandingPage'
import AnimatedBorderButton from './AnimatedBorderButton'
import UsernameAvatar from '../user/UsernameAvatar'

const navItems: Array<{ label: string; id: LandingSectionId }> = [
  { label: '首页', id: 'home' },
  { label: '核心能力', id: 'capabilities' },
  { label: '处理流程', id: 'workflow' },
  { label: '适用场景', id: 'scenarios' },
  { label: '产品路线', id: 'roadmap' },
]

interface LandingHeaderProps {
  activeSection: LandingSectionId
  isScrolled: boolean
  onNavigate: (id: LandingSectionId) => void
}

export default function LandingHeader({ activeSection, isScrolled, onNavigate }: LandingHeaderProps) {
  const [isMenuOpen, setIsMenuOpen] = useState(false)
  const { authStatus, currentUser, logout } = useAuth()
  const isAuthenticated = authStatus === 'authenticated' && currentUser !== null
  const userName = currentUser?.displayName || currentUser?.username || '当前用户'
  const userMenuItems: MenuProps['items'] = [
    { key: 'logout', icon: <LogoutOutlined />, label: '退出登录', danger: true },
  ]

  const handleUserMenu: MenuProps['onClick'] = ({ key }) => {
    if (key === 'logout') {
      void (async () => {
        try {
          await logout()
        } catch {
          // AuthContext clears the local session in finally even if the request fails.
        }
      })()
    }
  }

  const handleNavClick = (event: MouseEvent<HTMLAnchorElement>, id: LandingSectionId) => {
    event.preventDefault()
    onNavigate(id)
    setIsMenuOpen(false)
  }

  const handleBrandKeyDown = (event: KeyboardEvent<HTMLAnchorElement>) => {
    if (event.key !== ' ') return

    event.preventDefault()
    onNavigate('home')
    setIsMenuOpen(false)
  }

  return (
    <header className={`landing-header${isScrolled ? ' landing-header--scrolled' : ''}`}>
      <div className="landing-header__inner">
        <a
          className="landing-brand"
          href="#home"
          aria-label="返回首页"
          onClick={(event) => handleNavClick(event, 'home')}
          onKeyDown={handleBrandKeyDown}
        >
          <span className="landing-brand__mark" aria-hidden="true">
            <i />
            <i />
            <i />
            <i />
          </span>
          <span>AudioAgent</span>
        </a>

        <nav className={`landing-nav${isMenuOpen ? ' landing-nav--open' : ''}`} aria-label="首页导航">
          {navItems.map(({ label, id }) => (
            <a
              key={id}
              href={`#${id}`}
              className={activeSection === id ? 'landing-nav__link--active' : undefined}
              aria-current={activeSection === id ? 'location' : undefined}
              onClick={(event) => handleNavClick(event, id)}
            >
              {label}
            </a>
          ))}
        </nav>

        <div className="landing-header__actions">
          {authStatus === 'anonymous' && (
            <Link className="landing-login landing-login--anonymous" to="/login">
              登录
            </Link>
          )}
          {isAuthenticated && (
            <Dropdown
              menu={{ items: userMenuItems, onClick: handleUserMenu }}
              placement="bottomRight"
              trigger={['click']}
            >
              <button
                className="landing-login landing-user-trigger"
                type="button"
                aria-label={`打开${userName}的用户菜单`}
              >
                <UsernameAvatar username={currentUser.username} size={28} />
                <span className="landing-user-trigger__name">{userName}</span>
              </button>
            </Dropdown>
          )}
          {isAuthenticated && (
            <AnimatedBorderButton to="/dashboard" variant="compact">
              进入工作台
            </AnimatedBorderButton>
          )}
        </div>

        <button
          className="landing-header__menu-button"
          type="button"
          aria-label={isMenuOpen ? '关闭导航菜单' : '打开导航菜单'}
          aria-expanded={isMenuOpen}
          onClick={() => setIsMenuOpen((open) => !open)}
        >
          {isMenuOpen ? <CloseOutlined /> : <MenuOutlined />}
        </button>
      </div>
    </header>
  )
}
