import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import {
  ArrowDownRight,
  ArrowUpRight,
  Banknote,
  Boxes,
  CheckCircle2,
  ScrollText,
  TrendingDown,
  TrendingUp,
  Users,
  Wallet,
} from 'lucide-react'
import { useState } from 'react'
import { errorMessage } from '@/api/client'
import { ledgerApi, reportsApi } from '@/api/endpoints'
import { useAuth } from '@/auth/AuthContext'
import { PageHeader } from '@/components/layout/AppLayout'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Card, CardHeader } from '@/components/ui/Card'
import { EmptyState, ErrorState, PageLoader } from '@/components/ui/PageLoader'
import { cn, formatDate, formatMoney, titleCase } from '@/lib/utils'
import { CapitalModal } from './CapitalModal'

function Kpi({
  label,
  value,
  hint,
  icon: Icon,
  tone = 'brand',
}: {
  label: string
  value: string
  hint?: string
  icon: typeof Wallet
  tone?: 'brand' | 'success' | 'danger' | 'neutral'
}) {
  const tones = {
    brand: 'bg-lilac-200 text-amethyst-700',
    success: 'bg-emerald-50 text-emerald-700',
    danger: 'bg-red-50 text-red-700',
    neutral: 'bg-lilac-100 text-plum-700',
  }
  return (
    <Card className="p-5">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-xs font-medium tracking-wide text-muted-ink uppercase">{label}</p>
          <p className="tabular mt-1.5 truncate text-xl font-semibold text-plum-800">{value}</p>
          {hint && <p className="mt-0.5 text-xs text-muted-ink">{hint}</p>}
        </div>
        <div className={cn('flex h-9 w-9 shrink-0 items-center justify-center rounded-lg', tones[tone])}>
          <Icon className="h-4 w-4" />
        </div>
      </div>
    </Card>
  )
}

