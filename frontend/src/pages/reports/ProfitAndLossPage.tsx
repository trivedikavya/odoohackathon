import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { TrendingDown, TrendingUp } from 'lucide-react'
import { errorMessage } from '@/api/client'
import { reportsApi } from '@/api/endpoints'
import type { ReportSection } from '@/api/types'
import { PageHeader } from '@/components/layout/AppLayout'
import { Card, CardHeader } from '@/components/ui/Card'
import { Input } from '@/components/ui/Field'
import { ErrorState, PageLoader } from '@/components/ui/PageLoader'
import { cn, formatMoney, today } from '@/lib/utils'

function startOfYear() {
  return `${new Date().getFullYear()}-01-01`
}

function Section({ section }: { section: ReportSection }) {
  return (
    <div className="space-y-1">
      {section.lines.length === 0 ? (
        <p className="py-2 text-sm text-muted-ink italic">No activity in this period</p>
      ) : (
        section.lines.map((line) => (
          <div key={line.code} className="flex items-baseline justify-between gap-4 py-1">
            <span className="text-sm text-plum-800">
              <span className="tabular mr-2 text-xs text-muted-ink">{line.code}</span>
              {line.name}
            </span>
            <span className="tabular text-sm text-plum-800">{formatMoney(line.amount)}</span>
          </div>
        ))
      )}
      <div className="mt-2 flex items-baseline justify-between gap-4 border-t border-lilac-200 pt-2 font-semibold text-plum-800">
        <span className="text-sm">Total {section.title}</span>
        <span className="tabular text-sm">{formatMoney(section.total)}</span>
      </div>
    </div>
  )
}

export function ProfitAndLossPage() {
  const [from, setFrom] = useState(startOfYear())
  const [to, setTo] = useState(today())

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['profit-and-loss', from, to],
    queryFn: () => reportsApi.profitAndLoss(from, to),
  })

  const profitable = data ? Number(data.netProfit) >= 0 : true

  return (
    <>
      <PageHeader
        title="Profit & Loss"
        description="Income less expenses for the selected period, aggregated from the ledger."
        action={
          <div className="flex gap-2">
            <div>
              <label className="mb-1 block text-[10px] font-medium text-muted-ink uppercase">
                From
              </label>
              <Input type="date" value={from} onChange={(e) => setFrom(e.target.value)} className="w-40" />
            </div>
            <div>
              <label className="mb-1 block text-[10px] font-medium text-muted-ink uppercase">
                To
              </label>
              <Input type="date" value={to} onChange={(e) => setTo(e.target.value)} className="w-40" />
            </div>
          </div>
        }
      />

      {isLoading ? (
        <Card>
          <PageLoader />
        </Card>
      ) : isError ? (
        <Card>
          <ErrorState message={errorMessage(error)} />
        </Card>
      ) : data ? (
        <>
          <div className="mb-4 grid gap-4 sm:grid-cols-3">
            <Card className="p-5">
              <p className="text-xs font-medium tracking-wide text-muted-ink uppercase">Income</p>
              <p className="tabular mt-1 text-2xl font-semibold text-emerald-700">
                {formatMoney(data.totalIncome)}
              </p>
            </Card>
            <Card className="p-5">
              <p className="text-xs font-medium tracking-wide text-muted-ink uppercase">Expenses</p>
              <p className="tabular mt-1 text-2xl font-semibold text-red-700">
                {formatMoney(data.totalExpenses)}
              </p>
            </Card>
            <Card className={cn('p-5', profitable ? 'bg-emerald-50/40' : 'bg-red-50/40')}>
              <div className="flex items-center gap-1.5">
                <p className="text-xs font-medium tracking-wide text-muted-ink uppercase">
                  Net {profitable ? 'profit' : 'loss'}
                </p>
                {profitable ? (
                  <TrendingUp className="h-3.5 w-3.5 text-emerald-600" />
                ) : (
                  <TrendingDown className="h-3.5 w-3.5 text-red-600" />
                )}
              </div>
              <p
                className={cn(
                  'tabular mt-1 text-2xl font-semibold',
                  profitable ? 'text-emerald-700' : 'text-red-700',
                )}
              >
                {formatMoney(data.netProfit)}
              </p>
            </Card>
          </div>

          <div className="grid gap-4 lg:grid-cols-2">
            <Card>
              <CardHeader title="Income" />
              <div className="p-5">
                <Section section={data.income} />
              </div>
            </Card>
            <Card>
              <CardHeader title="Expenses" />
              <div className="p-5">
                <Section section={data.expenses} />
              </div>
            </Card>
          </div>
        </>
      ) : null}
    </>
  )
}
