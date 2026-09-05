import { useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import {
  Boxes,
  Building2,
  ChevronDown,
  ClipboardList,
  Clock,
  FileText,
  Handshake,
  LayoutDashboard,
  ListTree,
  LogOut,
  Menu,
  PiggyBank,
  Scale,
  ScrollText,
  Settings,
  ShieldCheck,
  Target,
  TrendingUp,
  Users,
  Wallet,
  X,
} from 'lucide-react'
import { useAuth } from '@/auth/AuthContext'
import type { AccessLevel } from '@/api/types'
import { cn } from '@/lib/utils'

interface NavItem {
  to: string
  label: string
  icon: typeof LayoutDashboard
  levels?: AccessLevel[]
}

interface NavGroup {
  label: string
  items: NavItem[]
}

const navigation: NavGroup[] = [
  {
    label: 'Overview',
    items: [{ to: '/', label: 'Dashboard', icon: LayoutDashboard }],
  },
  {
    label: 'Trade',
    items: [
      { to: '/deals', label: 'Deals & Requests', icon: Handshake },
      { to: '/documents', label: 'Documents', icon: ClipboardList },
      { to: '/payments', label: 'Payments', icon: Wallet },
    ],
  },
  {
    label: 'Master Data',
    items: [
      { to: '/contacts', label: 'Contacts', icon: Users },
      { to: '/products', label: 'Products', icon: Boxes },
    ],
  },
  {
    label: 'Accounting',
    items: [
      { to: '/ledger', label: 'General Ledger', icon: ScrollText },
      { to: '/reports/trial-balance', label: 'Trial Balance', icon: Scale },
      { to: '/reports/balance-sheet', label: 'Balance Sheet', icon: FileText },
      { to: '/reports/profit-and-loss', label: 'Profit & Loss', icon: TrendingUp },
      { to: '/reports/aging', label: 'AR / AP Aging', icon: Clock },
      { to: '/reports/reconciliation', label: 'Reconciliation', icon: ShieldCheck },
    ],
  },
  {
    label: 'Analytic',
    items: [
      { to: '/analytic-accounts', label: 'Projects & Centres', icon: Target },
      { to: '/budgets', label: 'Budgets', icon: PiggyBank },
      { to: '/reports/budget', label: 'Budget Report', icon: TrendingUp },
    ],
  },
  {
    label: 'Configuration',
    items: [
      { to: '/accounts', label: 'Chart of Accounts', icon: ListTree },
      { to: '/organisation', label: 'Organisation & GST', icon: Building2, levels: ['ADMIN'] },
      { to: '/users', label: 'Users', icon: Settings, levels: ['ADMIN'] },
    ],
  },
]

export function AppLayout() {
  const { user, logout, hasAccess } = useAuth()
  const navigate = useNavigate()
  const [mobileOpen, setMobileOpen] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)

  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  const initials = user?.partyName?.slice(0, 2).toUpperCase() ?? 'UF'

  const sidebar = (
    <nav className="scrollbar-thin flex h-full flex-col overflow-y-auto">
      <div className="flex items-center gap-2.5 px-5 py-5">
        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-amethyst-600 text-sm font-bold text-white">
          {initials}
        </div>
        <div className="min-w-0">
          <p className="truncate text-sm font-semibold text-white">{user?.partyName}</p>
          {/* The party type matters: a Vendor's app looks the same but its
              books are entirely its own. */}
          <p className="truncate text-xs text-lilac-300">{user?.partyType} · Accounting</p>
        </div>
      </div>

      <div className="flex-1 space-y-5 px-3 pb-4">
        {navigation.map((group) => {
          const items = group.items.filter((i) => !i.levels || hasAccess(...i.levels))
          if (items.length === 0) return null
          return (
            <div key={group.label}>
              <p className="px-3 pb-1.5 text-[10px] font-semibold tracking-widest text-lilac-300/70 uppercase">
                {group.label}
              </p>
              <div className="space-y-0.5">
                {items.map((item) => (
                  <NavLink
                    key={item.to}
                    to={item.to}
                    end={item.to === '/'}
                    onClick={() => setMobileOpen(false)}
                    className={({ isActive }) =>
                      cn(
                        'flex items-center gap-2.5 rounded-lg px-3 py-2 text-sm transition-colors',
                        isActive
                          ? 'bg-amethyst-600 font-medium text-white'
                          : 'text-lilac-200 hover:bg-plum-700 hover:text-white',
                      )
                    }
                  >
                    <item.icon className="h-4 w-4 shrink-0" />
                    <span className="truncate">{item.label}</span>
                  </NavLink>
                ))}
              </div>
            </div>
          )
        })}
      </div>
    </nav>
  )

  return (
    <div className="min-h-screen bg-lilac-50">
      <aside className="fixed inset-y-0 left-0 z-40 hidden w-60 bg-plum-800 lg:block">{sidebar}</aside>

      {mobileOpen && (
        <div className="fixed inset-0 z-50 lg:hidden">
          <div className="fixed inset-0 bg-plum-900/50" onClick={() => setMobileOpen(false)} />
          <aside className="fixed inset-y-0 left-0 w-64 bg-plum-800">
            <button
              onClick={() => setMobileOpen(false)}
              className="absolute top-5 right-3 rounded p-1 text-lilac-200 hover:bg-plum-700"
              aria-label="Close menu"
            >
              <X className="h-4 w-4" />
            </button>
            {sidebar}
          </aside>
        </div>
      )}

      <div className="lg:pl-60">
        <header className="sticky top-0 z-30 flex h-14 items-center justify-between border-b border-lilac-200 bg-white/90 px-4 backdrop-blur sm:px-6">
          <button
            onClick={() => setMobileOpen(true)}
            className="rounded-lg p-2 text-plum-800 hover:bg-lilac-100 lg:hidden"
            aria-label="Open menu"
          >
            <Menu className="h-5 w-5" />
          </button>

          <div className="relative ml-auto">
            <button
              onClick={() => setMenuOpen((v) => !v)}
              className="flex items-center gap-2 rounded-lg px-2 py-1.5 transition-colors hover:bg-lilac-100"
            >
              <div className="flex h-7 w-7 items-center justify-center rounded-full bg-lilac-200 text-xs font-semibold text-amethyst-800">
                {user?.fullName?.charAt(0).toUpperCase() ?? '?'}
              </div>
              <div className="hidden text-left sm:block">
                <p className="text-xs font-medium text-plum-800">{user?.fullName}</p>
                <p className="text-[10px] text-muted-ink">{user?.accessLevel}</p>
              </div>
              <ChevronDown className="h-3.5 w-3.5 text-muted-ink" />
            </button>

            {menuOpen && (
              <>
                <div className="fixed inset-0 z-10" onClick={() => setMenuOpen(false)} />
                <div className="absolute right-0 z-20 mt-1 w-56 rounded-lg border border-lilac-200 bg-white py-1 shadow-lg">
                  <div className="border-b border-lilac-100 px-3 py-2">
                    <p className="truncate text-sm font-medium text-plum-800">{user?.fullName}</p>
                    <p className="truncate text-xs text-muted-ink">
                      {user?.loginId} · {user?.email}
                    </p>
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
        </header>

        <main className="px-4 py-6 sm:px-6 lg:px-8">
          <Outlet />
        </main>
      </div>
    </div>
  )
}

export function PageHeader({
  title,
  description,
  action,
}: {
  title: string
  description?: string
  action?: React.ReactNode
}) {
  return (
    <div className="mb-6 flex flex-wrap items-start justify-between gap-3">
      <div>
        <h1 className="text-xl font-semibold text-plum-800">{title}</h1>
        {description && <p className="mt-1 text-sm text-muted-ink">{description}</p>}
      </div>
      {action}
    </div>
  )
}
