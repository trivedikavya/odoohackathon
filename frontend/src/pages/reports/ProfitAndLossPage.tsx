import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { PageHeader } from '@/components/layout/AppLayout'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Field'
import { ErrorState, PageLoader } from '@/components/ui/PageLoader'
import { reportsApi } from '@/api/endpoints'
import { errorMessage } from '@/api/client'
import type { ReportSection } from '@/api/types'
import { cn, formatMoney, today } from '@/lib/utils'

function startOfYear(): string {
  return `${new Date().getFullYear()}-01-01`
}

function Section({ section, emptyLabel }: { section: ReportSection; emptyLabel: string }) {
  return (
    <Card>
      <CardHeader title={section.title} />
      <CardBody className="pt-1">
        {section.lines.length === 0 ? (
          <p className="py-3 text-sm text-muted-ink">{emptyLabel}</p>
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
        <div className="mt-3 flex items-baseline justify-between gap-4 border-t-2 border-lilac-300 pt-3">
          <span className="text-sm font-semibold text-plum-800">Total {section.title}</span>
          <span className="tabular text-sm font-semibold text-plum-800">
            {formatMoney(section.total)}
          </span>
        </div>
      </CardBody>
    </Card>
  )
}

export function ProfitAndLossPage() {
  const [from, setFrom] = useState(startOfYear())
  const [to, setTo] = useState(today())

  const query = useQuery({
    queryKey: ['profit-and-loss', from, to],
    queryFn: () => reportsApi.profitAndLoss(from, to),
  })

  const netProfit = query.data ? Number(query.data.netProfit) : 0

  return (
    <div>
      <PageHeader
        title="Profit and loss"
        description="Income earned less expenses incurred over the chosen period."
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
      ) : query.data ? (
        <div className="space-y-4">
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            <Card className="p-4">
              <p className="text-[10px] font-medium tracking-wide text-muted-ink uppercase">
                Total income
              </p>
              <p className="tabular mt-1.5 text-lg font-semibold text-plum-800">
                {formatMoney(query.data.totalIncome)}
              </p>
            </Card>
            <Card className="p-4">
              <p className="text-[10px] font-medium tracking-wide text-muted-ink uppercase">
                Total expenses
              </p>
              <p className="tabular mt-1.5 text-lg font-semibold text-plum-800">
                {formatMoney(query.data.totalExpenses)}
              </p>
            </Card>
            <Card className="p-4">
              <p className="text-[10px] font-medium tracking-wide text-muted-ink uppercase">
                Net profit
              </p>
              <p
                className={cn(
                  'tabular mt-1.5 text-lg font-semibold',
                  netProfit >= 0 ? 'text-emerald-700' : 'text-red-600',
                )}
              >
                {formatMoney(query.data.netProfit)}
              </p>
              <p className="mt-0.5 text-xs text-muted-ink">
                {query.data.from} to {query.data.to}
              </p>
            </Card>
          </div>

          <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
            <Section
              section={query.data.income}
              emptyLabel="No income was recognised in this period."
            />
            <Section
              section={query.data.expenses}
              emptyLabel="No expenses were recognised in this period."
            />
          </div>
        </div>
      ) : null}
    </div>
  )
}
