import { Navigate, Outlet, useLocation } from 'react-router-dom'
import type { AccessLevel } from '@/api/types'
import { useAuth } from './AuthContext'
import { landingFor } from './landing'
import { PageLoader } from '@/components/ui/PageLoader'

/**
 * Client-side route guard - a convenience, not a security boundary. The
 * server enforces the same rules independently, so bypassing this
 * component gains an attacker nothing.
 */
export function ProtectedRoute({ levels }: { levels?: AccessLevel[] }) {
  const { user, loading } = useAuth()
  const location = useLocation()

  if (loading) return <PageLoader />
  if (!user) return <Navigate to="/login" state={{ from: location.pathname }} replace />

  if (levels && !levels.includes(user.accessLevel)) {
    return <Navigate to={landingFor(user.accessLevel)} replace />
  }
  return <Outlet />
}
