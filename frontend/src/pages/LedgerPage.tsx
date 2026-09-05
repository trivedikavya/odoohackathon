import { useState } from 'react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { CheckCircle2 } from 'lucide-react'
import { PageHeader } from '@/components/layout/AppLayout'
import { Card, CardBody } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { Input, Select } from '@/components/ui/Field'
import { Badge } from '@/components/ui/Badge'
import { TD, TH, THead, TR, Table } from '@/components/ui/Table'
import { EmptyState, ErrorState, PageLoader } from '@/components/ui/PageLoader'
import { ledgerApi } from '@/api/endpoints'
import { errorMessage } from '@/api/client'
import type { SourceType } from '@/api/types'
import { formatDate, formatMoney, titleCase } from '@/lib/utils'

const SOURCE_TYPES: SourceType[] = ['INVOICE', 'BILL', 'PAYMENT', 'OPENING', 'MANUAL']
const PAGE_SIZE = 10

export function LedgerPage() {
  const [sourceType, setSourceType] = useState<SourceType | ''>('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [page, setPage] = useState(0)

  const query = useQuery({
    queryKey: ['ledger', sourceType, from, to, page],
    queryFn: () =>
      ledgerApi.entries({
        sourceType: sourceType === '' ? undefined : sourceType,
        from: from || undefined,
        to: to || undefined,
        page,
        size: PAGE_SIZE,
      }),
    placeholderData: keepPreviousData,
  })

  // Any filter change invalidates the current page offset.
  const resetTo = <T,>(setter: (value: T) => void) => {
    return (value: T) => {
      setter(value)
      setPage(0)
    }
  }

  const entries = query.data?.content ?? []

  return (
    <div>
      <PageHeader
        title="Ledger"
        description="Every journal entry written into your book, newest first."
      />

      <Card className="mb-4">
        <CardBody className="flex flex-wrap items-end gap-3 py-4">
          <div>
            <label className="mb-1 block text-[10px] font-medium text-muted-ink uppercase">
              Source
            </label>
            <Select
              className="w-44"
              value={sourceType}
              onChange={(e) => resetTo(setSourceType)(e.target.value as SourceType | '')}
            >
              <option value="">All sources</option>
              {SOURCE_TYPES.map((t) => (
                <option key={t} value={t}>
                  {titleCase(t)}
                </option>
              ))}
            </Select>
          </div>
          <div>
            <label className="mb-1 block text-[10px] font-medium text-muted-ink uppercase">
              From
            </label>
            <Input
              type="date"
              className="w-44"
              value={from}
              onChange={(e) => resetTo(setFrom)(e.target.value)}
            />
          </div>
          <div>
            <label className="mb-1 block text-[10px] font-medium text-muted-ink uppercase">To</label>
            <Input
              type="date"
              className="w-44"
              value={to}
              onChange={(e) => resetTo(setTo)(e.target.value)}
            />
          </div>
          {(sourceType || from || to) && (
            <Button
              variant="ghost"
              size="sm"
              onClick={() => {
                setSourceType('')
                setFrom('')
                setTo('')
                setPage(0)
              }}
            >
              Clear
            </Button>
          )}
        </CardBody>
      </Card>

      {query.isLoading ? (
        <PageLoader label="Loading journal entries…" />
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
      ) : entries.length === 0 ? (
        <Card>
          <EmptyState
            title="No journal entries"
            description="Nothing has been posted to this book for the selected filters."
          />
        </Card>
      ) : (
        <div className="space-y-4">
          {entries.map((entry) => (
            <Card key={entry.id}>
              <div className="flex flex-wrap items-start justify-between gap-3 border-b border-lilac-200 px-5 py-3.5">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="tabular text-sm font-semibold text-plum-800">
                      {entry.entryNo}
                    </span>
                    <Badge tone="brand">{entry.journalCode}</Badge>
                    <span className="text-xs text-muted-ink">{entry.journalName}</span>
                    <Badge tone="neutral">{titleCase(entry.sourceType)}</Badge>
                    <span className="text-xs text-muted-ink">{formatDate(entry.entryDate)}</span>
                  </div>
                  {entry.narration && (
                    <p className="mt-1 text-sm text-muted-ink">{entry.narration}</p>
                  )}
                </div>
                <div className="flex items-center gap-4">
                  {entry.balanced && (
                    <Badge tone="success">
                      <CheckCircle2 className="mr-1 h-3 w-3" />
                      Balanced
                    </Badge>
                  )}
                  <div className="text-right">
                    <p className="tabular text-sm font-semibold text-plum-800">
                      {formatMoney(entry.totalDebit)}
                    </p>
                    <p className="text-[10px] text-muted-ink uppercase">Debit = Credit</p>
                  </div>
                </div>
              </div>

              <Table>
                <THead>
                  <tr>
                    <TH>Account</TH>
                    <TH>Contact</TH>
                    <TH className="w-32">Project</TH>
                    <TH>Label</TH>
                    <TH className="w-36 text-right">Debit</TH>
                    <TH className="w-36 text-right">Credit</TH>
                  </tr>
                </THead>
                <tbody>
                  {entry.lines.map((line) => (
                    <TR key={line.id}>
                      <TD>
                        <span className="tabular mr-1.5 text-xs text-muted-ink">
                          {line.accountCode}
                        </span>
                        {line.accountName}
                      </TD>
                      <TD>{line.contactName ?? <span className="text-muted-ink">—</span>}</TD>
                      <TD className="tabular text-xs">
                        {line.analyticAccountCode ?? <span className="text-muted-ink">—</span>}
                      </TD>
                      <TD className="text-xs text-muted-ink">{line.label ?? '—'}</TD>
                      <TD className="tabular text-right">
                        {Number(line.debit) === 0 ? (
                          <span className="text-muted-ink">—</span>
                        ) : (
                          formatMoney(line.debit)
                        )}
                      </TD>
                      <TD className="tabular text-right">
                        {Number(line.credit) === 0 ? (
                          <span className="text-muted-ink">—</span>
                        ) : (
                          formatMoney(line.credit)
                        )}
                      </TD>
                    </TR>
                  ))}
                </tbody>
              </Table>
            </Card>
          ))}

          <div className="flex items-center justify-between">
            <p className="text-xs text-muted-ink">
              Page {(query.data?.page ?? 0) + 1} of {Math.max(query.data?.totalPages ?? 1, 1)} —{' '}
              {query.data?.totalElements ?? 0} entries
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
