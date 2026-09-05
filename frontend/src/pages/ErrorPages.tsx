import { Component, type ErrorInfo, type ReactNode } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { AlertTriangle, ArrowLeft, Home, ShieldOff, WifiOff } from 'lucide-react'
import { useAuth } from '@/auth/AuthContext'
import { landingFor } from '@/auth/landing'
import { Button } from '@/components/ui/Button'

function ErrorShell({
  icon,
  code,
  title,
  children,
  actions,
}: {
  icon: ReactNode
  code: string
  title: string
  children: ReactNode
  actions?: ReactNode
}) {
  return (
    <div className="flex min-h-[70vh] items-center justify-center px-4 py-12">
      <div className="w-full max-w-md text-center">
        <div className="mx-auto mb-5 flex h-14 w-14 items-center justify-center rounded-2xl bg-lilac-100 text-amethyst-700">
          {icon}
        </div>
        <p className="text-xs font-semibold tracking-widest text-muted-ink uppercase">{code}</p>
        <h1 className="mt-1 text-xl font-semibold text-plum-800">{title}</h1>
        <div className="mt-2 text-sm leading-relaxed text-muted-ink">{children}</div>
        <div className="mt-6 flex flex-wrap justify-center gap-2">{actions}</div>
      </div>
    </div>
  )
}

/** Shown for any unmatched route. */
export function NotFoundPage() {
  const { user } = useAuth()
  const navigate = useNavigate()

  return (
    <ErrorShell
      icon={<AlertTriangle className="h-6 w-6" />}
      code="404"
      title="That page does not exist"
      actions={
        <>
          <Button variant="outline" onClick={() => navigate(-1)}>
            <ArrowLeft className="h-4 w-4" />
            Go back
          </Button>
          <Link to={landingFor(user?.accessLevel)}>
            <Button>
              <Home className="h-4 w-4" />
              Take me home
            </Button>
          </Link>
        </>
      }
    >
      The link may be out of date, or the record may have been removed.
    </ErrorShell>
  )
}

/**
 * Shown when the client-side guard blocks a route. Names the access level
 * required, because "forbidden" with no explanation just makes people
 * think the app is broken.
 */
export function ForbiddenPage({ required }: { required?: string }) {
  const { user } = useAuth()

  return (
    <ErrorShell
      icon={<ShieldOff className="h-6 w-6" />}
      code="403"
      title="You do not have access to this"
      actions={
        <Link to={landingFor(user?.accessLevel)}>
          <Button>
            <Home className="h-4 w-4" />
            Back to my dashboard
          </Button>
        </Link>
      }
    >
      <p>
        You are signed in as <strong className="text-plum-800">{user?.accessLevel}</strong>
        {required && (
          <>
            , and this page needs <strong className="text-plum-800">{required}</strong>
          </>
        )}
        .
      </p>
      <p className="mt-2 text-xs">
        The server enforces this independently — hiding the link is a convenience, not the
        protection.
      </p>
    </ErrorShell>
  )
}

export function OfflinePage() {
  return (
    <ErrorShell
      icon={<WifiOff className="h-6 w-6" />}
      code="Offline"
      title="Cannot reach the server"
      actions={
        <Button onClick={() => window.location.reload()}>Try again</Button>
      }
    >
      Check that the backend is running on port 8080 and that your connection is up.
    </ErrorShell>
  )
}

interface BoundaryState {
  error: Error | null
}

/**
 * Catches render-time crashes so a single bad component shows a readable
 * page instead of a blank white screen.
 */
export class AppErrorBoundary extends Component<{ children: ReactNode }, BoundaryState> {
  state: BoundaryState = { error: null }

  static getDerivedStateFromError(error: Error): BoundaryState {
    return { error }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Unhandled render error', error, info)
  }

  render() {
    if (!this.state.error) {
      return this.props.children
    }
    return (
      <div className="flex min-h-screen items-center justify-center bg-lilac-50 px-4">
        <div className="w-full max-w-lg text-center">
          <div className="mx-auto mb-5 flex h-14 w-14 items-center justify-center rounded-2xl bg-red-100 text-red-600">
            <AlertTriangle className="h-6 w-6" />
          </div>
          <p className="text-xs font-semibold tracking-widest text-muted-ink uppercase">
            Something broke
          </p>
          <h1 className="mt-1 text-xl font-semibold text-plum-800">
            The page could not be displayed
          </h1>
          <p className="mt-2 text-sm text-muted-ink">
            Your data is unaffected — this is a display fault, not a change to the ledger.
          </p>
          <pre className="mt-4 max-h-40 overflow-auto rounded-lg border border-lilac-200 bg-white p-3 text-left text-xs text-red-700">
            {this.state.error.message}
          </pre>
          <div className="mt-6 flex justify-center gap-2">
            <Button variant="outline" onClick={() => this.setState({ error: null })}>
              Dismiss
            </Button>
            <Button onClick={() => window.location.assign('/')}>Reload the app</Button>
          </div>
        </div>
      </div>
    )
  }
}