export function DashboardPage() {
  const { user, isAdmin } = useAuth()
  const [capitalOpen, setCapitalOpen] = useState(false)

  const summaryQuery = useQuery({
    queryKey: ['dashboard'],
    queryFn: () => reportsApi.dashboard(),
  })

  const balanceQuery = useQuery({
    queryKey: ['balance-sheet', 'dashboard'],
    queryFn: () => reportsApi.balanceSheet(),
  })

  const ledgerQuery = useQuery({
    queryKey: ['ledger', 'recent'],
    queryFn: () => ledgerApi.search({ size: 5 }),
  })

  if (summaryQuery.isLoading) return <PageLoader />
  if (summaryQuery.isError) return <ErrorState message={errorMessage(summaryQuery.error)} />

  const s = summaryQuery.data!
  const profitable = Number(s.netProfit) >= 0

  return (
    <>
      <PageHeader
        title={`Welcome back, ${user?.fullName ?? 'there'}`}
        description={`Financial position as at ${formatDate(s.asOf)}, computed live from the ledger.`}
        action={
          isAdmin ? (
            <Button variant="outline" onClick={() => setCapitalOpen(true)}>
              <Banknote className="h-4 w-4" />
              Record capital
            </Button>
          ) : undefined
        }
      />

      <div className="mb-4 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Kpi
          label="Total sales"
          value={formatMoney(s.totalSales)}
          hint={`${s.invoiceCount} invoice${s.invoiceCount === 1 ? '' : 's'}`}
          icon={ArrowUpRight}
          tone="success"
        />
        <Kpi
          label="Total purchases"
          value={formatMoney(s.totalPurchases)}
          hint={`${s.billCount} bill${s.billCount === 1 ? '' : 's'}`}
          icon={ArrowDownRight}
          tone="danger"
        />
        <Kpi
          label="Cash & bank"
          value={formatMoney(s.cashAndBank)}
          hint={`Cash ${formatMoney(s.cashBalance)} · Bank ${formatMoney(s.bankBalance)}`}
          icon={Banknote}
        />
        <Kpi
          label={profitable ? 'Net profit' : 'Net loss'}
          value={formatMoney(s.netProfit)}
          hint="Income less expenses"
          icon={profitable ? TrendingUp : TrendingDown}
          tone={profitable ? 'success' : 'danger'}
        />
      </div>

      <div className="mb-4 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Kpi
          label="Receivable"
          value={formatMoney(s.accountsReceivable)}
          hint="Owed to you by customers"
          icon={Wallet}
        />
        <Kpi
          label="Payable"
          value={formatMoney(s.accountsPayable)}
          hint="Owed by you to vendors"
          icon={Wallet}
        />
        <Kpi label="Contacts" value={String(s.contactCount)} icon={Users} tone="neutral" />
        <Kpi label="Products" value={String(s.productCount)} icon={Boxes} tone="neutral" />
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader
            title="Recent ledger activity"
            description="Journal entries generated automatically by your transactions"
            action={
              <Link to="/ledger" className="text-sm font-medium text-amethyst-600 hover:underline">
                View ledger
              </Link>
            }
          />
          <div className="divide-y divide-lilac-100">
            {ledgerQuery.isLoading ? (
              <PageLoader />
            ) : (ledgerQuery.data?.content.length ?? 0) === 0 ? (
              <EmptyState
                icon={<ScrollText className="h-7 w-7" />}
                title="No transactions yet"
                description="Create a purchase or sales order to see the ledger fill up."
              />
            ) : (
              ledgerQuery.data!.content.map((e) => (
                <div key={e.id} className="flex items-center justify-between gap-4 px-5 py-3">
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="tabular text-sm font-medium text-plum-800">{e.entryNo}</span>
                      <Badge tone="neutral">{titleCase(e.sourceType)}</Badge>
                    </div>
                    <p className="mt-0.5 truncate text-xs text-muted-ink">{e.narration}</p>
                  </div>
                  <div className="shrink-0 text-right">
                    <p className="tabular text-sm font-medium text-plum-800">
                      {formatMoney(e.totalDebit)}
                    </p>
                    <p className="text-xs text-muted-ink">{formatDate(e.entryDate)}</p>
                  </div>
                </div>
              ))
            )}
          </div>
        </Card>

        <Card>
          <CardHeader title="Accounting integrity" description="Checked on every request" />
          <div className="space-y-4 p-5">
            {balanceQuery.data && (
              <div
                className={cn(
                  'flex items-start gap-2.5 rounded-lg border px-3 py-2.5 text-sm',
                  balanceQuery.data.balanced
                    ? 'border-emerald-200 bg-emerald-50 text-emerald-800'
                    : 'border-red-200 bg-red-50 text-red-800',
                )}
              >
                <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0" />
                <div>
                  <p className="font-medium">
                    {balanceQuery.data.balanced ? 'Balance sheet balances' : 'Out of balance'}
                  </p>
                  <p className="mt-0.5 text-xs opacity-90">
                    Assets {formatMoney(balanceQuery.data.totalAssets)} = Liabilities + Equity{' '}
                    {formatMoney(balanceQuery.data.totalLiabilitiesAndEquity)}
                  </p>
                </div>
              </div>
            )}

            <div className="space-y-2 text-sm text-muted-ink">
              <p className="flex items-start gap-2">
                <CheckCircle2 className="mt-0.5 h-3.5 w-3.5 shrink-0 text-emerald-600" />
                Every entry validated as SUM(debit) = SUM(credit) before it is saved.
              </p>
              <p className="flex items-start gap-2">
                <CheckCircle2 className="mt-0.5 h-3.5 w-3.5 shrink-0 text-emerald-600" />
                Reports aggregate the ledger directly — no cached totals.
              </p>
              <p className="flex items-start gap-2">
                <CheckCircle2 className="mt-0.5 h-3.5 w-3.5 shrink-0 text-emerald-600" />
                Document totals recomputed server-side, never trusted from the client.
              </p>
            </div>

            <Link
              to="/reports/balance-sheet"
              className="block text-sm font-medium text-amethyst-600 hover:underline"
            >
              Open the balance sheet →
            </Link>
          </div>
        </Card>
      </div>

      <CapitalModal open={capitalOpen} onClose={() => setCapitalOpen(false)} />
    </>
  )
}
