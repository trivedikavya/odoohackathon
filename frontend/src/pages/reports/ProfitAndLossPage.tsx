import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { PageHeader } from '@/components/layout/AppLayout'
import { Card, CardBody } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Field'
import { ErrorState, PageLoader } from '@/components/ui/PageLoader'
import { reportsApi } from '@/api/endpoints'
import { errorMessage } from '@/api/client'
import type { ReportSection } from '@/api/types'
import { cn, formatMoney, formatNumber, today } from '@/lib/utils'

function startOfYear(): string {
  return `${new Date().getFullYear()}-01-01`
}

function Kpi({
  label,
  value,
  sub,
  tone,
}: {
  label: string
  value: string
  sub?: string
  tone?: 'positive' | 'negative'
}) {
  return (
    <Card className="p-4">
      <p className="text-[10px] font-medium tracking-wide text-muted-ink uppercase">{label}</p>
      <p
        className={cn(
          'tabular mt-1.5 text-lg font-semibold',
          tone === 'positive' && 'text-emerald-700',
          tone === 'negative' && 'text-red-600',
          !tone && 'text-plum-800',
        )}
      >
        {value}
      </p>
      <p className="mt-0.5 min-h-4 text-xs text-muted-ink">{sub ?? ''}</p>
    </Card>
  )
}

function Section({
  title,
  section,
  emptyLabel,
}: {
  title: string
  section: ReportSection
  emptyLabel: string
}) {
  return (
    <div>
      <p className="mb-1 text-xs font-semibold tracking-wide text-muted-ink uppercase">{title}</p>
      {section.lines.length === 0 ? (
        <p className="py-2 text-sm text-muted-ink">{emptyLabel}</p>
      ) : (
        section.lines.map((line) => (
          <div
            key={line.accountId ?? line.code}
            className="flex items-baseline justify-between gap-4 border-b border-lilac-100 py-2"
          >
            <div className="min-w-0">
              <span className="tabular mr-2 text-xs text-muted-ink">{line.code}</span>
              <span className="text-sm text-plum-800">{line.name}</span>
            </div>
            <span className="tabular shrink-0 text-sm text-plum-800">
              {formatMoney(line.amount)}
            </span>
          </div>
        ))
      )}
      <div className="mt-2 flex items-baseline justify-between gap-4 border-t border-lilac-300 pt-2">
        <span className="text-sm font-medium text-plum-800">Total {section.title.toLowerCase()}</span>
        <span className="tabular text-sm font-medium text-plum-800">
          {formatMoney(section.total)}
        </span>
      </div>
    </div>
  )
}

/** The two subtotals that carry the story: gross profit and the bottom line. */
function SubtotalRow({
  label,
  value,
  hint,
  emphasis,
}: {
  label: string
  value: string
  hint?: string
  emphasis?: boolean
}) {
  const positive = Number(value) >= 0
  return (
    <div
      className={cn(
        'flex items-baseline justify-between gap-4 rounded-lg px-3 py-2.5',
        emphasis ? 'bg-lilac-100' : 'bg-lilac-50',
      )}
    >
      <div className="min-w-0">
        <span className={cn('text-sm font-semibold text-plum-800', emphasis && 'text-plum-900')}>
          {label}
        </span>
        {hint && <p className="text-xs text-muted-ink">{hint}</p>}
      </div>
      <span
        className={cn(
          'tabular shrink-0 font-semibold',
          emphasis ? 'text-base' : 'text-sm',
          positive ? 'text-emerald-700' : 'text-red-600',
        )}
      >
        {formatMoney(value)}
      </span>
    </div>
  )
}

export function ProfitAndLossPage() {
  const [from, setFrom] = useState(startOfYear())
  const [to, setTo] = useState(today())

  const query = useQuery({
    queryKey: ['profit-and-loss', from, to],
    queryFn: () => reportsApi.profitAndLoss(from, to),
  })

  const data = query.data
  const grossProfit = data ? Number(data.grossProfit) : 0
  const netProfit = data ? Number(data.netProfit) : 0
  const marginLabel =
    data && data.grossMarginPercent !== null
      ? `${formatNumber(data.grossMarginPercent, 1)}% margin`
      : 'No revenue to measure against'

  return (
    <div>
      <PageHeader
        title="Profit and loss"
        description="Revenue less the cost of what was sold, then less the overheads of running the place."
        action={
          <div className="flex gap-3">
            <div>
              <label className="mb-1 block text-[10px] font-medium text-muted-ink uppercase">
                From
              </label>
              <Input
                type="date"
                className="w-44"
                value={from}
                onChange={(e) => setFrom(e.target.value)}
              />
            </div>
            <div>
              <label className="mb-1 block text-[10px] font-medium text-muted-ink uppercase">
                To
              </label>
              <Input
                type="date"
                className="w-44"
                value={to}
                onChange={(e) => setTo(e.target.value)}
              />
            </div>
          </div>
        }
      />

      {query.isLoading ? (
        <PageLoader label="Calculating profit and loss…" />
      ) : query.isError ? (
        <Card>
          <ErrorState
            message={errorMessage(query.error)}
            action={
              <Button variant="outline" size="sm" onClick={() => void query.refetch()}>
                Retry
              </Button>
            }
          />
        </Card>
      ) : data ? (
        <div className="space-y-4">
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-5">
            <Kpi label="Revenue" value={formatMoney(data.totalIncome)} />
            <Kpi label="Cost of sales" value={formatMoney(data.totalCostOfSales)} />
            <Kpi
              label="Gross profit"
              value={formatMoney(data.grossProfit)}
              sub={marginLabel}
              tone={grossProfit >= 0 ? 'positive' : 'negative'}
            />
            <Kpi label="Operating expenses" value={formatMoney(data.totalExpenses)} />
            <Kpi
              label="Net profit"
              value={formatMoney(data.netProfit)}
              sub={`${data.from} to ${data.to}`}
              tone={netProfit >= 0 ? 'positive' : 'negative'}
            />
          </div>

          <Card>
            <CardBody className="space-y-4">
              <Section
                title="Revenue"
                section={data.income}
                emptyLabel="No income was recognised in this period."
              />

              <Section
                title="Cost of sales"
                section={data.costOfSales}
                emptyLabel="Nothing was delivered in this period, so no cost of sales was recognised."
              />

              <SubtotalRow
                label="Gross profit"
                value={data.grossProfit}
                hint={marginLabel}
                emphasis
              />

              <Section
                title="Operating expenses"
                section={data.expenses}
                emptyLabel="No operating expenses were recognised in this period."
              />

              <SubtotalRow label="Net profit" value={data.netProfit} emphasis />
            </CardBody>
            <CardBody className="border-t border-lilac-200 py-3">
              <p className="text-xs text-muted-ink">
                Cost of sales is the weighted-average cost of the goods actually delivered, not what
                was bought. Buying stock capitalises into Inventory and so does not by itself reduce
                profit — the cost only lands here when the goods leave.
              </p>
            </CardBody>
          </Card>
        </div>
      ) : null}
    </div>
  )
}
