import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { errorMessage } from '@/api/client'
import { paymentsApi } from '@/api/endpoints'
import type { PaymentDirection } from '@/api/types'
import { PageHeader } from '@/components/layout/AppLayout'
import { Badge } from '@/components/ui/Badge'
import { Card } from '@/components/ui/Card'
import { Input, Select } from '@/components/ui/Field'
import { ErrorState, PageLoader } from '@/components/ui/PageLoader'
import { EmptyRow, Table, TD, TH, THead, TR } from '@/components/ui/Table'
import { formatDate, formatMoney } from '@/lib/utils'

export function PaymentsPage() {
  const [search, setSearch] = useState('')
  const [direction, setDirection] = useState<PaymentDirection | ''>('')

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['payments', search, direction],
    queryFn: () =>
      paymentsApi.search({
        search: search || undefined,
        direction: direction || undefined,
        size: 100,
      }),
  })

  const payments = data?.content ?? []

  return (
    <>
      <PageHeader
        title="Payments"
        description="Cash and bank movements settling invoices and bills. Each one posted its own journal entry."
      />

      <div className="mb-4 flex flex-wrap items-center gap-3">
        <Input
          placeholder="Search by number or contact…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="w-full sm:max-w-xs"
        />
        <Select
          value={direction}
          onChange={(e) => setDirection(e.target.value as PaymentDirection | '')}
          className="w-44"
        >
          <option value="">All directions</option>
          <option value="RECEIVE">Received</option>
          <option value="PAY">Paid out</option>
        </Select>
      </div>

      <Card>
        {isLoading ? (
          <PageLoader />
        ) : isError ? (
          <ErrorState message={errorMessage(error)} />
        ) : (
          <Table>
            <THead>
              <tr>
                <TH>Number</TH>
                <TH>Contact</TH>
                <TH>Direction</TH>
                <TH>Method</TH>
                <TH>Date</TH>
                <TH>Settles</TH>
                <TH>Journal entry</TH>
                <TH className="text-right">Amount</TH>
              </tr>
            </THead>
            <tbody>
              {payments.length === 0 ? (
                <EmptyRow colSpan={8}>No payments recorded yet.</EmptyRow>
              ) : (
                payments.map((p) => (
                  <TR key={p.id}>
                    <TD className="font-medium">{p.paymentNo}</TD>
                    <TD>{p.contactName}</TD>
                    <TD>
                      <Badge tone={p.direction === 'RECEIVE' ? 'success' : 'warning'}>
                        {p.direction === 'RECEIVE' ? 'Received' : 'Paid out'}
                      </Badge>
                    </TD>
                    <TD>
                      <Badge tone="neutral">{p.method === 'BANK' ? 'Bank' : 'Cash'}</Badge>
                    </TD>
                    <TD className="text-muted-ink">{formatDate(p.paymentDate)}</TD>
                    <TD className="text-muted-ink">{p.invoiceNo ?? p.billNo ?? '—'}</TD>
                    <TD className="tabular text-xs text-muted-ink">{p.journalEntryNo ?? '—'}</TD>
                    <TD className="tabular text-right font-medium">{formatMoney(p.amount)}</TD>
                  </TR>
                ))
              )}
            </tbody>
          </Table>
        )}
      </Card>
    </>
  )
}
