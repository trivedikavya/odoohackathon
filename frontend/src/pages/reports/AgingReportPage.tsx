import { Fragment, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { CheckCircle2, ChevronDown, ChevronRight, XCircle } from 'lucide-react'
import { PageHeader } from '@/components/layout/AppLayout'
import { Card, CardBody } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { Input, Select } from '@/components/ui/Field'
import { Badge } from '@/components/ui/Badge'
import { EmptyRow, TD, TH, THead, TR, Table } from '@/components/ui/Table'
import { ErrorState, PageLoader } from '@/components/ui/PageLoader'
import { reportsApi } from '@/api/endpoints'
import { errorMessage } from '@/api/client'
import type { AgingType } from '@/api/types'
import { cn, formatDate, formatMoney, titleCase, today } from '@/lib/utils'

const BUCKETS = [
  { key: 'current', label: 'Current' },
  { key: 'days1To30', label: '1–30 days' },
  { key: 'days31To60', label: '31–60 days' },
  { key: 'days61To90', label: '61–90 days' },
  { key: 'days90Plus', label: '90+ days' },
] as const

type BucketKey = (typeof BUCKETS)[number]['key']

function Money({ value }: { value: string }) {
  if (Number(value) === 0) return <span className="text-muted-ink">—</span>
  return <span className="tabular">{formatMoney(value)}</span>
}

export function AgingReportPage() {
  const [type, setType] = useState<AgingType>('RECEIVABLE')
  const [asOf, setAsOf] = useState(today())
  const [expanded, setExpanded] = useState<Record<number, boolean>>({})

  const query = useQuery({
    queryKey: ['aging', type, asOf],
    queryFn: () => reportsApi.aging(type, asOf),
  })

  const toggle = (partyId: number) =>
    setExpanded((current) => ({ ...current, [partyId]: !current[partyId] }))

  const controlAccount = type === 'RECEIVABLE' ? 'Accounts receivable' : 'Accounts payable'

  return (
    <div>
      <PageHeader
        title="Aging report"
        description={`Outstanding ${type === 'RECEIVABLE' ? 'customer' : 'supplier'} balances split by how overdue they are.`}
        action={
          <div className="flex gap-3">
            <div>
              <label className="mb-1 block text-[10px] font-medium text-muted-ink uppercase">
                Type
              </label>
              <Select
                className="w-44"
                value={type}
                onChange={(e) => setType(e.target.value as AgingType)}
              >
                <option value="RECEIVABLE">Receivable</option>
                <option value="PAYABLE">Payable</option>
              </Select>
            </div>
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
          </div>
        }
      />

      {query.isLoading ? (
        <PageLoader label="Aging the open documents…" />
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
          <div>
            <div
              className={cn(
                'flex items-start gap-2.5 rounded-xl border px-4 py-3 text-sm',
                query.data.reconciled
                  ? 'border-emerald-200 bg-emerald-50 text-emerald-800'
                  : 'border-red-200 bg-red-50 text-red-800',
              )}
            >
              {query.data.reconciled ? (
                <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-emerald-600" />
              ) : (
                <XCircle className="mt-0.5 h-4 w-4 shrink-0 text-red-600" />
              )}
              <span>
                {query.data.reconciled ? (
                  <>
                    Reconciled. The aging total ({formatMoney(query.data.total)}) matches the ledger
                    balance of the control account ({formatMoney(query.data.ledgerBalance)}).
                  </>
                ) : (
                  <>
                    Not reconciled. The aging total ({formatMoney(query.data.total)}) differs from
                    the ledger balance of {controlAccount} ({formatMoney(query.data.ledgerBalance)})
                    by {formatMoney(query.data.difference)}.
                  </>
                )}
              </span>
            </div>
            <p className="mt-2 text-xs text-muted-ink">
              Aging is necessarily derived from documents rather than from the ledger, because a due
              date lives on the invoice and never reaches a journal line. So rather than assume the
              two agree, the aging total is cross-checked against the balance of the {controlAccount}{' '}
              control account and the result is published above.
            </p>
          </div>

          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
            {BUCKETS.map((bucket) => {
              const danger = bucket.key === 'days90Plus'
              return (
                <Card
                  key={bucket.key}
                  className={cn('p-4', danger && 'border-red-200 bg-red-50/60')}
                >
                  <p
                    className={cn(
                      'text-[10px] font-medium tracking-wide uppercase',
                      danger ? 'text-red-700' : 'text-muted-ink',
                    )}
                  >
                    {bucket.label}
                  </p>
                  <p
                    className={cn(
                      'tabular mt-1.5 text-base font-semibold',
                      danger ? 'text-red-700' : 'text-plum-800',
                    )}
                  >
                    {formatMoney(query.data[bucket.key])}
                  </p>
                </Card>
              )
            })}
          </div>

          <Card>
            <Table>
              <THead>
                <tr>
                  <TH className="w-10" />
                  <TH>Counterparty</TH>
                  {BUCKETS.map((b) => (
                    <TH key={b.key} className="text-right">
                      {b.label}
                    </TH>
                  ))}
                  <TH className="text-right">Total</TH>
                </tr>
              </THead>
              <tbody>
                {query.data.rows.length === 0 ? (
                  <EmptyRow colSpan={8}>
                    Nothing outstanding as of {formatDate(query.data.asOf)}.
                  </EmptyRow>
                ) : (
                  query.data.rows.map((row) => {
                    const open = Boolean(expanded[row.partyId])
                    return (
                      <Fragment key={row.partyId}>
                        <TR className="cursor-pointer" onClick={() => toggle(row.partyId)}>
                          <TD>
                            {open ? (
                              <ChevronDown className="h-4 w-4 text-amethyst-600" />
                            ) : (
                              <ChevronRight className="h-4 w-4 text-muted-ink" />
                            )}
                          </TD>
                          <TD className="font-medium">{row.partyName}</TD>
                          {BUCKETS.map((b) => (
                            <TD key={b.key} className="text-right">
                              <Money value={row[b.key as BucketKey]} />
                            </TD>
                          ))}
                          <TD className="tabular text-right font-semibold">
                            {formatMoney(row.total)}
                          </TD>
                        </TR>
                        {open && (
                          <tr>
                            <td colSpan={8} className="border-b border-lilac-100 bg-lilac-50/60 p-0">
                              <div className="px-4 py-3">
                                {row.documents.length === 0 ? (
                                  <p className="text-xs text-muted-ink">
                                    No open documents for this counterparty.
                                  </p>
                                ) : (
                                  <table className="w-full text-xs">
                                    <thead>
                                      <tr className="text-left text-muted-ink">
                                        <th className="py-1 pr-4 font-medium">Document</th>
                                        <th className="py-1 pr-4 font-medium">Date</th>
                                        <th className="py-1 pr-4 font-medium">Due</th>
                                        <th className="py-1 pr-4 font-medium">Overdue</th>
                                        <th className="py-1 pr-4 font-medium">Bucket</th>
                                        <th className="py-1 text-right font-medium">Amount due</th>
                                      </tr>
                                    </thead>
                                    <tbody>
                                      {row.documents.map((doc) => (
                                        <tr key={doc.documentId} className="text-plum-800">
                                          <td className="py-1.5 pr-4 font-medium">
                                            {doc.documentNo}
                                          </td>
                                          <td className="py-1.5 pr-4">
                                            {formatDate(doc.documentDate)}
                                          </td>
                                          <td className="py-1.5 pr-4">{formatDate(doc.dueDate)}</td>
                                          <td className="py-1.5 pr-4">
                                            {doc.daysOverdue > 0 ? (
                                              <span className="font-medium text-red-600">
                                                {doc.daysOverdue} day
                                                {doc.daysOverdue === 1 ? '' : 's'}
                                              </span>
                                            ) : (
                                              <span className="text-muted-ink">—</span>
                                            )}
                                          </td>
                                          <td className="py-1.5 pr-4">
                                            <Badge
                                              tone={doc.daysOverdue > 90 ? 'danger' : 'neutral'}
                                            >
                                              {titleCase(doc.bucket)}
                                            </Badge>
                                          </td>
                                          <td className="tabular py-1.5 text-right">
                                            {formatMoney(doc.amountDue)}
                                          </td>
                                        </tr>
                                      ))}
                                    </tbody>
                                  </table>
                                )}
                              </div>
                            </td>
                          </tr>
                        )}
                      </Fragment>
                    )
                  })
                )}
              </tbody>
              {query.data.rows.length > 0 && (
                <tfoot>
                  <tr>
                    <td colSpan={2} className="border-t-2 border-lilac-300 px-4 py-3">
                      <span className="text-sm font-semibold text-plum-800">Total</span>
                    </td>
                    {BUCKETS.map((b) => (
                      <td
                        key={b.key}
                        className="tabular border-t-2 border-lilac-300 px-4 py-3 text-right text-sm font-semibold text-plum-800"
                      >
                        {formatMoney(query.data[b.key])}
                      </td>
                    ))}
                    <td className="tabular border-t-2 border-lilac-300 px-4 py-3 text-right text-sm font-semibold text-plum-800">
                      {formatMoney(query.data.total)}
                    </td>
                  </tr>
                </tfoot>
              )}
            </Table>
            <CardBody className="border-t border-lilac-200 py-3">
              <p className="text-xs text-muted-ink">
                Click any counterparty to see the individual documents behind their balance.
              </p>
            </CardBody>
          </Card>
        </div>
      ) : null}
    </div>
  )
}
