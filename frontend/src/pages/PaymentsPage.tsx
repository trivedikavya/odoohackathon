import { useState } from 'react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Search } from 'lucide-react'
import { PageHeader } from '@/components/layout/AppLayout'
import { Card, CardBody } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Field'
import { Badge } from '@/components/ui/Badge'
import { EmptyRow, TD, TH, THead, TR, Table } from '@/components/ui/Table'
import { ErrorState, PageLoader } from '@/components/ui/PageLoader'
import { tradeApi } from '@/api/endpoints'
import { errorMessage } from '@/api/client'
import { formatDate, formatMoney, titleCase } from '@/lib/utils'

const PAGE_SIZE = 20

export function PaymentsPage() {
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)

  const query = useQuery({
    queryKey: ['payments', search, page],
    queryFn: () => tradeApi.payments({ search: search || undefined, page, size: PAGE_SIZE }),
    placeholderData: keepPreviousData,
  })

  const payments = query.data?.content ?? []

  return (
    <div>
      <PageHeader
        title="Payments"
        description="Money in and money out, each one tied to the document it settles and the journal entry it wrote."
      />

      <Card className="mb-4">
        <CardBody className="flex flex-wrap items-end gap-3 py-4">
          <div>
            <label className="mb-1 block text-[10px] font-medium text-muted-ink uppercase">
              Search
            </label>
            <div className="relative">
              <Search className="absolute top-1/2 left-2.5 h-4 w-4 -translate-y-1/2 text-muted-ink" />
              <Input
                className="w-64 pl-8"
                placeholder="Payment no, document or reference"
                value={search}
                onChange={(e) => {
                  setSearch(e.target.value)
                  setPage(0)
                }}
              />
            </div>
          </div>
        </CardBody>
      </Card>

      {query.isLoading ? (
        <PageLoader label="Loading payments…" />
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
      ) : (
        <div className="space-y-4">
          <Card>
            <Table>
              <THead>
                <tr>
                  <TH className="w-36">Payment no</TH>
                  <TH className="w-32">Date</TH>
                  <TH className="w-28">Direction</TH>
                  <TH className="w-24">Method</TH>
                  <TH className="w-36">Against</TH>
                  <TH>Counterparty</TH>
                  <TH>Reference</TH>
                  <TH className="w-36 text-right">Amount</TH>
                  <TH className="w-36">Journal entry</TH>
                </tr>
              </THead>
              <tbody>
                {payments.length === 0 ? (
                  <EmptyRow colSpan={9}>
                    {search ? 'No payments match that search.' : 'No payments recorded yet.'}
                  </EmptyRow>
                ) : (
                  payments.map((payment) => (
                    <TR key={payment.id}>
                      <TD className="tabular font-medium">{payment.paymentNo}</TD>
                      <TD>{formatDate(payment.paymentDate)}</TD>
                      <TD>
                        <Badge tone={payment.direction === 'IN' ? 'success' : 'warning'}>
                          {payment.direction === 'IN' ? 'Received' : 'Paid'}
                        </Badge>
                      </TD>
                      <TD>{titleCase(payment.method)}</TD>
                      <TD className="tabular text-xs">{payment.documentNo}</TD>
                      <TD>
                        {payment.counterpartyName ?? <span className="text-muted-ink">—</span>}
                      </TD>
                      <TD className="text-xs text-muted-ink">{payment.reference ?? '—'}</TD>
                      <TD className="tabular text-right font-medium">
                        {formatMoney(payment.amount)}
                      </TD>
                      <TD className="tabular text-xs text-muted-ink">
                        {payment.journalEntryNo ?? '—'}
                      </TD>
                    </TR>
                  ))
                )}
              </tbody>
            </Table>
          </Card>

          <div className="flex items-center justify-between">
            <p className="text-xs text-muted-ink">
              Page {(query.data?.page ?? 0) + 1} of {Math.max(query.data?.totalPages ?? 1, 1)} —{' '}
              {query.data?.totalElements ?? 0} payments
            </p>
            <div className="flex gap-2">
              <Button
                variant="outline"
                size="sm"
                disabled={page === 0}
                onClick={() => setPage((p) => Math.max(p - 1, 0))}
              >
                Previous
              </Button>
              <Button
                variant="outline"
                size="sm"
                disabled={query.data?.last ?? true}
                onClick={() => setPage((p) => p + 1)}
              >
                Next
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
