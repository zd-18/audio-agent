import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from './AuthContext'

function AuthStartup() {
  return (
    <main className="auth-startup" aria-busy="true" aria-label="正在恢复登录状态">
      <span className="auth-startup__mark" aria-hidden="true"><i /><i /><i /><i /><i /></span>
      <strong>正在启动 AudioAgent</strong>
      <small>正在安全恢复你的工作区</small>
    </main>
  )
}

export function ProtectedRoute() {
  const { authStatus } = useAuth()
  const location = useLocation()
  if (authStatus === 'initializing') return <AuthStartup />
  if (authStatus !== 'authenticated') {
    const from = `${location.pathname}${location.search}${location.hash}`
    return <Navigate to="/login" replace state={{ from }} />
  }
  return <Outlet />
}

export function PublicOnlyRoute() {
  const { authStatus } = useAuth()
  if (authStatus === 'initializing') return <AuthStartup />
  if (authStatus === 'authenticated') return <Navigate to="/dashboard" replace />
  return <Outlet />
}
