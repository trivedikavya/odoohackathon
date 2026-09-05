import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { AlertTriangle, CheckCircle2 } from 'lucide-react'
import { errorMessage } from '@/api/client'
import { reportsApi } from '@/api/endpoints'
import type { ReportSection } from '@/api/types'
import { PageHeader } from '@/components/layout/AppLayout'
import { Card, CardHeader } from '@/components/ui/Card'
import { Input } from '@/components/ui/Field'
import { ErrorState, PageLoader } from '@/components/ui/PageLoader'
import { cn, formatMoney, today } from '@/lib/utils'

function Section({ section, emphasis }: { section: ReportSection; emphasis?: boolean }) {
  return (
    <div>
      <p className="mb-2 text-xs font-semibold tracking-wide text-muted-ink uppercase">
        {section.title}
      </p>
      <div className="space-y-1">
        {section.lines.length === 0 ? (
          <p className="py-2 text-sm text-muted-ink italic">No balances</p>
        ) : (
          section.lines.map((line) => (
            <div
              key={`${line.code}-${line.accountId ?? 'derived'}`}
              className="flex items-baseline justify-between gap-4 py-1"
            >
              <span className="text-sm text-plum-800">
                <span className="tabular mr-2 text-xs text-muted-ink">{line.code}</span>
                {line.name}
              </span>
              <span className="tabular text-sm text-plum-800">{formatMoney(line.amount)}</span>
            </div>
          ))
        )}
      </div>
      <div
        className={cn(
          'mt-2 flex items-baseline justify-between gap-4 border-t border-lilac-200 pt-2 font-semibold',
          emphasis ? 'text-amethyst-700' : 'text-plum-800',
        )}
      >
        <span className="text-sm">Total {section.title}</span>
        <span className="tabular text-sm">{formatMoney(section.total)}</span>
      </div>
    </div>
  )
}

export function BalanceSheetPage() {
  const [asOf, setAsOf] = useState(today())

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['balance-sheet', asOf],
    queryFn: () => reportsApi.balanceSheet(asOf),
  })

  return (
    <>
      <PageHeader
        title="Balance Sheet"
        description="Computed live by aggregating the ledger — there is no stored total anywhere in the system."
        action={
          <div>
            <label className="mb-1 block text-[10px] font-medium text-muted-ink uppercase">
              As at
            </label>
            <Input
              type="date"
              value={asOf}
              onChange={(e) => setAsOf(e.target.value)}
              className="w-44"
            />
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
          <div
            className={cn(
              'mb-4 flex items-center gap-2.5 rounded-lg border px-4 py-3 text-sm',
              data.balanced
                ? 'border-emerald-200 bg-emerald-50 text-emerald-800'
                : 'border-red-200 bg-red-50 text-red-800',
            )}
          >
            {data.balanced ? (
              <CheckCircle2 className="h-4 w-4 shrink-0 text-emerald-600" />
            ) : (
              <AlertTriangle className="h-4 w-4 shrink-0 text-red-600" />
            )}
            <span>
              {data.balanced ? (
                <>
                  <strong>Balanced.</strong> Assets ({formatMoney(data.totalAssets)}) = Liabilities
                  + Equity ({formatMoney(data.totalLiabilitiesAndEquity)}).
                </>
              ) : (
                <>
                  <strong>Out of balance</strong> by {formatMoney(data.difference)}. This should
                  never happen — it indicates a ledger integrity problem.
                </>
              )}
            </span>
          </div>

          <div className="grid gap-4 lg:grid-cols-2">
            <Card>
              <CardHeader title="Assets" description="What the business owns" />
              <div className="p-5">
                <Section section={data.assets} emphasis />
              </div>
            </Card>

            <Card>
              <CardHeader title="Liabilities & Equity" description="How it is financed" />
              <div className="space-y-6 p-5">
                <Section section={data.liabilities} />
                <Section section={data.equity} />
                <div className="flex items-baseline justify-between gap-4 border-t-2 border-amethyst-600/30 pt-3 font-semibold text-amethyst-700">
                  <span className="text-sm">Total Liabilities & Equity</span>
                  <span className="tabular text-sm">
                    {formatMoney(data.totalLiabilitiesAndEquity)}
                  </span>
                </div>
              </div>
            </Card>
          </div>

          <p className="mt-4 text-xs text-muted-ink">
            Retained earnings of {formatMoney(data.retainedEarnings)} is derived as income less
            expenses to date, which is what makes the statement balance without any stored figure.
          </p>
        </>
      ) : null}
    </>
  )
}
