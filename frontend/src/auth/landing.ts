import type { AccessLevel } from '@/api/types'

/**
 * Where a user belongs after signing in.
 *
 * A customer has no back office at all - the server rejects every
 * non-portal path for them - so sending them to the staff dashboard would
 * render a page made entirely of 403s.
 */
export function landingFor(level: AccessLevel | undefined): string {
  return level === 'USER' ? '/portal' : '/'
}

export function isPortalPath(path: string | undefined): boolean {
  return !!path && path.startsWith('/portal')
}

/**
 * Only honours the page the user was trying to reach when it belongs to
 * the area their role can actually use.
 */
export function destinationFor(level: AccessLevel | undefined, intended?: string): string {
  const home = landingFor(level)
  if (!intended) return home
  return isPortalPath(intended) === (level === 'USER') ? intended : home
}
