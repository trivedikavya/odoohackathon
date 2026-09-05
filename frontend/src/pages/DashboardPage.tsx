import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { AlertTriangle, CheckCircle2, XCircle } from 'lucide-react'
import { PageHeader } from '@/components/layout/AppLayout'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { ErrorState, InlineLoader, PageLoader } from '@/components/ui/PageLoader'
import { reportsApi } from '@/api/endpoints'
import { errorMessage } from '@/api/client'
import { useAuth } from '@/auth/AuthContext'
import { cn, formatMoney, formatNumber } from '@/lib/utils'

const MONTH_NAMES = [
  'Jan',
  'Feb',
  'Mar',
  'Apr',
  'May',
  'Jun',
  'Jul',
  'Aug',
  'Sep',
  'Oct',
  'Nov',
  'Dec',
]

/** "2026-03" -> "Mar 26". Falls back to the raw value for anything unexpected. */
function formatPeriod(period: string): string {
  const parts = period.split('-')
  if (parts.length < 2) return period
  const monthIndex = Number(parts[1]) - 1
  const name = MONTH_NAMES[monthIndex]
  if (!name) return period
  return `${name} ${parts[0].slice(2)}`
}

function greeting(): string {
  const hour = new Date().getHours()
  if (hour < 12) return 'Good morning'
  if (hour < 17) return 'Good afternoon'
  return 'Good evening'
}

function Kpi({
  label,
  value,
  hint,
  tone = 'default',
}: {
  label: string
  value: ReactNode
  hint?: string
  tone?: 'default' | 'positive' | 'negative'
}) {
  return (
    <Card className="p-4">
      <p className="text-[10px] font-medium tracking-wide text-muted-ink uppercase">{label}</p>
      <p
        className={cn(
          'mt-1.5 text-lg font-semibold',
          tone === 'positive' && 'text-emerald-700',
          tone === 'negative' && 'text-red-600',
          tone === 'default' && 'text-plum-800',
        )}
      >
        {value}
      </p>
      {hint && <p className="mt-0.5 text-xs text-muted-ink">{hint}</p>}
    </Card>
  )
}

function IntegrityBanner({ ok, children }: { ok: boolean; children: ReactNode }) {
  return (
    <div
      className={cn(
        'flex items-start gap-2 rounded-lg border px-3 py-2.5 text-sm',
        ok
          ? 'border-emerald-200 bg-emerald-50 text-emerald-800'
          : 'border-red-200 bg-red-50 text-red-800',
      )}
    >
      {ok ? (
        <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-emerald-600" />
      ) : (
        <XCircle className="mt-0.5 h-4 w-4 shrink-0 text-red-600" />
      )}
      <span>{children}</span>
    </div>
  )
}

