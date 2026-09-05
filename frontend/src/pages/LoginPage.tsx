import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { ArrowRight, BookOpenCheck, Scale, ShieldCheck } from 'lucide-react'
import { errorMessage } from '@/api/client'
import { useAuth } from '@/auth/AuthContext'
import { destinationFor } from '@/auth/landing'
import { Button } from '@/components/ui/Button'
import { FormRow, Input } from '@/components/ui/Field'

const highlights = [
  {
    icon: ArrowRight,
    title: 'One deal, two sets of books',
    body: 'Raise a request once. Your counterparty sees the same trade, and each side posts its own entries.',
  },
  {
    icon: Scale,
    title: 'Double entry, always balanced',
    body: 'Invoices and bills generate the journal entry for you, with the GST split worked out.',
  },
  {
    icon: BookOpenCheck,
    title: 'Reports that reconcile',
    body: 'Trial balance, P&L, balance sheet and ageing, all driven from the same ledger.',
  },
]

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const [identifier, setIdentifier] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  // ProtectedRoute stashes the blocked path here; router state is loosely
  // typed, so narrow it to the one shape we actually put in.
  const intended = (location.state as { from?: string } | null)?.from

  const onSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      const profile = await login(identifier.trim(), password)
      navigate(destinationFor(profile.accessLevel, intended), { replace: true })
    } catch (err) {
      setError(errorMessage(err, 'Could not sign you in'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="grid min-h-screen bg-lilac-50 lg:grid-cols-2">
      {/* Brand panel — desktop only; on mobile the form should own the fold. */}
      <div className="relative hidden overflow-hidden bg-plum-800 lg:flex lg:flex-col lg:justify-between lg:p-12">
        <div
          className="pointer-events-none absolute -top-32 -right-24 h-96 w-96 rounded-full bg-amethyst-600/30 blur-3xl"
          aria-hidden
        />
        <div
          className="pointer-events-none absolute -bottom-40 -left-20 h-96 w-96 rounded-full bg-violet-soft-600/20 blur-3xl"
          aria-hidden
        />

        <div className="relative flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-amethyst-600 text-sm font-bold text-white">
            UF
          </div>
          <div>
            <p className="text-sm font-semibold text-white">Urban Furniture</p>
            <p className="text-xs text-lilac-300">Multi-party accounting</p>
          </div>
        </div>

        <div className="relative max-w-md">
          <h2 className="text-3xl leading-tight font-semibold text-white">
            Books that agree with your counterparty.
          </h2>
          <p className="mt-3 text-sm text-lilac-200">
            Sellers, vendors and customers work the same deal from opposite sides. Nothing touches
            the ledger until it is invoiced.
          </p>

          <ul className="mt-10 space-y-6">
            {highlights.map((item) => (
              <li key={item.title} className="flex gap-3.5">
                <div className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-white/10 ring-1 ring-white/15">
                  <item.icon className="h-4 w-4 text-lilac-200" />
                </div>
                <div>
                  <p className="text-sm font-medium text-white">{item.title}</p>
                  <p className="mt-0.5 text-sm text-lilac-300">{item.body}</p>
                </div>
              </li>
            ))}
          </ul>
        </div>

        <p className="relative flex items-center gap-2 text-xs text-lilac-300/80">
          <ShieldCheck className="h-3.5 w-3.5" />
          Every role is enforced on the server as well as here.
        </p>
      </div>

      {/* Form panel */}
      <div className="flex items-center justify-center px-5 py-12 sm:px-10">
        <div className="w-full max-w-sm">
          <div className="mb-8 flex items-center gap-2.5 lg:hidden">
            <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-amethyst-600 text-sm font-bold text-white">
              UF
            </div>
            <div>
              <p className="text-sm font-semibold text-plum-800">Urban Furniture</p>
              <p className="text-xs text-muted-ink">Multi-party accounting</p>
            </div>
          </div>

          <h1 className="text-2xl font-semibold text-plum-800">Sign in</h1>
          <p className="mt-1.5 text-sm text-muted-ink">
            Welcome back. Use your login ID or the email you registered with.
          </p>

          {error && (
            <div
              role="alert"
              className="mt-6 rounded-lg border border-red-200 bg-red-50 px-3.5 py-3 text-sm text-red-700"
            >
              {error}
            </div>
          )}

          <form onSubmit={onSubmit} className="mt-6 space-y-4" noValidate>
            <FormRow label="Login ID or email" required>
              <Input
                value={identifier}
                onChange={(e) => setIdentifier(e.target.value)}
                autoComplete="username"
                autoFocus
                placeholder="acme.books or you@acme.in"
                required
              />
            </FormRow>

            <FormRow label="Password" required>
              <Input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
                placeholder="••••••••"
                required
              />
            </FormRow>

            <Button
              type="submit"
              size="lg"
              className="w-full"
              loading={submitting}
              disabled={!identifier.trim() || !password}
            >
              Sign in
            </Button>
          </form>

          <p className="mt-6 text-center text-sm text-muted-ink">
            New here?{' '}
            <Link
              to="/register"
              className="font-medium text-amethyst-600 hover:text-amethyst-700 hover:underline"
            >
              Create an account
            </Link>
          </p>
        </div>
      </div>
    </div>
  )
}
