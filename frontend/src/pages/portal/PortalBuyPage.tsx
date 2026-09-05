import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Send, Trash2 } from 'lucide-react'
import { errorMessage } from '@/api/client'
import { portalApi } from '@/api/endpoints'
import type { DealLineRequest } from '@/api/types'
import { PageHeader } from '@/components/layout/AppLayout'
import { Badge, StatusBadge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { FormRow, Input, Select, Textarea } from '@/components/ui/Field'
import { Modal } from '@/components/ui/Modal'
import { ErrorState, PageLoader } from '@/components/ui/PageLoader'
import { EmptyRow, Table, TD, TH, THead, TR } from '@/components/ui/Table'
import { useToast } from '@/components/ui/Toast'
import { formatDate, formatMoney, titleCase, today } from '@/lib/utils'

interface DraftLine extends DealLineRequest {
  key: string
}

const emptyLine = (): DraftLine => ({
  key: crypto.randomUUID(),
  description: '',
  quantity: '1',
  unitPrice: '',
  taxRate: '18',
})

/**
 * Customers buy through the portal namespace rather than the back office,
 * because the server bars a USER account from every non-portal path.
 */
export function PortalBuyPage() {
  const qc = useQueryClient()
  const toast = useToast()

  const [open, setOpen] = useState(false)
  const [sellerId, setSellerId] = useState('')
  const [dealDate, setDealDate] = useState(today())
  const [notes, setNotes] = useState('')
  const [lines, setLines] = useState<DraftLine[]>([emptyLine()])
  const [formError, setFormError] = useState<string | null>(null)

  const suppliers = useQuery({ queryKey: ['portal-suppliers'], queryFn: portalApi.suppliers })
  const orders = useQuery({
    queryKey: ['portal-orders'],
    queryFn: () => portalApi.myOrders({ size: 50 }),
  })

  const totals = useMemo(() => {
    let untaxed = 0
    let tax = 0
    for (const l of lines) {
      const base = (Number(l.quantity) || 0) * (Number(l.unitPrice) || 0)
      untaxed += base
      tax += (base * (Number(l.taxRate) || 0)) / 100
    }
    return { untaxed, tax, total: untaxed + tax }
  }, [lines])

  const reset = () => {
    setSellerId('')
    setDealDate(today())
    setNotes('')
    setLines([emptyLine()])
    setFormError(null)
  }

  const create = useMutation({
    mutationFn: () =>
      portalApi.createOrder({
        sellerPartyId: Number(sellerId),
        dealDate,
        notes: notes.trim() || undefined,
        lines: lines
          .filter((l) => l.description.trim() && Number(l.quantity) > 0)
          .map(({ description, quantity, unitPrice, taxRate }) => ({
            description,
            quantity,
            unitPrice: unitPrice || '0',
            taxRate: taxRate || '0',
          })),
      }),
    onSuccess: (deal) => {
      qc.invalidateQueries({ queryKey: ['portal-orders'] })
      setOpen(false)
      reset()
      toast.success(`Request ${deal.dealNo} created — send it when you are ready`)
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  const send = useMutation({
    mutationFn: (id: number) => portalApi.sendOrder(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['portal-orders'] })
      toast.success('Request sent to the supplier')
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  const cancel = useMutation({
    mutationFn: (id: number) => portalApi.cancelOrder(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['portal-orders'] })
      toast.success('Request cancelled')
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    setFormError(null)
    if (!sellerId) {
      setFormError('Choose who you want to buy from')
      return
    }
    if (!lines.some((l) => l.description.trim() && Number(l.quantity) > 0)) {
      setFormError('Add at least one item with a description and quantity')
      return
    }
    create.mutate()
  }

  const updateLine = (key: string, patch: Partial<DraftLine>) =>
    setLines((cur) => cur.map((l) => (l.key === key ? { ...l, ...patch } : l)))

  const rows = orders.data?.content ?? []

  return (
    <>
      <PageHeader
        title="Buy"
        description="Send a request to any seller or vendor. They confirm the price, deliver, then invoice you."
        action={
          <Button onClick={() => { reset(); setOpen(true) }}>
            <Plus className="h-4 w-4" />
            New request
          </Button>
        }
      />

      <Card>
        {orders.isLoading ? (
          <PageLoader />
        ) : orders.isError ? (
          <ErrorState message={errorMessage(orders.error)} />
        ) : (
          <Table>
            <THead>
              <tr>
                <TH>Request</TH>
                <TH>Supplier</TH>
                <TH>Date</TH>
                <TH>Status</TH>
                <TH className="text-right">Total</TH>
                <TH className="text-right">Actions</TH>
              </tr>
            </THead>
            <tbody>
              {rows.length === 0 ? (
                <EmptyRow colSpan={6}>
                  Nothing yet. Use &ldquo;New request&rdquo; to ask a supplier for a quote.
                </EmptyRow>
              ) : (
                rows.map((deal) => (
                  <TR key={deal.id}>
                    <TD className="font-medium">{deal.dealNo}</TD>
                    <TD>{deal.sellerName}</TD>
                    <TD>{formatDate(deal.dealDate)}</TD>
                    <TD>
                      <StatusBadge status={deal.status} />
                    </TD>
                    <TD className="tabular text-right">{formatMoney(deal.totalAmount)}</TD>
                    <TD className="text-right">
                      <div className="flex justify-end gap-2">
                        {deal.status === 'RFQ_DRAFT' && (
                          <Button
                            size="sm"
                            onClick={() => send.mutate(deal.id)}
                            loading={send.isPending}
                          >
                            <Send className="h-3.5 w-3.5" />
                            Send
                          </Button>
                        )}
                        {['RFQ_DRAFT', 'RFQ_SENT', 'ACCEPTED', 'DELIVERED'].includes(
                          deal.status,
                        ) && (
                          <Button
                            size="sm"
                            variant="ghost"
                            onClick={() => cancel.mutate(deal.id)}
                          >
                            Cancel
                          </Button>
                        )}
                      </div>
                    </TD>
                  </TR>
                ))
              )}
            </tbody>
          </Table>
        )}
      </Card>

      <Modal
        open={open}
        onClose={() => setOpen(false)}
        size="lg"
        title="New purchase request"
        description="Prices you enter are a proposal — the supplier confirms or declines them."
        footer={
          <>
            <Button variant="outline" type="button" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" form="portal-rfq" loading={create.isPending}>
              Create request
            </Button>
          </>
        }
      >
        <form id="portal-rfq" onSubmit={submit} className="space-y-5">
          <div className="grid gap-4 sm:grid-cols-2">
            <FormRow label="Buy from" required>
              <Select value={sellerId} onChange={(e) => setSellerId(e.target.value)} required>
                <option value="">Choose a supplier…</option>
                {(suppliers.data ?? []).map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name} — {titleCase(s.type)}
                    {s.state ? ` (${s.state})` : ''}
                  </option>
                ))}
              </Select>
            </FormRow>
            <FormRow label="Date" required>
              <Input
                type="date"
                value={dealDate}
                onChange={(e) => setDealDate(e.target.value)}
                required
              />
            </FormRow>
          </div>

          <div>
            <div className="mb-2 flex items-center justify-between">
              <p className="text-xs font-medium tracking-wide text-muted-ink uppercase">Items</p>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => setLines((c) => [...c, emptyLine()])}
              >
                <Plus className="h-3.5 w-3.5" />
                Add item
              </Button>
            </div>

            <div className="space-y-2">
              {lines.map((line) => (
                <div
                  key={line.key}
                  className="grid grid-cols-12 items-end gap-2 rounded-lg border border-lilac-200 bg-lilac-50/40 p-2.5"
                >
                  <div className="col-span-12 sm:col-span-5">
                    <label className="mb-1 block text-[10px] text-muted-ink uppercase">Item</label>
                    <Input
                      value={line.description}
                      placeholder="What do you need?"
                      onChange={(e) => updateLine(line.key, { description: e.target.value })}
                    />
                  </div>
                  <div className="col-span-4 sm:col-span-2">
                    <label className="mb-1 block text-[10px] text-muted-ink uppercase">Qty</label>
                    <Input
                      type="number"
                      min="0.001"
                      step="0.001"
                      value={line.quantity}
                      onChange={(e) => updateLine(line.key, { quantity: e.target.value })}
                    />
                  </div>
                  <div className="col-span-4 sm:col-span-2">
                    <label className="mb-1 block text-[10px] text-muted-ink uppercase">Price</label>
                    <Input
                      type="number"
                      min="0"
                      step="0.01"
                      value={line.unitPrice}
                      onChange={(e) => updateLine(line.key, { unitPrice: e.target.value })}
                    />
                  </div>
                  <div className="col-span-3 sm:col-span-2">
                    <label className="mb-1 block text-[10px] text-muted-ink uppercase">Tax %</label>
                    <Input
                      type="number"
                      min="0"
                      max="100"
                      step="0.01"
                      value={line.taxRate}
                      onChange={(e) => updateLine(line.key, { taxRate: e.target.value })}
                    />
                  </div>
                  <div className="col-span-1 flex justify-end">
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      aria-label="Remove item"
                      disabled={lines.length === 1}
                      onClick={() => setLines((c) => c.filter((l) => l.key !== line.key))}
                    >
                      <Trash2 className="h-4 w-4 text-muted-ink" />
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <FormRow label="Notes">
            <Textarea value={notes} onChange={(e) => setNotes(e.target.value)} rows={2} />
          </FormRow>

          <div className="rounded-lg border border-lilac-200 bg-lilac-50 p-3 text-sm">
            <div className="flex justify-between text-muted-ink">
              <span>Subtotal</span>
              <span className="tabular">{formatMoney(totals.untaxed)}</span>
            </div>
            <div className="flex justify-between text-muted-ink">
              <span>Tax</span>
              <span className="tabular">{formatMoney(totals.tax)}</span>
            </div>
            <div className="mt-1 flex justify-between border-t border-lilac-200 pt-1 font-semibold text-plum-800">
              <span>Total</span>
              <span className="tabular">{formatMoney(totals.total)}</span>
            </div>
            <p className="mt-2 border-t border-lilac-200 pt-2 text-xs text-muted-ink">
              <Badge tone="neutral">No accounting effect yet</Badge> A request is not a
              transaction. Nothing is recorded until the supplier delivers and invoices you.
            </p>
          </div>

          {formError && (
            <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
              {formError}
            </div>
          )}
        </form>
      </Modal>
    </>
  )
}
