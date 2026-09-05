import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { tokenStore, UNAUTHORIZED_EVENT } from '@/api/client'
import { authApi } from '@/api/endpoints'
import type { AccessLevel, RegisterRequest, UserProfile } from '@/api/types'
import { AuthContext, type AuthContextValue } from './AuthContext'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserProfile | null>(null)
  const [loading, setLoading] = useState(true)

  // Restore the session on load: a stored token that is still valid means
  // the server can tell us who we are.
  useEffect(() => {
    let cancelled = false
    if (!tokenStore.get()) {
      setLoading(false)
      return
    }
    authApi
      .me()
      .then((profile) => { if (!cancelled) setUser(profile) })
      .catch(() => { tokenStore.clear(); if (!cancelled) setUser(null) })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [])

  useEffect(() => {
    const onUnauthorized = () => setUser(null)
    window.addEventListener(UNAUTHORIZED_EVENT, onUnauthorized)
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, onUnauthorized)
  }, [])

  const login = useCallback(async (identifier: string, password: string) => {
    const result = await authApi.login(identifier, password)
    tokenStore.set(result.token)
    setUser(result.user)
    return result.user
  }, [])

  const register = useCallback(async (body: RegisterRequest) => {
    const result = await authApi.register(body)
    tokenStore.set(result.token)
    setUser(result.user)
    return result.user
  }, [])

  const logout = useCallback(() => {
    tokenStore.clear()
    setUser(null)
  }, [])

  const hasAccess = useCallback(
    (...levels: AccessLevel[]) => (user ? levels.includes(user.accessLevel) : false),
    [user],
  )

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      loading,
      login,
      register,
      logout,
      hasAccess,
      isAdmin: user?.accessLevel === 'ADMIN',
      keepsBooks: user?.bookId != null,
    }),
    [user, loading, login, register, logout, hasAccess],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
