import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { AlertTriangle, ArrowRight, Receipt, ShoppingBag } from 'lucide-react'
import { errorMessage } from '@/api/client'
import { portalApi } from '@/api/endpoints'
import { PageHeader } from '@/components/layout/AppLayout'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { ErrorState, PageLoader } from '@/components/ui/PageLoader'
import { cn, formatMoney } from '@/lib/utils'

function Kpi({
  label,
  value,
  hint,
  tone,
}: {
  label: string
  value: string
  hint?: string
  tone?: 'default' | 'warning'
}) {
  return (
    <Card className={cn('p-5', tone === 'warning' && 'border-amber-200 bg-amber-50')}>
      <p className="text-[10px] font-medium tracking-wide text-muted-ink uppercase">{label}</p>
      <p
        className={cn(
          'tabular mt-1 text-2xl font-semibold',
          tone === 'warning' ? 'text-amber-800' : 'text-plum-800',
        )}
      >
        {value}
      </p>
      {hint && <p className="mt-1 text-xs text-muted-ink">{hint}</p>}
    </Card>
  )
}

export function PortalOverviewPage() {
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['portal-summary'],
    queryFn: portalApi.summary,
  })

  if (isLoading) {
    return (
      <Card>
        <PageLoader />
      </Card>
    )
  }
  if (isError) {
    return (
      <Card>
        <ErrorState message={errorMessage(error)} />
      </Card>
    )
  }
  if (!data) return null

  return (
    <>
      <PageHeader
        title={`Welcome, ${data.partyName}`}
        description="Everything you have bought, from every supplier, in one place."
      />

      {data.overdueCount > 0 && (
        <div className="mb-4 flex items-center gap-2.5 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
          <AlertTriangle className="h-4 w-4 shrink-0 text-amber-600" />
          <span>
            You have <strong>{data.overdueCount}</strong>{' '}
            {data.overdueCount === 1 ? 'invoice' : 'invoices'} past the due date.
          </span>
        </div>
      )}

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Kpi
          label="Outstanding"
          value={formatMoney(data.outstanding)}
          hint={`${data.openCount} open ${data.openCount === 1 ? 'invoice' : 'invoices'}`}
          tone={Number(data.outstanding) > 0 ? 'warning' : 'default'}
        />
        <Kpi label="Total billed" value={formatMoney(data.totalBilled)} hint="All time" />
        <Kpi label="Total paid" value={formatMoney(data.totalPaid)} hint="All time" />
        <Kpi label="Overdue" value={String(data.overdueCount)} hint="Past the due date" />
      </div>

      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        <Card className="p-5">
          <p className="flex items-center gap-2 text-sm font-medium text-plum-800">
            <ShoppingBag className="h-4 w-4 text-amethyst-600" />
            Buy something
          </p>
          <p className="mt-1 mb-3 text-sm text-muted-ink">
            Send a request to any seller or vendor. They confirm the price, deliver, and invoice
            you.
          </p>
          <Link to="/portal/buy">
            <Button variant="outline">
              Browse suppliers
              <ArrowRight className="h-4 w-4" />
            </Button>
          </Link>
        </Card>

        <Card className="p-5">
          <p className="flex items-center gap-2 text-sm font-medium text-plum-800">
            <Receipt className="h-4 w-4 text-amethyst-600" />
            Settle an invoice
          </p>
          <p className="mt-1 mb-3 text-sm text-muted-ink">
            Review what is outstanding and pay it directly from here.
          </p>
          <Link to="/portal/invoices">
            <Button variant="outline">
              View my invoices
              <ArrowRight className="h-4 w-4" />
            </Button>
          </Link>
        </Card>
      </div>

      <p className="mt-4 text-xs text-muted-ink">
        You are only ever shown records addressed to you. That restriction is applied by the server
        from your session, not by hiding rows in this page.
      </p>
    </>
  )
}
