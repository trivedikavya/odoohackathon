import { useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { errorMessage } from '@/api/client'
import { useAuth } from '@/auth/AuthContext'
import { Button } from '@/components/ui/Button'
import { FormRow, Input } from '@/components/ui/Field'
import { PageLoader } from '@/components/ui/PageLoader'

export function LoginPage() {
  const { user, loading, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation() as { state?: { from?: string } }

  const [email, setEmail] = useState('admin@urbanfurniture.test')
  const [password, setPassword] = useState('Admin@123')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (loading) return <PageLoader />
  if (user) return <Navigate to={location.state?.from ?? '/'} replace />

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      await login(email, password)
      navigate(location.state?.from ?? '/', { replace: true })
    } catch (err) {
      setError(errorMessage(err, 'Unable to sign in'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex min-h-screen">
      {/* Brand panel */}
      <div className="relative hidden flex-1 bg-plum-800 lg:block">
        <div className="absolute inset-0 bg-gradient-to-br from-amethyst-700/40 to-transparent" />
        <div className="relative flex h-full flex-col justify-between p-12">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-amethyst-600 font-bold text-white">
              UF
            </div>
            <span className="text-lg font-semibold text-white">Urban Furniture</span>
          </div>

          <div className="max-w-md">
            <h1 className="text-3xl leading-tight font-semibold text-white">
              Double-entry accounting, done properly.
            </h1>
            <p className="mt-4 text-sm leading-relaxed text-lilac-200">
              Every transaction posts a balanced journal entry, validated on the server. Every
              report is computed live from the ledger — never from a stored total that can drift.
            </p>
          </div>

          <p className="text-xs text-lilac-300/70">Assets = Liabilities + Equity</p>
        </div>
      </div>

      {/* Form panel */}
      <div className="flex flex-1 items-center justify-center px-6 py-12">
        <div className="w-full max-w-sm">
          <div className="mb-8 lg:hidden">
            <div className="mb-3 flex h-10 w-10 items-center justify-center rounded-lg bg-amethyst-600 font-bold text-white">
              UF
            </div>
            <h1 className="text-xl font-semibold text-plum-800">Urban Furniture</h1>
          </div>

          <h2 className="text-lg font-semibold text-plum-800">Sign in</h2>
          <p className="mt-1 mb-6 text-sm text-muted-ink">
            Use your account to access the accounting workspace.
          </p>

          <form onSubmit={onSubmit} className="space-y-4">
            <FormRow label="Email" required>
              <Input
                type="email"
                value={email}
                autoComplete="username"
                onChange={(e) => setEmail(e.target.value)}
                required
              />
            </FormRow>

            <FormRow label="Password" required>
              <Input
                type="password"
                value={password}
                autoComplete="current-password"
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </FormRow>

            {error && (
              <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
                {error}
              </div>
            )}

            <Button type="submit" className="w-full" loading={submitting} size="lg">
              Sign in
            </Button>
          </form>

          <div className="mt-8 rounded-lg border border-lilac-200 bg-lilac-50 p-3">
            <p className="mb-2 text-xs font-semibold tracking-wide text-muted-ink uppercase">
              Demo accounts
            </p>
            <div className="space-y-1 text-xs text-plum-800">
              <p>
                <span className="font-medium">Owner:</span> admin@urbanfurniture.test / Admin@123
              </p>
              <p>
                <span className="font-medium">Accountant:</span> accountant@urbanfurniture.test /
                Accountant@123
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
