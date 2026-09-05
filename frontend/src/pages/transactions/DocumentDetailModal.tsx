import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { ledgerApi } from '@/api/endpoints'
import type { DocumentResponse } from '@/api/types'
import { StatusBadge } from '@/components/ui/Badge'
import { Modal } from '@/components/ui/Modal'
import { EmptyState, InlineLoader } from '@/components/ui/PageLoader'
import { Table, TD, TH, THead, TR } from '@/components/ui/Table'
import { formatDate, formatMoney, formatNumber } from '@/lib/utils'

/**
 * Shows a posted document alongside the exact journal entry it generated -
 * the clearest demonstration that the ledger is derived from the document.
 */
export function DocumentDetailModal({
  open,
  document: doc,
  onClose,
}: {
  open: boolean
  document: DocumentResponse | null
  onClose: () => void
}) {
  const { data: entry, isLoading } = useQuery({
    queryKey: ['journal-entry', doc?.journalEntryId],
    queryFn: () => ledgerApi.get(doc!.journalEntryId!),
    enabled: open && !!doc?.journalEntryId,
  })

  if (!doc) return null

  return (
    <Modal
      open={open}
      onClose={onClose}
      size="lg"
      title={doc.documentNo}
      description={`${doc.contactName} · ${formatDate(doc.documentDate)}`}
    >
      <div className="space-y-5">
        <div className="flex flex-wrap items-center gap-4">
          <StatusBadge status={doc.status} />
          {doc.sourceOrderNo && (
            <span className="text-sm text-muted-ink">
              From order <span className="font-medium text-plum-800">{doc.sourceOrderNo}</span>
            </span>
          )}
          {doc.dueDate && (
            <span className="text-sm text-muted-ink">Due {formatDate(doc.dueDate)}</span>
          )}
        </div>

        <div>
          <p className="mb-2 text-xs font-medium tracking-wide text-muted-ink uppercase">Lines</p>
          <div className="overflow-hidden rounded-lg border border-lilac-200">
            <Table>
              <THead>
                <tr>
                  <TH>Product</TH>
                  <TH className="text-right">Qty</TH>
                  <TH className="text-right">Unit price</TH>
                  <TH className="text-right">Tax</TH>
                  <TH className="text-right">Total</TH>
                </tr>
              </THead>
              <tbody>
                {doc.lines.map((l) => (
                  <TR key={l.id}>
                    <TD>{l.productName}</TD>
                    <TD className="tabular text-right">{formatNumber(l.quantity, 3)}</TD>
                    <TD className="tabular text-right">{formatMoney(l.unitPrice)}</TD>
                    <TD className="tabular text-right">{formatMoney(l.taxAmount)}</TD>
                    <TD className="tabular text-right font-medium">{formatMoney(l.lineTotal)}</TD>
                  </TR>
                ))}
              </tbody>
            </Table>
          </div>
        </div>

        <div className="flex justify-end">
          <div className="w-full space-y-1 text-sm sm:w-64">
            <div className="flex justify-between text-muted-ink">
              <span>Untaxed</span>
              <span className="tabular">{formatMoney(doc.untaxedAmount)}</span>
            </div>
            <div className="flex justify-between text-muted-ink">
              <span>Tax</span>
              <span className="tabular">{formatMoney(doc.taxAmount)}</span>
            </div>
            <div className="flex justify-between border-t border-lilac-200 pt-1 font-semibold text-plum-800">
              <span>Total</span>
              <span className="tabular">{formatMoney(doc.totalAmount)}</span>
            </div>
            <div className="flex justify-between text-muted-ink">
              <span>Paid</span>
              <span className="tabular">{formatMoney(doc.amountPaid)}</span>
            </div>
            <div className="flex justify-between font-semibold text-amethyst-700">
              <span>Outstanding</span>
              <span className="tabular">{formatMoney(doc.amountDue)}</span>
            </div>
          </div>
        </div>

        <div>
          <p className="mb-2 text-xs font-medium tracking-wide text-muted-ink uppercase">
            Generated journal entry
          </p>

          {!doc.journalEntryId ? (
            <div className="rounded-lg border border-lilac-200 bg-lilac-50">
              <EmptyState
                title="Not posted yet"
                description="This document is still a draft, so it has no accounting effect. Post it to generate the journal entry."
              />
            </div>
          ) : isLoading ? (
            <div className="flex justify-center py-6">
              <InlineLoader />
            </div>
          ) : entry ? (
            <div className="overflow-hidden rounded-lg border border-lilac-200">
              <div className="flex flex-wrap items-center justify-between gap-2 border-b border-lilac-200 bg-lilac-50 px-4 py-2.5">
                <div>
                  <Link
                    to="/ledger"
                    className="text-sm font-medium text-amethyst-600 hover:underline"
                  >
                    {entry.entryNo}
                  </Link>
                  <span className="ml-2 text-xs text-muted-ink">
                    {entry.journalName} · {formatDate(entry.entryDate)}
                  </span>
                </div>
                <span className="rounded-full bg-emerald-50 px-2 py-0.5 text-xs font-medium text-emerald-700 ring-1 ring-emerald-600/20 ring-inset">
                  Balanced
                </span>
              </div>
              <Table>
                <THead>
                  <tr>
                    <TH>Account</TH>
                    <TH className="text-right">Debit</TH>
                    <TH className="text-right">Credit</TH>
                  </tr>
                </THead>
                <tbody>
                  {entry.lines.map((l) => (
                    <TR key={l.id}>
                      <TD>
                        <span className="tabular text-muted-ink">{l.accountCode}</span>{' '}
                        {l.accountName}
                      </TD>
                      <TD className="tabular text-right">
                        {Number(l.debit) > 0 ? formatMoney(l.debit) : '—'}
                      </TD>
                      <TD className="tabular text-right">
                        {Number(l.credit) > 0 ? formatMoney(l.credit) : '—'}
                      </TD>
                    </TR>
                  ))}
                  <tr className="bg-lilac-50 font-semibold">
                    <TD>Total</TD>
                    <TD className="tabular text-right">{formatMoney(entry.totalDebit)}</TD>
                    <TD className="tabular text-right">{formatMoney(entry.totalCredit)}</TD>
                  </tr>
                </tbody>
              </Table>
            </div>
          ) : null}
        </div>
      </div>
    </Modal>
  )
}
