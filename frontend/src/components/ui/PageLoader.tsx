import { AlertCircle, Loader2 } from 'lucide-react'
import type { ReactNode } from 'react'

export function PageLoader({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="flex min-h-64 items-center justify-center gap-2 text-muted-ink">
      <Loader2 className="h-5 w-5 animate-spin text-amethyst-600" />
      <span className="text-sm">{label}</span>
    </div>
  )
}

export function InlineLoader() {
  return <Loader2 className="h-4 w-4 animate-spin text-amethyst-600" />
}

export function ErrorState({ message, action }: { message: string; action?: ReactNode }) {
  return (
    <div className="flex min-h-40 flex-col items-center justify-center gap-3 px-6 py-10 text-center">
      <AlertCircle className="h-8 w-8 text-red-500" />
      <p className="max-w-md text-sm text-plum-800">{message}</p>
      {action}
    </div>
  )
}

export function EmptyState({
  title,
  description,
  action,
  icon,
}: {
  title: string
  description?: string
  action?: ReactNode
  icon?: ReactNode
}) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 px-6 py-14 text-center">
      {icon && <div className="mb-1 text-violet-soft-500">{icon}</div>}
      <p className="text-sm font-medium text-plum-800">{title}</p>
      {description && <p className="max-w-sm text-sm text-muted-ink">{description}</p>}
      {action && <div className="mt-3">{action}</div>}
    </div>
  )
}
