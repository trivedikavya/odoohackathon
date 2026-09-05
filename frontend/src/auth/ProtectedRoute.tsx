import { Navigate, Outlet, useLocation } from 'react-router-dom'
import type { Role } from '@/api/types'
import { useAuth } from './AuthContext'
import { PageLoader } from '@/components/ui/PageLoader'

/**
 * Client-side route guard.
 *
 * This is a convenience for the user, not a security boundary - the server
 * enforces the same rules with @PreAuthorize, so bypassing this component
 * gains an attacker nothing.
 */
export function ProtectedRoute({ roles }: { roles?: Role[] }) {
  const { user, loading } = useAuth()
  const location = useLocation()

  if (loading) return <PageLoader />

  if (!user) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />
  }

  if (roles && !roles.includes(user.role)) {
    return <Navigate to="/" replace />
  }

  return <Outlet />
}
