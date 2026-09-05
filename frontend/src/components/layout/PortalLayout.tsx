import { useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { ChevronDown, FileText, LogOut, Receipt, ShoppingBag, Wallet } from 'lucide-react'
import { useAuth } from '@/auth/AuthContext'
import { cn } from '@/lib/utils'

/**
 * Shell for customer self-service.
 *
 * A separate layout rather than the staff sidebar with items hidden. A
 * customer keeps no books and the server refuses every non-portal path
 * for them, so presenting a trimmed-down back office would imply those
 * pages exist and are merely out of reach.
 */
const portalNav = [
  { to: '/portal', label: 'Overview', icon: FileText, end: true },
  { to: '/portal/buy', label: 'Buy', icon: ShoppingBag, end: false },
  { to: '/portal/invoices', label: 'My Invoices', icon: Receipt, end: false },
  { to: '/portal/payments', label: 'My Payments', icon: Wallet, end: false },
]

export function PortalLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [menuOpen, setMenuOpen] = useState(false)

  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="min-h-screen bg-lilac-50">
      <header className="bg-plum-800">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-3 sm:px-6">
          <div className="flex min-w-0 items-center gap-2.5">
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-amethyst-600 text-sm font-bold text-white">
              {user?.partyName?.slice(0, 2).toUpperCase() ?? 'C'}
            </div>
            <div className="min-w-0">
              <p className="truncate text-sm font-semibold text-white">{user?.partyName}</p>
              <p className="truncate text-xs text-lilac-300">Customer Portal</p>
            </div>
          </div>

          <div className="relative">
            <button
              onClick={() => setMenuOpen((v) => !v)}
              className="flex items-center gap-2 rounded-lg px-2 py-1.5 transition-colors hover:bg-plum-700"
            >
              <div className="flex h-7 w-7 items-center justify-center rounded-full bg-lilac-200 text-xs font-semibold text-amethyst-800">
                {user?.fullName?.charAt(0).toUpperCase() ?? '?'}
              </div>
              <div className="hidden text-left sm:block">
                <p className="text-xs font-medium text-white">{user?.fullName}</p>
                <p className="text-[10px] text-lilac-300">{user?.loginId}</p>
              </div>
              <ChevronDown className="h-3.5 w-3.5 text-lilac-300" />
            </button>

            {menuOpen && (
              <>
                <div className="fixed inset-0 z-10" onClick={() => setMenuOpen(false)} />
                <div className="absolute right-0 z-20 mt-1 w-56 rounded-lg border border-lilac-200 bg-white py-1 shadow-lg">
                  <div className="border-b border-lilac-100 px-3 py-2">
                    <p className="truncate text-sm font-medium text-plum-800">{user?.fullName}</p>
                    <p className="truncate text-xs text-muted-ink">{user?.email}</p>
                  </div>
                  <button
                    onClick={handleLogout}
                    className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-plum-800 hover:bg-lilac-50"
                  >
                    <LogOut className="h-4 w-4" />
                    Sign out
                  </button>
                </div>
              </>
            )}
          </div>
        </div>

        <nav className="mx-auto flex max-w-6xl gap-1 overflow-x-auto px-4 sm:px-6">
          {portalNav.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                cn(
                  'flex shrink-0 items-center gap-2 border-b-2 px-3 py-2.5 text-sm transition-colors',
                  isActive
                    ? 'border-amethyst-400 font-medium text-white'
                    : 'border-transparent text-lilac-200 hover:text-white',
                )
              }
            >
              <item.icon className="h-4 w-4" />
              {item.label}
            </NavLink>
          ))}
        </nav>
      </header>

      <main className="mx-auto max-w-6xl px-4 py-6 sm:px-6">
        <Outlet />
      </main>
    </div>
  )
}
