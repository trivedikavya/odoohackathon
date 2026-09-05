import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  ArrowLeftRight,
  Ban,
  Check,
  Handshake,
  Inbox,
  PackageCheck,
  Plus,
  Receipt,
  Send,
  Trash2,
  Wallet,
  X,
} from 'lucide-react'
import { errorMessage } from '@/api/client'
import { masterApi, tradeApi } from '@/api/endpoints'
import type {
  CreateDealRequest,
  Deal,
  DealLineRequest,
  DealStatus,
  DocumentSummary,
  PaymentMethod,
  SettlementRequest,
  TaxTreatment,
} from '@/api/types'
import { useAuth } from '@/auth/AuthContext'
import { PageHeader } from '@/components/layout/AppLayout'
import { Badge, StatusBadge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { FormRow, Input, Label, Select, Textarea } from '@/components/ui/Field'
import { Modal } from '@/components/ui/Modal'
import { EmptyState, ErrorState, InlineLoader, PageLoader } from '@/components/ui/PageLoader'
import { EmptyRow, Table, TD, TH, THead, TR } from '@/components/ui/Table'
import { useToast } from '@/components/ui/Toast'
import { addDays, cn, formatDate, formatMoney, formatNumber, today } from '@/lib/utils'

/** Before invoicing there is nothing on the ledger, so a deal can still be pulled. */
const CANCELLABLE: DealStatus[] = ['RFQ_DRAFT', 'RFQ_SENT', 'ACCEPTED', 'DELIVERED']
const SETTLEABLE: DealStatus[] = ['INVOICED', 'PARTIALLY_PAID']

function taxTreatmentBadge(treatment: TaxTreatment) {
  if (treatment === 'INTRA_STATE') return <Badge tone="info">Intra-state · CGST + SGST</Badge>
  if (treatment === 'INTER_STATE') return <Badge tone="brand">Inter-state · IGST</Badge>
  return <Badge tone="neutral">Tax treatment not set</Badge>
}

/** The payable/receivable document for a deal — orders never carry a balance. */
function settlementDocument(deal: Deal): DocumentSummary | undefined {
  return deal.myDocuments.find((d) => d.docType === 'INVOICE' || d.docType === 'BILL')
}

function outstandingOf(deal: Deal): string {
  return settlementDocument(deal)?.amountDue ?? deal.totalAmount
}

type Tab = 'all' | 'inbox'

export function DealsPage() {
  const { user } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()

  const [tab, setTab] = useState<Tab>('all')
  const [detailId, setDetailId] = useState<number | null>(null)
  const [creating, setCreating] = useState(false)
  const [rejecting, setRejecting] = useState<Deal | null>(null)
  const [settling, setSettling] = useState<Deal | null>(null)

  const dealsQuery = useQuery({
    queryKey: ['deals'],
    queryFn: () => tradeApi.deals({ size: 100 }),
  })

  const inboxQuery = useQuery({
    queryKey: ['deal-inbox'],
    queryFn: () => tradeApi.inbox({ size: 100 }),
  })

  const invalidate = () => {
    // Every deal action can move money, documents and the dashboard at once.
    void queryClient.invalidateQueries({ queryKey: ['deals'] })
    void queryClient.invalidateQueries({ queryKey: ['deal-inbox'] })
    void queryClient.invalidateQueries({ queryKey: ['dashboard'] })
    void queryClient.invalidateQueries({ queryKey: ['documents'] })
  }

  const onError = (err: unknown) => toast.error(errorMessage(err))

  const settled = (message: string) => () => {
    invalidate()
    toast.success(message)
  }

  const sendM = useMutation({
    mutationFn: (id: number) => tradeApi.send(id),
    onSuccess: settled('Request sent to the supplier.'),
    onError,
  })

  const acceptM = useMutation({
    mutationFn: (id: number) => tradeApi.accept(id),
    onSuccess: settled('Deal accepted.'),
    onError,
  })

  const rejectM = useMutation({
    mutationFn: (vars: { id: number; reason?: string }) => tradeApi.reject(vars.id, vars.reason),
    onSuccess: () => {
      invalidate()
      toast.success('Deal rejected.')
      setRejecting(null)
    },
    onError,
  })

  const deliverM = useMutation({
    mutationFn: (id: number) => tradeApi.deliver(id, today()),
    onSuccess: settled('Marked as delivered.'),
    onError,
  })

  const invoiceM = useMutation({
    mutationFn: (id: number) => tradeApi.invoice(id, { docDate: today(), dueDate: addDays(today(), 30) }),
    onSuccess: settled('Invoice raised — the journal entry has been posted.'),
    onError,
  })

  const settleM = useMutation({
    mutationFn: (vars: { id: number; body: SettlementRequest }) =>
      tradeApi.settle(vars.id, vars.body),
    onSuccess: () => {
      invalidate()
      toast.success('Payment recorded.')
      setSettling(null)
    },
    onError,
  })

  const cancelM = useMutation({
    mutationFn: (id: number) => tradeApi.cancel(id),
    onSuccess: settled('Deal cancelled.'),
    onError,
  })

  const activeQuery = tab === 'all' ? dealsQuery : inboxQuery
  const rows = activeQuery.data?.content ?? []
  const inboxCount = inboxQuery.data?.totalElements ?? 0

  const busyWith = (id: number) =>
    (sendM.isPending && sendM.variables === id) ||
    (acceptM.isPending && acceptM.variables === id) ||
    (deliverM.isPending && deliverM.variables === id) ||
    (invoiceM.isPending && invoiceM.variables === id) ||
    (cancelM.isPending && cancelM.variables === id)

  return (
    <>
      <PageHeader
        title="Deals"
        description="A deal is the one trade both sides share. Nothing reaches the ledger until it is invoiced."
        action={
          <Button onClick={() => setCreating(true)}>
            <Plus className="h-4 w-4" />
            New request
          </Button>
        }
      />

      <div className="mb-4 inline-flex rounded-lg border border-lilac-200 bg-white p-1">
        <TabButton active={tab === 'all'} onClick={() => setTab('all')} icon={ArrowLeftRight}>
          All deals
        </TabButton>
        <TabButton active={tab === 'inbox'} onClick={() => setTab('inbox')} icon={Inbox}>
          Inbox
          {inboxCount > 0 && (
            <span
              className={cn(
                'ml-1.5 inline-flex h-5 min-w-5 items-center justify-center rounded-full px-1.5 text-[11px] font-semibold',
                tab === 'inbox' ? 'bg-white/20 text-white' : 'bg-amethyst-600 text-white',
              )}
            >
              {inboxCount}
            </span>
          )}
        </TabButton>
      </div>

      <Card>
        {activeQuery.isPending ? (
          <PageLoader label="Loading deals…" />
        ) : activeQuery.isError ? (
          <ErrorState
            message={errorMessage(activeQuery.error)}
            action={
              <Button variant="outline" onClick={() => void activeQuery.refetch()}>
                Try again
              </Button>
            }
          />
        ) : rows.length === 0 ? (
          <EmptyState
            icon={<Handshake className="h-8 w-8" />}
            title={tab === 'inbox' ? 'Nothing awaiting you' : 'No deals yet'}
            description={
              tab === 'inbox'
                ? 'When a counterparty sends you a request, it lands here for you to accept or reject.'
                : 'Raise a request to a supplier to start your first deal.'
            }
            action={
              tab === 'all' ? (
                <Button onClick={() => setCreating(true)}>
                  <Plus className="h-4 w-4" />
                  New request
                </Button>
              ) : undefined
            }
          />
        ) : (
          <Table>
            <THead>
              <tr>
                <TH>Deal no</TH>
                <TH>Counterparty</TH>
                <TH>Direction</TH>
                <TH>Date</TH>
                <TH>Status</TH>
                <TH className="text-right">Total</TH>
                <TH className="text-right">Actions</TH>
              </tr>
            </THead>
            <tbody>
              {rows.map((deal) => (
                <TR
                  key={deal.id}
                  className="cursor-pointer"
                  onClick={() => setDetailId(deal.id)}
                  title="View deal"
                >
                  <TD className="font-medium whitespace-nowrap">{deal.dealNo}</TD>
                  <TD>
                    <span className="block max-w-56 truncate">
                      {deal.iAmSeller ? deal.buyerName : deal.sellerName}
                    </span>
                  </TD>
                  <TD>
                    <Badge tone={deal.iAmSeller ? 'success' : 'info'}>
                      {deal.iAmSeller ? 'Selling' : 'Buying'}
                    </Badge>
                  </TD>
                  <TD className="whitespace-nowrap">{formatDate(deal.dealDate)}</TD>
                  <TD>
                    <StatusBadge status={deal.status} />
                  </TD>
                  <TD className="text-right whitespace-nowrap">
                    <span className="tabular">{formatMoney(deal.totalAmount)}</span>
                  </TD>
                  {/* Row click opens the detail modal, so actions must not bubble. */}
                  <TD onClick={(e) => e.stopPropagation()}>
                    <div className="flex flex-wrap justify-end gap-1.5">
                      {deal.status === 'RFQ_DRAFT' && deal.initiatedByPartyId === user?.partyId && (
                        <Button
                          size="sm"
                          onClick={() => sendM.mutate(deal.id)}
                          loading={sendM.isPending && sendM.variables === deal.id}
                          disabled={busyWith(deal.id)}
                        >
                          <Send className="h-3.5 w-3.5" />
                          Send
                        </Button>
                      )}

                      {deal.awaitingMyDecision && (
                        <>
                          <Button
                            size="sm"
                            onClick={() => acceptM.mutate(deal.id)}
                            loading={acceptM.isPending && acceptM.variables === deal.id}
                            disabled={busyWith(deal.id)}
                          >
                            <Check className="h-3.5 w-3.5" />
                            Accept
                          </Button>
                          <Button
                            size="sm"
                            variant="outline"
                            onClick={() => setRejecting(deal)}
                            disabled={busyWith(deal.id)}
                          >
                            <X className="h-3.5 w-3.5" />
                            Reject
                          </Button>
                        </>
                      )}

                      {deal.status === 'ACCEPTED' && deal.iAmSeller && (
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => deliverM.mutate(deal.id)}
                          loading={deliverM.isPending && deliverM.variables === deal.id}
                          disabled={busyWith(deal.id)}
                        >
                          <PackageCheck className="h-3.5 w-3.5" />
                          Mark delivered
                        </Button>
                      )}

                      {deal.status === 'DELIVERED' && deal.iAmSeller && (
                        <Button
                          size="sm"
                          onClick={() => invoiceM.mutate(deal.id)}
                          loading={invoiceM.isPending && invoiceM.variables === deal.id}
                          disabled={busyWith(deal.id)}
                        >
                          <Receipt className="h-3.5 w-3.5" />
                          Invoice
                        </Button>
                      )}

                      {SETTLEABLE.includes(deal.status) && (
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => setSettling(deal)}
                          disabled={busyWith(deal.id)}
                        >
                          <Wallet className="h-3.5 w-3.5" />
                          Record payment
                        </Button>
                      )}

                      {CANCELLABLE.includes(deal.status) && (
                        <Button
                          size="sm"
                          variant="ghost"
                          className="text-red-600 hover:bg-red-50 hover:text-red-700"
                          onClick={() => cancelM.mutate(deal.id)}
                          loading={cancelM.isPending && cancelM.variables === deal.id}
                          disabled={busyWith(deal.id)}
                        >
                          <Ban className="h-3.5 w-3.5" />
                          Cancel
                        </Button>
                      )}
                    </div>
                  </TD>
                </TR>
              ))}
              {rows.length === 0 && <EmptyRow colSpan={7}>No deals to show.</EmptyRow>}
            </tbody>
          </Table>
        )}
      </Card>

      {detailId !== null && (
        <DealDetailModal dealId={detailId} onClose={() => setDetailId(null)} />
      )}

      {creating && (
        <NewDealModal
          onClose={() => setCreating(false)}
          onCreated={() => {
            invalidate()
            setCreating(false)
          }}
        />
      )}

      {rejecting && (
        <RejectModal
          deal={rejecting}
          submitting={rejectM.isPending}
          onClose={() => setRejecting(null)}
          onConfirm={(reason) => rejectM.mutate({ id: rejecting.id, reason })}
        />
      )}

      {settling && (
        <SettleModal
          deal={settling}
          submitting={settleM.isPending}
          onClose={() => setSettling(null)}
          onConfirm={(body) => settleM.mutate({ id: settling.id, body })}
        />
      )}
    </>
  )
}

