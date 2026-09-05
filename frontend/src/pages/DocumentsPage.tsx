import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { FileText, Receipt, ScrollText, ShoppingCart } from 'lucide-react'
import { errorMessage } from '@/api/client'
import { ledgerApi, tradeApi } from '@/api/endpoints'
import type { DocumentKind, TaxTreatment } from '@/api/types'
import { PageHeader } from '@/components/layout/AppLayout'
import { Badge, StatusBadge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { Modal } from '@/components/ui/Modal'
import { EmptyState, ErrorState, PageLoader } from '@/components/ui/PageLoader'
import { EmptyRow, Table, TD, TH, THead, TR } from '@/components/ui/Table'
import { cn, formatDate, formatMoney, formatNumber } from '@/lib/utils'

const TABS: {
  kind: DocumentKind
  label: string
  icon: typeof FileText
  blurb: string
  counterpartyLabel: string
}[] = [
  {
    kind: 'PURCHASE_ORDER',
    label: 'Purchase Orders',
    icon: ShoppingCart,
    blurb: 'What you have agreed to buy. No ledger impact until the bill arrives.',
    counterpartyLabel: 'Supplier',
  },
  {
    kind: 'SALES_ORDER',
    label: 'Sales Orders',
    icon: ScrollText,
    blurb: 'What you have agreed to sell. No ledger impact until you invoice.',
    counterpartyLabel: 'Customer',
  },
  {
    kind: 'BILL',
    label: 'Bills',
    icon: FileText,
    blurb: 'What you owe. Each posted bill carries its own journal entry.',
    counterpartyLabel: 'Supplier',
  },
  {
    kind: 'INVOICE',
    label: 'Invoices',
    icon: Receipt,
    blurb: 'What you are owed. Each posted invoice carries its own journal entry.',
    counterpartyLabel: 'Customer',
  },
]

function taxTreatmentBadge(treatment: TaxTreatment) {
  if (treatment === 'INTRA_STATE') return <Badge tone="info">Intra-state · CGST + SGST</Badge>
  if (treatment === 'INTER_STATE') return <Badge tone="brand">Inter-state · IGST</Badge>
  return <Badge tone="neutral">Tax treatment not set</Badge>
}

export function DocumentsPage() {
  const [kind, setKind] = useState<DocumentKind>('INVOICE')
  const [openId, setOpenId] = useState<number | null>(null)

  const tab = TABS.find((t) => t.kind === kind) ?? TABS[3]

  const query = useQuery({
    queryKey: ['documents', kind],
    queryFn: () => tradeApi.documents({ docType: kind, size: 100 }),
  })

  const rows = query.data?.content ?? []

  return (
    <>
      <PageHeader title="Documents" description={tab.blurb} />

      <div className="mb-4 flex flex-wrap gap-1 rounded-lg border border-lilac-200 bg-white p-1">
        {TABS.map((t) => (
          <button
            key={t.kind}
            type="button"
            onClick={() => setKind(t.kind)}
            aria-pressed={kind === t.kind}
            className={cn(
              'inline-flex items-center gap-2 rounded-md px-3.5 py-1.5 text-sm font-medium transition-colors',
              kind === t.kind
                ? 'bg-amethyst-600 text-white'
                : 'text-muted-ink hover:bg-lilac-50 hover:text-plum-800',
            )}
          >
            <t.icon className="h-4 w-4" />
            {t.label}
          </button>
        ))}
      </div>

      <Card>
        {query.isPending ? (
          <PageLoader label="Loading documents…" />
        ) : query.isError ? (
          <ErrorState
            message={errorMessage(query.error)}
            action={
              <Button variant="outline" onClick={() => void query.refetch()}>
                Try again
              </Button>
            }
          />
        ) : rows.length === 0 ? (
          <EmptyState
            icon={<tab.icon className="h-8 w-8" />}
            title={`No ${tab.label.toLowerCase()} yet`}
            description="Documents are generated as deals move through their lifecycle."
          />
        ) : (
          <Table>
            <THead>
              <tr>
                <TH>Doc no</TH>
                <TH>Deal no</TH>
                <TH>{tab.counterpartyLabel}</TH>
                <TH>Date</TH>
                <TH>Due</TH>
                <TH>Status</TH>
                <TH className="text-right">Total</TH>
                <TH className="text-right">Due amount</TH>
              </tr>
            </THead>
            <tbody>
              {rows.map((doc) => {
                const overdue =
                  Number(doc.amountDue) > 0 &&
                  Boolean(doc.dueDate) &&
                  new Date(doc.dueDate as string) < new Date()
                return (
                  <TR
                    key={doc.id}
                    className="cursor-pointer"
                    onClick={() => setOpenId(doc.id)}
                    title="View document"
                  >
                    <TD className="font-medium whitespace-nowrap">{doc.docNo}</TD>
                    <TD className="whitespace-nowrap text-muted-ink">{doc.dealNo}</TD>
                    <TD>
                      <span className="block max-w-56 truncate">{doc.counterpartyName ?? '—'}</span>
                    </TD>
                    <TD className="whitespace-nowrap">{formatDate(doc.docDate)}</TD>
                    <TD className={cn('whitespace-nowrap', overdue && 'font-medium text-red-600')}>
                      {formatDate(doc.dueDate)}
                    </TD>
                    <TD>
                      <StatusBadge status={doc.status} />
                    </TD>
                    <TD className="text-right whitespace-nowrap">
                      <span className="tabular">{formatMoney(doc.totalAmount)}</span>
                    </TD>
                    <TD className="text-right whitespace-nowrap">
                      <span
                        className={cn('tabular', Number(doc.amountDue) > 0 && 'font-medium')}
                      >
                        {formatMoney(doc.amountDue)}
                      </span>
                    </TD>
                  </TR>
                )
              })}
            </tbody>
          </Table>
        )}
      </Card>

      {openId !== null && (
        <DocumentDetailModal documentId={openId} onClose={() => setOpenId(null)} />
      )}
    </>
  )
}

function DocumentDetailModal({
  documentId,
  onClose,
}: {
  documentId: number
  onClose: () => void
}) {
  const docQuery = useQuery({
    queryKey: ['document', documentId],
    queryFn: () => tradeApi.document(documentId),
  })
  const doc = docQuery.data

  // Orders never post, so the entry only exists once the document is a bill
  // or an invoice that has been posted.
  const entryId = doc?.journalEntryId ?? null
  const entryQuery = useQuery({
    queryKey: ['journal-entry', entryId],
    queryFn: () => ledgerApi.entry(entryId as number),
    enabled: entryId !== null,
  })

  return (
    <Modal
      open
      onClose={onClose}
      size="xl"
      title={doc ? doc.docNo : 'Document'}
      description={doc ? `Deal ${doc.dealNo} · ${doc.counterpartyName ?? 'Unknown party'}` : undefined}
      footer={
        <Button variant="outline" onClick={onClose}>
          Close
        </Button>
      }
    >
      {docQuery.isPending ? (
        <PageLoader label="Loading document…" />
      ) : docQuery.isError || !doc ? (
        <ErrorState message={errorMessage(docQuery.error)} />
      ) : (
        <div className="space-y-6">
          <div className="flex flex-wrap items-center gap-2">
            <StatusBadge status={doc.status} />
            {taxTreatmentBadge(doc.taxTreatment)}
            {doc.placeOfSupply && <Badge tone="neutral">Place of supply: {doc.placeOfSupply}</Badge>}
            <span className="text-xs text-muted-ink">
              Dated {formatDate(doc.docDate)}
              {doc.dueDate ? ` · due ${formatDate(doc.dueDate)}` : ''}
            </span>
          </div>

          {/* Document and its ledger consequence, shown together. */}
          <div className="grid gap-5 lg:grid-cols-2">
            <section className="rounded-xl border border-lilac-200">
              <div className="flex items-center justify-between border-b border-lilac-200 bg-lilac-50/60 px-4 py-2.5">
                <h3 className="text-sm font-semibold text-plum-800">Document lines</h3>
                <span className="text-xs text-muted-ink">
                  {doc.lines.length} {doc.lines.length === 1 ? 'line' : 'lines'}
                </span>
              </div>

              <Table>
                <THead>
                  <tr>
                    <TH>Description</TH>
                    <TH className="text-right">Qty</TH>
                    <TH className="text-right">Rate</TH>
                    <TH className="text-right">Untaxed</TH>
                    <TH className="text-right">CGST</TH>
                    <TH className="text-right">SGST</TH>
                    <TH className="text-right">IGST</TH>
                    <TH className="text-right">Total</TH>
                  </tr>
                </THead>
                <tbody>
                  {doc.lines.length === 0 ? (
                    <EmptyRow colSpan={8}>This document has no lines.</EmptyRow>
                  ) : (
                    doc.lines.map((line) => (
                      <TR key={line.id}>
                        <TD>
                          <span className="block max-w-56 truncate">{line.description}</span>
                          <span className="text-xs text-muted-ink">
                            {line.hsnCode ? `HSN ${line.hsnCode} · ` : ''}
                            {formatNumber(line.taxRate, 0)}% GST
                            {line.analyticAccountName ? ` · ${line.analyticAccountName}` : ''}
                          </span>
                        </TD>
                        <TD className="text-right">
                          <span className="tabular">{formatNumber(line.quantity)}</span>
                        </TD>
                        <TD className="text-right">
                          <span className="tabular">{formatMoney(line.unitPrice)}</span>
                        </TD>
                        <TD className="text-right">
                          <span className="tabular">{formatMoney(line.untaxedAmount)}</span>
                        </TD>
                        <TD className="text-right text-muted-ink">
                          <span className="tabular">{formatMoney(line.cgstAmount)}</span>
                        </TD>
                        <TD className="text-right text-muted-ink">
                          <span className="tabular">{formatMoney(line.sgstAmount)}</span>
                        </TD>
                        <TD className="text-right text-muted-ink">
                          <span className="tabular">{formatMoney(line.igstAmount)}</span>
                        </TD>
                        <TD className="text-right font-medium">
                          <span className="tabular">{formatMoney(line.lineTotal)}</span>
                        </TD>
                      </TR>
                    ))
                  )}
                </tbody>
              </Table>

              <dl className="space-y-1.5 border-t border-lilac-200 px-4 py-3.5 text-sm">
                <Amount label="Untaxed" value={doc.untaxedAmount} />
                <Amount label="CGST" value={doc.cgstAmount} muted />
                <Amount label="SGST" value={doc.sgstAmount} muted />
                <Amount label="IGST" value={doc.igstAmount} muted />
                <Amount label="Tax total" value={doc.taxAmount} />
                <div className="flex items-center justify-between border-t border-lilac-200 pt-2 text-base font-semibold text-plum-800">
                  <dt>Total</dt>
                  <dd className="tabular">{formatMoney(doc.totalAmount)}</dd>
                </div>
                <Amount label="Settled" value={doc.amountSettled} muted />
                <div className="flex items-center justify-between font-medium text-plum-800">
                  <dt>Amount due</dt>
                  <dd className="tabular">{formatMoney(doc.amountDue)}</dd>
                </div>
              </dl>
            </section>

            <section className="rounded-xl border border-lilac-200">
              <div className="flex items-center justify-between gap-3 border-b border-lilac-200 bg-lilac-50/60 px-4 py-2.5">
                <div className="min-w-0">
                  <h3 className="text-sm font-semibold text-plum-800">Journal entry</h3>
                  {entryQuery.data && (
                    <p className="truncate text-xs text-muted-ink">
                      {entryQuery.data.entryNo} · {entryQuery.data.journalName} ·{' '}
                      {formatDate(entryQuery.data.entryDate)}
                    </p>
                  )}
                </div>
                {entryQuery.data && (
                  <Badge tone={entryQuery.data.balanced ? 'success' : 'danger'}>
                    {entryQuery.data.balanced ? 'Balanced' : 'Out of balance'}
                  </Badge>
                )}
              </div>

              {entryId === null ? (
                <EmptyState
                  title="Nothing posted yet"
                  description="Orders are commitments, not accounting events. A journal entry appears once this deal is invoiced."
                />
              ) : entryQuery.isPending ? (
                <PageLoader label="Loading entry…" />
              ) : entryQuery.isError || !entryQuery.data ? (
                <ErrorState message={errorMessage(entryQuery.error)} />
              ) : (
                <>
                  <Table>
                    <THead>
                      <tr>
                        <TH className="w-20">Code</TH>
                        <TH>Account</TH>
                        <TH className="text-right">Debit</TH>
                        <TH className="text-right">Credit</TH>
                      </tr>
                    </THead>
                    <tbody>
                      {entryQuery.data.lines.map((line) => (
                        <TR key={line.id}>
                          <TD className="tabular align-top text-muted-ink">{line.accountCode}</TD>
                          <TD className="align-top">
                            <span className="block max-w-56 truncate font-medium">
                              {line.accountName}
                            </span>
                            {line.label && (
                              <span className="block max-w-56 truncate text-xs text-muted-ink">
                                {line.label}
                              </span>
                            )}
                          </TD>
                          <TD className="text-right align-top">
                            {Number(line.debit) > 0 ? (
                              <span className="tabular">{formatMoney(line.debit)}</span>
                            ) : (
                              <span className="text-muted-ink">—</span>
                            )}
                          </TD>
                          <TD className="text-right align-top">
                            {Number(line.credit) > 0 ? (
                              <span className="tabular">{formatMoney(line.credit)}</span>
                            ) : (
                              <span className="text-muted-ink">—</span>
                            )}
                          </TD>
                        </TR>
                      ))}
                    </tbody>
                  </Table>

                  <div className="flex items-center justify-between gap-4 border-t border-lilac-200 bg-lilac-50/40 px-4 py-3 text-sm font-semibold text-plum-800">
                    <span>Totals</span>
                    <span className="flex gap-8">
                      <span className="tabular">{formatMoney(entryQuery.data.totalDebit)}</span>
                      <span className="tabular">{formatMoney(entryQuery.data.totalCredit)}</span>
                    </span>
                  </div>

                  {entryQuery.data.narration && (
                    <p className="border-t border-lilac-200 px-4 py-3 text-xs text-muted-ink">
                      {entryQuery.data.narration}
                    </p>
                  )}
                </>
              )}
            </section>
          </div>
        </div>
      )}
    </Modal>
  )
}

function Amount({ label, value, muted }: { label: string; value: string; muted?: boolean }) {
  return (
    <div className={cn('flex items-center justify-between', muted ? 'text-muted-ink' : 'text-plum-800')}>
      <dt>{label}</dt>
      <dd className={cn('tabular', !muted && 'text-plum-800')}>{formatMoney(value)}</dd>
    </div>
  )
}
