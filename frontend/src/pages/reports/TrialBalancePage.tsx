import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { CheckCircle2, XCircle } from 'lucide-react'
import { PageHeader } from '@/components/layout/AppLayout'
import { Card, CardBody } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Field'
import { Badge } from '@/components/ui/Badge'
import { EmptyRow, TD, TH, THead, TR, Table } from '@/components/ui/Table'
import { ErrorState, PageLoader } from '@/components/ui/PageLoader'
import { reportsApi } from '@/api/endpoints'
import { errorMessage } from '@/api/client'
import { cn, formatMoney, titleCase, today } from '@/lib/utils'

export function TrialBalancePage() {
  const [asOf, setAsOf] = useState(today())

  const query = useQuery({
    queryKey: ['trial-balance', asOf],
    queryFn: () => reportsApi.trialBalance(asOf),
  })

  return (
    <div>
      <PageHeader
        title="Trial balance"
        description="Every account in the book with its net position, debits against credits."
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
        <PageLoader label="Totalling the ledger…" />
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
                  Balanced. Debits ({formatMoney(query.data.totalDebit)}) = credits (
                  {formatMoney(query.data.totalCredit)}).
                </>
              ) : (
                <>
                  Not balanced. Debits ({formatMoney(query.data.totalDebit)}) against credits (
                  {formatMoney(query.data.totalCredit)}), a difference of{' '}
                  {formatMoney(query.data.difference)}.
                </>
              )}
            </span>
          </div>

          <Card>
            <Table>
              <THead>
                <tr>
                  <TH className="w-28">Code</TH>
                  <TH>Account</TH>
                  <TH className="w-32">Type</TH>
                  <TH className="w-40 text-right">Debit</TH>
                  <TH className="w-40 text-right">Credit</TH>
                </tr>
              </THead>
              <tbody>
                {query.data.rows.length === 0 ? (
                  <EmptyRow colSpan={5}>Nothing has been posted to this book yet.</EmptyRow>
                ) : (
                  query.data.rows.map((row) => (
                    <TR key={row.code}>
                      <TD className="tabular text-muted-ink">{row.code}</TD>
                      <TD className="font-medium">{row.name}</TD>
                      <TD>
                        <Badge tone="neutral">{titleCase(row.type)}</Badge>
                      </TD>
                      <TD className="tabular text-right">
                        {Number(row.debit) === 0 ? (
                          <span className="text-muted-ink">—</span>
                        ) : (
                          formatMoney(row.debit)
                        )}
                      </TD>
                      <TD className="tabular text-right">
                        {Number(row.credit) === 0 ? (
                          <span className="text-muted-ink">—</span>
                        ) : (
                          formatMoney(row.credit)
                        )}
                      </TD>
                    </TR>
                  ))
                )}
              </tbody>
              {query.data.rows.length > 0 && (
                <tfoot>
                  <tr>
                    <td colSpan={3} className="border-t-2 border-lilac-300 px-4 py-3">
                      <span className="text-sm font-semibold text-plum-800">Total</span>
                    </td>
                    <td className="tabular border-t-2 border-lilac-300 px-4 py-3 text-right text-sm font-semibold text-plum-800">
                      {formatMoney(query.data.totalDebit)}
                    </td>
                    <td className="tabular border-t-2 border-lilac-300 px-4 py-3 text-right text-sm font-semibold text-plum-800">
                      {formatMoney(query.data.totalCredit)}
                    </td>
                  </tr>
                </tfoot>
              )}
            </Table>
            <CardBody className="border-t border-lilac-200 py-3">
              <p className="text-xs text-muted-ink">
                Each account is netted first, so it appears on one side only — a debit balance or a
                credit balance, never both. The two columns must still come to the same figure,
                because every entry that fed them was checked as SUM(debit) = SUM(credit) at the
                moment it was written.
              </p>
            </CardBody>
          </Card>
        </div>
      ) : null}
    </div>
  )
}