function TabButton({
  active,
  onClick,
  icon: Icon,
  children,
}: {
  active: boolean
  onClick: () => void
  icon: typeof Inbox
  children: React.ReactNode
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      className={cn(
        'inline-flex items-center gap-2 rounded-md px-3.5 py-1.5 text-sm font-medium transition-colors',
        active ? 'bg-amethyst-600 text-white' : 'text-muted-ink hover:bg-lilac-50 hover:text-plum-800',
      )}
    >
      <Icon className="h-4 w-4" />
      {children}
    </button>
  )
}

/* ------------------------------------------------------------------ */
/* Detail                                                              */
/* ------------------------------------------------------------------ */

function DealDetailModal({ dealId, onClose }: { dealId: number; onClose: () => void }) {
  const query = useQuery({ queryKey: ['deal', dealId], queryFn: () => tradeApi.deal(dealId) })
  const deal = query.data

  return (
    <Modal
      open
      onClose={onClose}
      size="xl"
      title={deal ? `Deal ${deal.dealNo}` : 'Deal'}
      description={deal ? `Raised ${formatDate(deal.dealDate)}` : undefined}
      footer={
        <Button variant="outline" onClick={onClose}>
          Close
        </Button>
      }
    >
      {query.isPending ? (
        <PageLoader label="Loading deal…" />
      ) : query.isError || !deal ? (
        <ErrorState message={errorMessage(query.error)} />
      ) : (
        <div className="space-y-6">
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <Fact label="Seller">
              <span className="font-medium text-plum-800">{deal.sellerName}</span>
              <span className="block text-xs text-muted-ink">{deal.sellerType}</span>
            </Fact>
            <Fact label="Buyer">
              <span className="font-medium text-plum-800">{deal.buyerName}</span>
              <span className="block text-xs text-muted-ink">{deal.buyerType}</span>
            </Fact>
            <Fact label="Status">
              <div className="flex flex-wrap gap-1.5">
                <StatusBadge status={deal.status} />
                <Badge tone={deal.iAmSeller ? 'success' : 'info'}>
                  {deal.iAmSeller ? 'Selling' : 'Buying'}
                </Badge>
              </div>
            </Fact>
            <Fact label="Place of supply">
              <span className="text-plum-800">{deal.placeOfSupply ?? '—'}</span>
              <span className="mt-1.5 block">{taxTreatmentBadge(deal.taxTreatment)}</span>
            </Fact>
          </div>

          <div className="grid gap-3 sm:grid-cols-3">
            <Fact label="Expected delivery">{formatDate(deal.expectedDelivery)}</Fact>
            <Fact label="Delivered">{formatDate(deal.deliveredAt)}</Fact>
            <Fact label="Notes">{deal.notes?.trim() || '—'}</Fact>
          </div>

          {deal.status === 'REJECTED' && (
            <div className="rounded-lg border border-red-200 bg-red-50 px-3.5 py-3 text-sm text-red-700">
              Rejected{deal.rejectReason ? `: ${deal.rejectReason}` : '.'}
            </div>
          )}

          <div className="overflow-hidden rounded-lg border border-lilac-200">
            <Table>
              <THead>
                <tr>
                  <TH className="w-10">#</TH>
                  <TH>Description</TH>
                  <TH>HSN</TH>
                  <TH className="text-right">Qty</TH>
                  <TH className="text-right">Rate</TH>
                  <TH className="text-right">Tax %</TH>
                  <TH className="text-right">Untaxed</TH>
                  <TH className="text-right">CGST</TH>
                  <TH className="text-right">SGST</TH>
                  <TH className="text-right">IGST</TH>
                  <TH className="text-right">Total</TH>
                </tr>
              </THead>
              <tbody>
                {deal.lines.length === 0 ? (
                  <EmptyRow colSpan={11}>This deal has no lines.</EmptyRow>
                ) : (
                  deal.lines.map((line) => (
                    <TR key={line.id}>
                      <TD className="text-muted-ink">{line.lineNo}</TD>
                      <TD>{line.description}</TD>
                      <TD className="text-muted-ink">{line.hsnCode ?? '—'}</TD>
                      <TD className="text-right">
                        <span className="tabular">{formatNumber(line.quantity)}</span>
                      </TD>
                      <TD className="text-right">
                        <span className="tabular">{formatMoney(line.unitPrice)}</span>
                      </TD>
                      <TD className="text-right">
                        <span className="tabular">{formatNumber(line.taxRate, 0)}%</span>
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
          </div>

          <div className="flex justify-end">
            <dl className="w-full max-w-xs space-y-1.5 text-sm sm:w-72">
              <TotalRow label="Untaxed" value={deal.untaxedAmount} />
              <TotalRow label="Tax" value={deal.taxAmount} />
              <div className="flex items-center justify-between border-t border-lilac-200 pt-2 text-base font-semibold text-plum-800">
                <dt>Total</dt>
                <dd className="tabular">{formatMoney(deal.totalAmount)}</dd>
              </div>
            </dl>
          </div>

          <div>
            <p className="mb-2 text-xs font-medium tracking-wide text-muted-ink uppercase">
              My documents
            </p>
            {deal.myDocuments.length === 0 ? (
              <p className="text-sm text-muted-ink">
                No documents yet — they are created as the deal progresses.
              </p>
            ) : (
              <div className="flex flex-wrap gap-2">
                {deal.myDocuments.map((doc) => (
                  <span
                    key={doc.id}
                    className="inline-flex items-center gap-2 rounded-full border border-lilac-200 bg-lilac-50 py-1 pr-3 pl-1.5 text-xs"
                  >
                    <span className="rounded-full bg-white px-2 py-0.5 font-medium text-plum-800">
                      {doc.docNo}
                    </span>
                    <StatusBadge status={doc.status} />
                    <span className="tabular text-muted-ink">
                      {formatMoney(doc.amountDue)} due
                    </span>
                  </span>
                ))}
              </div>
            )}
          </div>

          {deal.mirrored && (
            <p className="flex gap-2 rounded-lg bg-lilac-50 px-3.5 py-3 text-xs leading-relaxed text-muted-ink">
              <ArrowLeftRight className="mt-0.5 h-3.5 w-3.5 shrink-0 text-amethyst-600" />
              <span>
                Both parties keep books, so this deal is mirrored: the counterparty sees the same
                trade from the other side, with their own order, {deal.iAmSeller ? 'bill' : 'invoice'}{' '}
                and journal entries.
              </span>
            </p>
          )}
        </div>
      )}
    </Modal>
  )
}

function Fact({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="rounded-lg border border-lilac-200 bg-lilac-50/50 px-3.5 py-3">
      <p className="text-[10px] font-semibold tracking-widest text-muted-ink uppercase">{label}</p>
      <div className="mt-1 text-sm text-plum-800">{children}</div>
    </div>
  )
}

function TotalRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between text-muted-ink">
      <dt>{label}</dt>
      <dd className="tabular text-plum-800">{formatMoney(value)}</dd>
    </div>
  )
}

/* ------------------------------------------------------------------ */
/* New request                                                         */
/* ------------------------------------------------------------------ */

interface DraftLine {
  key: number
  description: string
  hsnCode: string
  quantity: string
  unitPrice: string
  taxRate: string
}

let lineKey = 0
const blankLine = (): DraftLine => ({
  key: ++lineKey,
  description: '',
  hsnCode: '',
  quantity: '1',
  unitPrice: '',
  taxRate: '18',
})

function NewDealModal({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) {
  const toast = useToast()

  const [sellerPartyId, setSellerPartyId] = useState('')
  const [dealDate, setDealDate] = useState(today())
  const [expectedDelivery, setExpectedDelivery] = useState(addDays(today(), 7))
  const [notes, setNotes] = useState('')
  const [lines, setLines] = useState<DraftLine[]>([blankLine()])
  const [showErrors, setShowErrors] = useState(false)

  const suppliersQuery = useQuery({ queryKey: ['suppliers'], queryFn: () => masterApi.suppliers() })

  const createM = useMutation({
    mutationFn: (body: CreateDealRequest) => tradeApi.createDeal(body),
    onSuccess: (deal) => {
      toast.success(`Draft ${deal.dealNo} created. Send it when you are ready.`)
      onCreated()
    },
    onError: (err: unknown) => toast.error(errorMessage(err)),
  })

  const updateLine = (key: number, patch: Partial<DraftLine>) =>
    setLines((prev) => prev.map((l) => (l.key === key ? { ...l, ...patch } : l)))

  const totals = useMemo(() => {
    let untaxed = 0
    let tax = 0
    for (const line of lines) {
      const qty = Number(line.quantity) || 0
      const price = Number(line.unitPrice) || 0
      const rate = Number(line.taxRate) || 0
      const base = qty * price
      untaxed += base
      tax += (base * rate) / 100
    }
    return { untaxed, tax, total: untaxed + tax }
  }, [lines])

  const validLines = lines.filter(
    (l) => l.description.trim() && Number(l.quantity) > 0 && Number(l.unitPrice) >= 0,
  )
  const canSubmit = Boolean(sellerPartyId) && Boolean(dealDate) && validLines.length > 0

  const submit = () => {
    if (!canSubmit) {
      setShowErrors(true)
      return
    }
    const body: CreateDealRequest = {
      sellerPartyId: Number(sellerPartyId),
      dealDate,
      expectedDelivery: expectedDelivery || undefined,
      notes: notes.trim() || undefined,
      lines: validLines.map<DealLineRequest>((l) => ({
        description: l.description.trim(),
        hsnCode: l.hsnCode.trim() || undefined,
        quantity: String(Number(l.quantity)),
        unitPrice: String(Number(l.unitPrice)),
        taxRate: l.taxRate.trim() ? String(Number(l.taxRate)) : undefined,
      })),
    }
    createM.mutate(body)
  }

  return (
    <Modal
      open
      onClose={onClose}
      size="xl"
      title="New request for quotation"
      description="Pick who you are buying from and list what you need. It is saved as a draft until you send it."
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button onClick={submit} loading={createM.isPending} disabled={!canSubmit}>
            Create draft
          </Button>
        </>
      }
    >
      <div className="space-y-5">
        <div className="grid gap-4 sm:grid-cols-3">
          <FormRow
            label="Supplier"
            required
            className="sm:col-span-3"
            error={showErrors && !sellerPartyId ? 'Choose who you are buying from.' : undefined}
          >
            {suppliersQuery.isPending ? (
              <div className="flex h-10 items-center gap-2 text-sm text-muted-ink">
                <InlineLoader />
                Loading suppliers…
              </div>
            ) : suppliersQuery.isError ? (
              <p className="text-sm text-red-600">{errorMessage(suppliersQuery.error)}</p>
            ) : (
              <Select
                value={sellerPartyId}
                onChange={(e) => setSellerPartyId(e.target.value)}
                disabled={(suppliersQuery.data?.length ?? 0) === 0}
              >
                <option value="">
                  {(suppliersQuery.data?.length ?? 0) === 0
                    ? 'No suppliers available yet'
                    : 'Select a supplier…'}
                </option>
                {suppliersQuery.data?.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name} · {s.type}
                    {s.state ? ` · ${s.state}` : ''}
                  </option>
                ))}
              </Select>
            )}
          </FormRow>

          <FormRow label="Deal date" required>
            <Input type="date" value={dealDate} onChange={(e) => setDealDate(e.target.value)} />
          </FormRow>

          <FormRow label="Expected delivery">
            <Input
              type="date"
              value={expectedDelivery}
              onChange={(e) => setExpectedDelivery(e.target.value)}
              min={dealDate}
            />
          </FormRow>
        </div>

        <FormRow label="Notes">
          <Textarea
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            placeholder="Anything the supplier should know…"
          />
        </FormRow>

        <div>
          <div className="mb-2 flex items-center justify-between">
            <Label required>Lines</Label>
            <Button
              size="sm"
              variant="outline"
              onClick={() => setLines((prev) => [...prev, blankLine()])}
            >
              <Plus className="h-3.5 w-3.5" />
              Add line
            </Button>
          </div>

          <div className="overflow-hidden rounded-lg border border-lilac-200">
            <Table>
              <THead>
                <tr>
                  <TH>Description</TH>
                  <TH className="w-28">HSN</TH>
                  <TH className="w-24 text-right">Qty</TH>
                  <TH className="w-32 text-right">Unit price</TH>
                  <TH className="w-24 text-right">Tax %</TH>
                  <TH className="w-32 text-right">Amount</TH>
                  <TH className="w-12" />
                </tr>
              </THead>
              <tbody>
                {lines.map((line) => {
                  const base = (Number(line.quantity) || 0) * (Number(line.unitPrice) || 0)
                  const amount = base * (1 + (Number(line.taxRate) || 0) / 100)
                  return (
                    <TR key={line.key}>
                      <TD>
                        <Input
                          value={line.description}
                          onChange={(e) => updateLine(line.key, { description: e.target.value })}
                          placeholder="Teak dining table, 6 seater"
                        />
                      </TD>
                      <TD>
                        <Input
                          value={line.hsnCode}
                          onChange={(e) => updateLine(line.key, { hsnCode: e.target.value })}
                          placeholder="9403"
                        />
                      </TD>
                      <TD>
                        <Input
                          type="number"
                          min="0"
                          step="any"
                          className="text-right"
                          value={line.quantity}
                          onChange={(e) => updateLine(line.key, { quantity: e.target.value })}
                        />
                      </TD>
                      <TD>
                        <Input
                          type="number"
                          min="0"
                          step="any"
                          className="text-right"
                          value={line.unitPrice}
                          onChange={(e) => updateLine(line.key, { unitPrice: e.target.value })}
                          placeholder="0.00"
                        />
                      </TD>
                      <TD>
                        <Input
                          type="number"
                          min="0"
                          max="100"
                          step="any"
                          className="text-right"
                          value={line.taxRate}
                          onChange={(e) => updateLine(line.key, { taxRate: e.target.value })}
                        />
                      </TD>
                      <TD className="text-right">
                        <span className="tabular">{formatMoney(amount)}</span>
                      </TD>
                      <TD>
                        <Button
                          size="icon"
                          variant="ghost"
                          aria-label="Remove line"
                          className="text-red-600 hover:bg-red-50"
                          disabled={lines.length === 1}
                          onClick={() =>
                            setLines((prev) => prev.filter((l) => l.key !== line.key))
                          }
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </TD>
                    </TR>
                  )
                })}
              </tbody>
            </Table>
          </div>

          {showErrors && validLines.length === 0 && (
            <p className="mt-1 text-xs text-red-600">
              Add at least one line with a description, a quantity and a price.
            </p>
          )}
        </div>

        <div className="flex justify-end">
          <dl className="w-full max-w-xs space-y-1.5 text-sm sm:w-72">
            <div className="flex items-center justify-between text-muted-ink">
              <dt>Untaxed</dt>
              <dd className="tabular text-plum-800">{formatMoney(totals.untaxed)}</dd>
            </div>
            <div className="flex items-center justify-between text-muted-ink">
              <dt>Tax</dt>
              <dd className="tabular text-plum-800">{formatMoney(totals.tax)}</dd>
            </div>
            <div className="flex items-center justify-between border-t border-lilac-200 pt-2 text-base font-semibold text-plum-800">
              <dt>Total</dt>
              <dd className="tabular">{formatMoney(totals.total)}</dd>
            </div>
          </dl>
        </div>

        <p className="text-xs text-muted-ink">
          The CGST/SGST versus IGST split is decided by the server from your state and the
          supplier&rsquo;s.
        </p>
      </div>
    </Modal>
  )
}

