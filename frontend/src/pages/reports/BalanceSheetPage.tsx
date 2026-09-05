import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { CheckCircle2, XCircle } from 'lucide-react'
import { PageHeader } from '@/components/layout/AppLayout'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Field'
import { ErrorState, PageLoader } from '@/components/ui/PageLoader'
import { reportsApi } from '@/api/endpoints'
import { errorMessage } from '@/api/client'
import type { ReportSection } from '@/api/types'
import { cn, formatMoney, today } from '@/lib/utils'

function SectionRows({ section }: { section: ReportSection }) {
  if (section.lines.length === 0) {
    return <p className="py-3 text-sm text-muted-ink">No balances under {section.title}.</p>
  }
  return (
    <div>
      {section.lines.map((line) => (
        <div
          key={`${section.title}-${line.accountId ?? line.code}`}
          className="flex items-baseline justify-between gap-4 border-b border-lilac-100 py-2 last:border-b-0"
        >
          <div className="min-w-0">
            <span className="tabular mr-2 text-xs text-muted-ink">{line.code}</span>
            <span className="text-sm text-plum-800">{line.name}</span>
          </div>
          <span className="tabular shrink-0 text-sm text-plum-800">{formatMoney(line.amount)}</span>
        </div>
      ))}
    </div>
  )
}

export function BalanceSheetPage() {
  const [asOf, setAsOf] = useState(today())

  const query = useQuery({
    queryKey: ['balance-sheet', asOf],
    queryFn: () => reportsApi.balanceSheet(asOf),
  })

  return (
    <div>
      <PageHeader
        title="Balance sheet"
        description="What the book owns, what it owes, and what is left over."
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
        <PageLoader label="Building the balance sheet…" />
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
          <div
            className={cn(
              'flex items-start gap-2.5 rounded-xl border px-4 py-3 text-sm',
              query.data.balanced
                ? 'border-emerald-200 bg-emerald-50 text-emerald-800'
                : 'border-red-200 bg-red-50 text-red-800',
            )}
          >
            {query.data.balanced ? (
              <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-emerald-600" />
            ) : (
              <XCircle className="mt-0.5 h-4 w-4 shrink-0 text-red-600" />
            )}
            <span>
              {query.data.balanced ? (
                <>
                  Balanced. Assets ({formatMoney(query.data.totalAssets)}) = Liabilities + Equity (
                  {formatMoney(query.data.totalLiabilitiesAndEquity)}).
                </>
              ) : (
                <>
                  Out of balance by {formatMoney(query.data.difference)}. Assets (
                  {formatMoney(query.data.totalAssets)}) vs Liabilities + Equity (
                  {formatMoney(query.data.totalLiabilitiesAndEquity)}).
                </>
              )}
            </span>
          </div>

          <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
            <Card>
              <CardHeader title="Assets" description={query.data.assets.title} />
              <CardBody className="pt-1">
                <SectionRows section={query.data.assets} />
                <div className="mt-3 flex items-baseline justify-between gap-4 border-t-2 border-lilac-300 pt-3">
                  <span className="text-sm font-semibold text-plum-800">Total assets</span>
                  <span className="tabular text-sm font-semibold text-plum-800">
                    {formatMoney(query.data.totalAssets)}
                  </span>
                </div>
              </CardBody>
            </Card>

            <Card>
              <CardHeader title="Liabilities and equity" />
              <CardBody className="pt-1">
                <p className="pb-1 text-[10px] font-semibold tracking-wide text-muted-ink uppercase">
                  {query.data.liabilities.title}
                </p>
                <SectionRows section={query.data.liabilities} />
                <div className="mt-2 flex items-baseline justify-between gap-4 border-t border-lilac-200 pt-2">
                  <span className="text-sm font-medium text-plum-800">Total liabilities</span>
                  <span className="tabular text-sm font-medium text-plum-800">
                    {formatMoney(query.data.liabilities.total)}
                  </span>
                </div>

                <p className="pt-5 pb-1 text-[10px] font-semibold tracking-wide text-muted-ink uppercase">
                  {query.data.equity.title}
                </p>
                <SectionRows section={query.data.equity} />
                <div className="flex items-baseline justify-between gap-4 border-b border-lilac-100 py-2">
                  <span className="text-sm text-plum-800">Retained earnings (derived)</span>
                  <span className="tabular text-sm text-plum-800">
                    {formatMoney(query.data.retainedEarnings)}
                  </span>
                </div>
                <div className="mt-2 flex items-baseline justify-between gap-4 border-t border-lilac-200 pt-2">
                  <span className="text-sm font-medium text-plum-800">Total equity</span>
                  <span className="tabular text-sm font-medium text-plum-800">
                    {formatMoney(query.data.equity.total)}
                  </span>
                </div>

                <div className="mt-3 flex items-baseline justify-between gap-4 border-t-2 border-lilac-300 pt-3">
                  <span className="text-sm font-semibold text-plum-800">
                    Total liabilities and equity
                  </span>
                  <span className="tabular text-sm font-semibold text-plum-800">
                    {formatMoney(query.data.totalLiabilitiesAndEquity)}
                  </span>
                </div>
              </CardBody>
            </Card>
          </div>

          <p className="text-xs text-muted-ink">
            Retained earnings is not a stored figure. It is derived at read time as income less
            expenses over the life of the book, which is precisely why the two sides agree without
            anyone having to post a closing entry.
          </p>
        </div>
      ) : null}
    </div>
  )
}
