import { createContext, useContext } from 'react'
import type { AccessLevel, UserProfile } from '@/api/types'

export interface AuthContextValue {
  user: UserProfile | null
  loading: boolean
  /** Resolves with the profile so callers can route on role immediately. */
  login: (identifier: string, password: string) => Promise<UserProfile>
  register: (body: import('@/api/types').RegisterRequest) => Promise<UserProfile>
  logout: () => void
  hasAccess: (...levels: AccessLevel[]) => boolean
  isAdmin: boolean
  /** True for staff of a seller or vendor; false for customers. */
  keepsBooks: boolean
}

export const AuthContext = createContext<AuthContextValue | undefined>(undefined)

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>')
  return ctx
}