export function DashboardPage() {
  const { user } = useAuth()

  // Each panel owns its own query so a single failing endpoint degrades one
  // section instead of blanking the whole dashboard.
  const summary = useQuery({ queryKey: ['dashboard'], queryFn: () => reportsApi.dashboard() })
  const balanceSheet = useQuery({
    queryKey: ['dashboard', 'balance-sheet'],
    queryFn: () => reportsApi.balanceSheet(),
  })
  const reconciliation = useQuery({
    queryKey: ['dashboard', 'reconciliation'],
    queryFn: () => reportsApi.reconciliation(),
  })
  const trend = useQuery({
    queryKey: ['dashboard', 'sales-trend', 12],
    queryFn: () => reportsApi.salesTrend(12),
  })

  // Recharts needs numbers; every money field arrives as a string.
  const chartData = (trend.data?.points ?? []).map((p) => ({
    period: p.period,
    income: Number(p.income),
    expenses: Number(p.expenses),
  }))
  const hasChartData = chartData.some((p) => p.income !== 0 || p.expenses !== 0)

  return (
    <div>
      <PageHeader
        title={`${greeting()}, ${user?.fullName ?? 'there'}`}
        description={
          summary.data ? `${summary.data.bookName} — books as of ${summary.data.asOf}` : undefined
        }
      />

      {summary.data && summary.data.awaitingMyDecisionCount > 0 && (
        <div className="mb-6 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-amber-300 bg-amber-50 px-4 py-3.5">
          <div className="flex items-start gap-2.5">
            <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-amber-600" />
            <div>
              <p className="text-sm font-semibold text-amber-900">
                {summary.data.awaitingMyDecisionCount} request
                {summary.data.awaitingMyDecisionCount === 1 ? '' : 's'} awaiting your decision
              </p>
              <p className="text-xs text-amber-800">
                A counterparty is waiting on you to accept or reject.
              </p>
            </div>
          </div>
          <Link to="/deals">
            <Button size="sm">Review requests</Button>
          </Link>
        </div>
      )}

      {summary.isLoading ? (
        <PageLoader label="Loading your summary…" />
      ) : summary.isError ? (
        <Card className="mb-6">
          <ErrorState
            message={errorMessage(summary.error)}
            action={
              <Button variant="outline" size="sm" onClick={() => void summary.refetch()}>
                Retry
              </Button>
            }
          />
        </Card>
      ) : summary.data ? (
        <div className="mb-6 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <Kpi
            label="Total sales"
            value={<span className="tabular">{formatMoney(summary.data.totalSales)}</span>}
          />
          <Kpi
            label="Total purchases"
            value={<span className="tabular">{formatMoney(summary.data.totalPurchases)}</span>}
          />
          <Kpi
            label="Cash"
            value={<span className="tabular">{formatMoney(summary.data.cashBalance)}</span>}
          />
          <Kpi
            label="Bank"
            value={<span className="tabular">{formatMoney(summary.data.bankBalance)}</span>}
          />
          <Kpi
            label="Receivable"
            value={<span className="tabular">{formatMoney(summary.data.accountsReceivable)}</span>}
            hint="Owed to you"
          />
          <Kpi
            label="Payable"
            value={<span className="tabular">{formatMoney(summary.data.accountsPayable)}</span>}
            hint="You owe"
          />
          <Kpi
            label="Net profit"
            value={<span className="tabular">{formatMoney(summary.data.netProfit)}</span>}
            tone={Number(summary.data.netProfit) >= 0 ? 'positive' : 'negative'}
          />
          <Kpi
            label="Cash and bank"
            value={<span className="tabular">{formatMoney(summary.data.cashAndBank)}</span>}
          />
          <Kpi label="Invoices" value={formatNumber(summary.data.invoiceCount, 0)} />
          <Kpi label="Bills" value={formatNumber(summary.data.billCount, 0)} />
          <Kpi label="Open deals" value={formatNumber(summary.data.openDealCount, 0)} />
          <Kpi
            label="Awaiting my decision"
            value={formatNumber(summary.data.awaitingMyDecisionCount, 0)}
            tone={summary.data.awaitingMyDecisionCount > 0 ? 'negative' : 'default'}
          />
        </div>
      ) : null}

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader
            title="Income and expenses"
            description="Last 12 months, aggregated from posted journal lines."
          />
          <CardBody>
            {trend.isLoading ? (
              <div className="flex h-[260px] items-center justify-center">
                <InlineLoader />
              </div>
            ) : trend.isError ? (
              <ErrorState
                message={errorMessage(trend.error)}
                action={
                  <Button variant="outline" size="sm" onClick={() => void trend.refetch()}>
                    Retry
                  </Button>
                }
              />
            ) : !hasChartData ? (
              <div className="flex h-[260px] items-center justify-center text-sm text-muted-ink">
                No posted activity in the last 12 months yet.
              </div>
            ) : (
              // ResponsiveContainer measures its parent, so the parent must have a
              // resolved height — a percentage height on the container alone is zero.
              <div className="h-[260px] w-full">
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart data={chartData} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                    <defs>
                      <linearGradient id="dash-income" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor="#663399" stopOpacity={0.35} />
                        <stop offset="95%" stopColor="#663399" stopOpacity={0.02} />
                      </linearGradient>
                      <linearGradient id="dash-expenses" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor="#A3779D" stopOpacity={0.3} />
                        <stop offset="95%" stopColor="#A3779D" stopOpacity={0.02} />
                      </linearGradient>
                    </defs>
                    <CartesianGrid strokeDasharray="3 3" stroke="#F3E8F3" vertical={false} />
                    <XAxis
                      dataKey="period"
                      tickFormatter={(value: string) => formatPeriod(value)}
                      tick={{ fontSize: 11, fill: '#6B5B7B' }}
                      tickLine={false}
                      axisLine={{ stroke: '#E6C7E6' }}
                    />
                    <YAxis
                      tick={{ fontSize: 11, fill: '#6B5B7B' }}
                      tickLine={false}
                      axisLine={false}
                      width={70}
                      tickFormatter={(value: number) => formatNumber(value, 0)}
                    />
                    <Tooltip
                      labelFormatter={(label: unknown) => formatPeriod(String(label))}
                      formatter={(value: unknown, name: unknown) => [
                        formatMoney(Number(value)),
                        name === 'income' ? 'Income' : 'Expenses',
                      ]}
                      contentStyle={{
                        borderRadius: 8,
                        border: '1px solid #E6C7E6',
                        fontSize: 12,
                      }}
                    />
                    <Area
                      type="monotone"
                      dataKey="income"
                      stroke="#663399"
                      strokeWidth={2}
                      fill="url(#dash-income)"
                    />
                    <Area
                      type="monotone"
                      dataKey="expenses"
                      stroke="#A3779D"
                      strokeWidth={2}
                      fill="url(#dash-expenses)"
                    />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
            )}
          </CardBody>
        </Card>

        <Card>
          <CardHeader
            title="Accounting integrity"
            description="Checks that run against the live ledger."
          />
          <CardBody className="space-y-3">
            {balanceSheet.isLoading ? (
              <div className="flex items-center gap-2 text-sm text-muted-ink">
                <InlineLoader /> Checking the balance sheet…
              </div>
            ) : balanceSheet.isError ? (
              <IntegrityBanner ok={false}>
                Balance sheet unavailable — {errorMessage(balanceSheet.error)}
              </IntegrityBanner>
            ) : balanceSheet.data ? (
              <IntegrityBanner ok={balanceSheet.data.balanced}>
                {balanceSheet.data.balanced
                  ? `Balance sheet balances (Assets ${formatMoney(balanceSheet.data.totalAssets)} = Liabilities + Equity ${formatMoney(balanceSheet.data.totalLiabilitiesAndEquity)})`
                  : `Balance sheet is out by ${formatMoney(balanceSheet.data.difference)} (Assets ${formatMoney(balanceSheet.data.totalAssets)} vs ${formatMoney(balanceSheet.data.totalLiabilitiesAndEquity)})`}
              </IntegrityBanner>
            ) : null}

            {reconciliation.isLoading ? (
              <div className="flex items-center gap-2 text-sm text-muted-ink">
                <InlineLoader /> Checking counterparty books…
              </div>
            ) : reconciliation.isError ? (
              <IntegrityBanner ok={false}>
                Reconciliation unavailable — {errorMessage(reconciliation.error)}
              </IntegrityBanner>
            ) : reconciliation.data ? (
              <IntegrityBanner ok={reconciliation.data.allMatched}>
                {reconciliation.data.allMatched
                  ? `All ${reconciliation.data.comparableCount} counterparties reconcile`
                  : `${reconciliation.data.matchedCount} of ${reconciliation.data.comparableCount} reconcile`}
              </IntegrityBanner>
            ) : null}

            <ul className="space-y-1.5 pt-1 text-xs text-muted-ink">
              <li className="flex gap-2">
                <span className="text-amethyst-600">•</span>
                Every entry is validated as SUM(debit) = SUM(credit) before it is saved.
              </li>
              <li className="flex gap-2">
                <span className="text-amethyst-600">•</span>
                Reports aggregate the ledger directly. There are no cached totals to drift.
              </li>
              <li className="flex gap-2">
                <span className="text-amethyst-600">•</span>
                Books are isolated — no entry can span two sets of books.
              </li>
            </ul>

            <div className="pt-1">
              <Link
                to="/reports/reconciliation"
                className="text-xs font-medium text-amethyst-600 hover:text-amethyst-700"
              >
                Open the reconciliation report →
              </Link>
            </div>
          </CardBody>
        </Card>
      </div>
    </div>
  )
}
