import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { PageHeader } from '@/components/layout/AppLayout'
import { Card, CardBody } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Field'
import { Badge } from '@/components/ui/Badge'
import { EmptyRow, TD, TH, THead, TR, Table } from '@/components/ui/Table'
import { EmptyState, ErrorState, PageLoader } from '@/components/ui/PageLoader'
import { reportsApi } from '@/api/endpoints'
import { errorMessage } from '@/api/client'
import { cn, formatDate, formatMoney, formatNumber, today } from '@/lib/utils'

function Utilisation({ percent }: { percent?: string | null }) {
  if (percent === null || percent === undefined) {
    return <span className="text-muted-ink">—</span>
  }
  const value = Number(percent)
  if (Number.isNaN(value)) return <span className="text-muted-ink">—</span>
  const over = value > 100
  return (
    <div className="w-32">
      <div className="flex items-baseline justify-between">
        <span className={cn('tabular text-xs', over ? 'text-red-600' : 'text-plum-800')}>
          {formatNumber(value, 1)}%
        </span>
      </div>
      <div className="mt-1 h-1.5 w-full overflow-hidden rounded-full bg-lilac-100">
        <div
          className={cn('h-full rounded-full', over ? 'bg-red-500' : 'bg-amethyst-600')}
          // The bar itself is capped at 100% width; the number above carries the overshoot.
          style={{ width: `${Math.min(Math.max(value, 0), 100)}%` }}
        />
      </div>
    </div>
  )
}

export function BudgetReportPage() {
  const [asOf, setAsOf] = useState(today())

  const query = useQuery({
    queryKey: ['budget-report', asOf],
    queryFn: () => reportsApi.budget(asOf),
  })

  return (
    <div>
      <PageHeader
        title="Budget report"
        description="Planned spend against what the ledger actually recorded."
        action={
          <div>
            <label className="mb-1 block text-[10px] font-medium text-muted-ink uppercase">
              As of
            </label>
            <Input
              type="date"
              className="w-44"
              value={asOf}
              onChange={(e) => setAsOf(e.target.value)}
            />
          </div>
        }
      />

      {query.isLoading ? (
        <PageLoader label="Measuring budgets against the ledger…" />
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
                Total planned
              </p>
              <p className="tabular mt-1.5 text-lg font-semibold text-plum-800">
                {formatMoney(query.data.totalPlanned)}
              </p>
            </Card>
            <Card className="p-4">
              <p className="text-[10px] font-medium tracking-wide text-muted-ink uppercase">
                Total actual
              </p>
              <p className="tabular mt-1.5 text-lg font-semibold text-plum-800">
                {formatMoney(query.data.totalActual)}
              </p>
            </Card>
            <Card className="p-4">
              <p className="text-[10px] font-medium tracking-wide text-muted-ink uppercase">
                Total variance
              </p>
              <p
                className={cn(
                  'tabular mt-1.5 text-lg font-semibold',
                  Number(query.data.totalVariance) >= 0 ? 'text-emerald-700' : 'text-red-600',
                )}
              >
                {formatMoney(query.data.totalVariance)}
              </p>
            </Card>
          </div>

          <Card>
            {query.data.budgets.length === 0 ? (
              <EmptyState
                title="No budgets to report on"
                description="Create a budget against an analytic account and its actuals will appear here automatically."
              />
            ) : (
              <Table>
                <THead>
                  <tr>
                    <TH>Budget</TH>
                    <TH>Analytic account</TH>
                    <TH className="w-52">Period</TH>
                    <TH>Responsible</TH>
                    <TH className="text-right">Planned</TH>
                    <TH className="text-right">Actual</TH>
                    <TH className="text-right">Variance</TH>
                    <TH className="w-40">Utilisation</TH>
                  </tr>
                </THead>
                <tbody>
                  {query.data.budgets.length === 0 ? (
                    <EmptyRow colSpan={8}>No budgets defined.</EmptyRow>
                  ) : (
                    query.data.budgets.map((b) => (
                      <TR key={b.budgetId}>
                        <TD className="font-medium">
                          {b.name}
                          {b.overBudget && (
                            <Badge tone="danger" className="ml-2">
                              Over budget
                            </Badge>
                          )}
                        </TD>
                        <TD>
                          <span className="tabular mr-1.5 text-xs text-muted-ink">
                            {b.analyticAccountCode}
                          </span>
                          {b.analyticAccountName}
                        </TD>
                        <TD className="text-xs text-muted-ink">
                          {formatDate(b.periodStart)} — {formatDate(b.periodEnd)}
                        </TD>
                        <TD>{b.responsible ?? <span className="text-muted-ink">—</span>}</TD>
                        <TD className="tabular text-right">{formatMoney(b.plannedAmount)}</TD>
                        <TD className="tabular text-right">{formatMoney(b.actualAmount)}</TD>
                        <TD
                          className={cn(
                            'tabular text-right',
                            Number(b.variance) >= 0 ? 'text-emerald-700' : 'text-red-600',
                          )}
                        >
                          {formatMoney(b.variance)}
                        </TD>
                        <TD>
                          <Utilisation percent={b.utilisationPercent} />
                        </TD>
                      </TR>
                    ))
                  )}
                </tbody>
              </Table>
            )}
            <CardBody className="border-t border-lilac-200 py-3">
              <p className="text-xs text-muted-ink">
                Actuals are aggregated live from the journal lines tagged to the analytic account and
                dated inside the budget period. Nothing is snapshotted, so a draft or cancelled
                document consumes no budget at all — only what was actually posted counts.
              </p>
            </CardBody>
          </Card>
        </div>
      ) : null}
    </div>
  )
}