/* ------------------------------------------------------------------ */
/* Reject                                                              */
/* ------------------------------------------------------------------ */

function RejectModal({
  deal,
  submitting,
  onClose,
  onConfirm,
}: {
  deal: Deal
  submitting: boolean
  onClose: () => void
  onConfirm: (reason?: string) => void
}) {
  const [reason, setReason] = useState('')

  return (
    <Modal
      open
      onClose={onClose}
      size="sm"
      title={`Reject ${deal.dealNo}?`}
      description="The counterparty will see this deal as rejected. Nothing is posted to either ledger."
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            Keep it
          </Button>
          <Button
            variant="danger"
            loading={submitting}
            onClick={() => onConfirm(reason.trim() || undefined)}
          >
            Reject deal
          </Button>
        </>
      }
    >
      <FormRow label="Reason (optional)">
        <Textarea
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          autoFocus
          placeholder="Price is above our budget for this quarter."
        />
      </FormRow>
    </Modal>
  )
}

/* ------------------------------------------------------------------ */
/* Settlement                                                          */
/* ------------------------------------------------------------------ */

function SettleModal({
  deal,
  submitting,
  onClose,
  onConfirm,
}: {
  deal: Deal
  submitting: boolean
  onClose: () => void
  onConfirm: (body: SettlementRequest) => void
}) {
  const remaining = outstandingOf(deal)
  const [method, setMethod] = useState<PaymentMethod>('BANK')
  const [settlementDate, setSettlementDate] = useState(today())
  const [amount, setAmount] = useState(remaining)
  const [reference, setReference] = useState('')

  const numericAmount = Number(amount)
  const remainingNumber = Number(remaining)
  const amountError =
    !amount || Number.isNaN(numericAmount) || numericAmount <= 0
      ? 'Enter an amount greater than zero.'
      : numericAmount > remainingNumber + 0.001
        ? `That is more than the ${formatMoney(remaining)} outstanding.`
        : undefined

  return (
    <Modal
      open
      onClose={onClose}
      size="sm"
      title={`Record payment · ${deal.dealNo}`}
      description={`${formatMoney(remaining)} outstanding with ${
        deal.iAmSeller ? deal.buyerName : deal.sellerName
      }.`}
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button
            loading={submitting}
            disabled={Boolean(amountError)}
            onClick={() =>
              onConfirm({
                method,
                settlementDate,
                amount: String(numericAmount),
                reference: reference.trim() || undefined,
              })
            }
          >
            Record payment
          </Button>
        </>
      }
    >
      <div className="grid gap-4 sm:grid-cols-2">
        <FormRow label="Method" required>
          <Select value={method} onChange={(e) => setMethod(e.target.value as PaymentMethod)}>
            <option value="BANK">Bank</option>
            <option value="CASH">Cash</option>
          </Select>
        </FormRow>

        <FormRow label="Date" required>
          <Input
            type="date"
            value={settlementDate}
            onChange={(e) => setSettlementDate(e.target.value)}
          />
        </FormRow>

        <FormRow label="Amount" required error={amountError}>
          <Input
            type="number"
            min="0"
            step="0.01"
            className="text-right"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
          />
        </FormRow>

        <FormRow label="Reference">
          <Input
            value={reference}
            onChange={(e) => setReference(e.target.value)}
            placeholder="UTR / cheque no"
          />
        </FormRow>
      </div>
    </Modal>
  )
}
