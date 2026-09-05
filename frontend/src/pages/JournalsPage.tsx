import { useQuery } from '@tanstack/react-query'
import { errorMessage } from '@/api/client'
import { journalsApi } from '@/api/endpoints'
import { PageHeader } from '@/components/layout/AppLayout'
import { Badge } from '@/components/ui/Badge'
import { Card } from '@/components/ui/Card'
import { ErrorState, PageLoader } from '@/components/ui/PageLoader'
import { EmptyRow, Table, TD, TH, THead, TR } from '@/components/ui/Table'
import { titleCase } from '@/lib/utils'

export function JournalsPage() {
  const { data: journals = [], isLoading, isError, error } = useQuery({
    queryKey: ['journals'],
    queryFn: journalsApi.list,
  })

  return (
    <>
      <PageHeader
        title="Journals"
        description="Every ledger entry is booked into one of these journals, chosen automatically by transaction type."
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
                <TH>Code</TH>
                <TH>Name</TH>
                <TH>Type</TH>
                <TH>Default account</TH>
                <TH>Used for</TH>
              </tr>
            </THead>
            <tbody>
              {journals.length === 0 ? (
                <EmptyRow colSpan={5}>No journals configured.</EmptyRow>
              ) : (
                journals.map((j) => (
                  <TR key={j.id}>
                    <TD className="tabular font-medium">{j.code}</TD>
                    <TD>{j.name}</TD>
                    <TD>
                      <Badge tone="brand">{titleCase(j.type)}</Badge>
                    </TD>
                    <TD className="text-muted-ink">{j.defaultAccountName ?? '—'}</TD>
                    <TD className="text-sm text-muted-ink">
                      {j.type === 'SALES' && 'Customer invoices'}
                      {j.type === 'PURCHASE' && 'Vendor bills'}
                      {j.type === 'CASH' && 'Cash payments'}
                      {j.type === 'BANK' && 'Bank payments'}
                    </TD>
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
