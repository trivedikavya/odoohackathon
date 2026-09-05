import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Check, CheckCircle2, XCircle } from 'lucide-react'
import { PageHeader } from '@/components/layout/AppLayout'
import { Card, CardBody } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Field'
import { Badge } from '@/components/ui/Badge'
import { EmptyRow, TD, TH, THead, TR, Table } from '@/components/ui/Table'
import { ErrorState, PageLoader } from '@/components/ui/PageLoader'
import { reportsApi } from '@/api/endpoints'
import { errorMessage } from '@/api/client'
import { cn, formatMoney, today } from '@/lib/utils'

/**
 * A position is signed: positive means the counterparty owes us. We show the
 * magnitude and spell out the direction underneath, which reads far better than
 * a bare minus sign in an accounting context.
 */
function Position({ value }: { value: string }) {
  const n = Number(value)
  return (
    <div className="text-right">
      <span className="tabular text-sm text-plum-800">{formatMoney(Math.abs(n))}</span>
      <p className="text-[10px] text-muted-ink">
        {n === 0 ? 'settled' : n > 0 ? 'they owe' : 'we owe'}
      </p>
    </div>
  )
}

export function ReconciliationPage() {
  const [asOf, setAsOf] = useState(today())

  const query = useQuery({
    queryKey: ['reconciliation', asOf],
    queryFn: () => reportsApi.reconciliation(asOf),
  })

  const data = query.data

  return (
    <div>
      <PageHeader
        title="Counterparty reconciliation"
        description="Our books against theirs, counterparty by counterparty."
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
        <PageLoader label="Comparing books…" />
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
          <div
            className={cn(
              'flex items-start gap-2.5 rounded-xl border px-4 py-3.5',
              data.allMatched
                ? 'border-emerald-200 bg-emerald-50'
                : 'border-red-200 bg-red-50',
            )}
          >
            {data.allMatched ? (
              <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0 text-emerald-600" />
            ) : (
              <XCircle className="mt-0.5 h-5 w-5 shrink-0 text-red-600" />
            )}
            <div>
              <p
                className={cn(
                  'text-sm font-semibold',
                  data.allMatched ? 'text-emerald-900' : 'text-red-900',
                )}
              >
                {data.allMatched
                  ? `All ${data.comparableCount} comparable counterparties reconcile`
                  : `${data.matchedCount} of ${data.comparableCount} reconcile`}
              </p>
              <p
                className={cn(
                  'text-xs',
                  data.allMatched ? 'text-emerald-800' : 'text-red-800',
                )}
              >
                {data.allMatched
                  ? 'Both sides of every mirrored deal agree to the rupee.'
                  : 'One or more counterparty balances have diverged. Each divergence is listed below.'}
              </p>
            </div>
          </div>

          <Card>
            <CardBody className="space-y-2 py-4">
              <p className="text-sm text-plum-800">
                When both parties to a trade keep their books on this platform, a single deal writes
                a mirrored entry into each book — a sale in one is the matching purchase in the
                other.
              </p>
              <p className="text-sm text-plum-800">
                It follows that what our books say a counterparty owes us must equal what their books
                say they owe us. A mismatch would mean the two ledgers have diverged, which should
                not be possible while the mirroring holds. This report proves it rather than assuming
                it.
              </p>
              <p className="text-xs text-muted-ink">
                Counterparties who keep no books of their own — customers — have nothing on the other
                side to compare against, so they are listed but excluded from the count.
              </p>
            </CardBody>
          </Card>

          <Card>
            <Table>
              <THead>
                <tr>
                  <TH>Counterparty</TH>
                  <TH className="w-44 text-right">Our position</TH>
                  <TH className="w-44 text-right">Their position</TH>
                  <TH className="w-40 text-right">Difference</TH>
                  <TH className="w-56">Status</TH>
                </tr>
              </THead>
              <tbody>
                {data.rows.length === 0 ? (
                  <EmptyRow colSpan={5}>
                    No counterparty balances to compare as of {asOf}.
                  </EmptyRow>
                ) : (
                  data.rows.map((row) => (
                    <TR
                      key={row.partyId}
                      className={cn(row.comparable && !row.matched && 'bg-red-50/60')}
                    >
                      <TD className="font-medium">{row.partyName}</TD>
                      <TD className="text-right align-top">
                        <Position value={row.ourPosition} />
                      </TD>
                      <TD className="text-right align-top">
                        {row.comparable && row.theirPosition !== null ? (
                          <Position value={row.theirPosition} />
                        ) : (
                          <span className="text-muted-ink">—</span>
                        )}
                      </TD>
                      <TD className="tabular text-right align-top">
                        {row.comparable && row.difference !== null ? (
                          <span
                            className={cn(
                              Number(row.difference) === 0 ? 'text-muted-ink' : 'font-medium text-red-600',
                            )}
                          >
                            {formatMoney(row.difference)}
                          </span>
                        ) : (
                          <span className="text-muted-ink">—</span>
                        )}
                      </TD>
                      <TD className="align-top">
                        {!row.comparable ? (
                          <Badge tone="neutral">No books — nothing to compare</Badge>
                        ) : row.matched ? (
                          <Badge tone="success">
                            <Check className="mr-1 h-3 w-3" />
                            Matches
                          </Badge>
                        ) : (
                          <Badge tone="danger">Diverged</Badge>
                        )}
                      </TD>
                    </TR>
                  ))
                )}
              </tbody>
            </Table>
          </Card>
        </div>
      ) : null}
    </div>
  )
}
