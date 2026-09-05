import { useQuery } from '@tanstack/react-query'
import { errorMessage } from '@/api/client'
import { portalApi } from '@/api/endpoints'
import { PageHeader } from '@/components/layout/AppLayout'
import { Badge } from '@/components/ui/Badge'
import { Card } from '@/components/ui/Card'
import { ErrorState, PageLoader } from '@/components/ui/PageLoader'
import { EmptyRow, Table, TD, TH, THead, TR } from '@/components/ui/Table'
import { formatDate, formatMoney, titleCase } from '@/lib/utils'

export function PortalPaymentsPage() {
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['portal-payments'],
    queryFn: () => portalApi.myPayments({ size: 100 }),
  })

  const payments = data?.content ?? []

  return (
    <>
      <PageHeader
        title="My Payments"
        description="Every payment recorded against your invoices."
      />

      <Card>
        {isLoading ? (
          <PageLoader />
        ) : isError ? (
          <ErrorState message={errorMessage(error)} />
        ) : (
          <Table>
            <THead>
              <tr>
                <TH>Payment</TH>
                <TH>Date</TH>
                <TH>Against</TH>
                <TH>Method</TH>
                <TH>Reference</TH>
                <TH className="text-right">Amount</TH>
              </tr>
            </THead>
            <tbody>
              {payments.length === 0 ? (
                <EmptyRow colSpan={6}>No payments recorded yet.</EmptyRow>
              ) : (
                payments.map((p) => (
                  <TR key={p.id}>
                    <TD className="font-medium">{p.paymentNo}</TD>
                    <TD>{formatDate(p.paymentDate)}</TD>
                    <TD>{p.documentNo}</TD>
                    <TD>
                      <Badge tone="neutral">{titleCase(p.method)}</Badge>
                    </TD>
                    <TD className="text-muted-ink">{p.reference || '—'}</TD>
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
