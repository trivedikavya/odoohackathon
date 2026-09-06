import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Package, Plus, Send, Trash2 } from 'lucide-react'
import { errorMessage } from '@/api/client'
import { portalApi } from '@/api/endpoints'
import type { Product } from '@/api/types'
import { PageHeader } from '@/components/layout/AppLayout'
import { Badge, StatusBadge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { FormRow, Input, Select, Textarea } from '@/components/ui/Field'
import { Modal } from '@/components/ui/Modal'
import { EmptyState, ErrorState, InlineLoader, PageLoader } from '@/components/ui/PageLoader'
import { EmptyRow, Table, TD, TH, THead, TR } from '@/components/ui/Table'
import { useToast } from '@/components/ui/Toast'
import { formatDate, formatMoney, formatNumber, titleCase, today } from '@/lib/utils'

interface DraftLine {
  key: string
  productId: string
  quantity: string
}

const emptyLine = (): DraftLine => ({ key: crypto.randomUUID(), productId: '', quantity: '1' })

/** A combo reports the bundles its scarcest component can build, so this covers both cases. */
function availableQuantity(product: Product): number {
  return Number(product.quantityOnHand ?? '0')
}

function outOfStock(product: Product): boolean {
  // Services carry no stock by design and are always orderable.
  if (!product.tracksStock && product.type === 'SERVICE') return false
  return availableQuantity(product) <= 0
}

function optionLabel(product: Product): string {
  const price = formatMoney(product.salesPrice)
  if (product.type === 'SERVICE') return `${product.name} — ${price} · service`
  const stock = `${formatNumber(product.quantityOnHand ?? '0', 0)} in stock`
  return outOfStock(product)
    ? `${product.name} — ${price} · out of stock`
    : `${product.name} — ${price} · ${stock}`
}

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

  const catalogue = useQuery({
    queryKey: ['portal-supplier-catalogue', sellerId],
    queryFn: () => portalApi.supplierCatalogue(Number(sellerId)),
    enabled: open && Boolean(sellerId),
  })

  const catalogueById = useMemo(() => {
    const map = new Map<string, Product>()
    for (const p of catalogue.data ?? []) map.set(String(p.id), p)
    return map
  }, [catalogue.data])

  const totals = useMemo(() => {
    let untaxed = 0
    let tax = 0
    for (const l of lines) {
      const product = catalogueById.get(l.productId)
      if (!product) continue
      const base = (Number(l.quantity) || 0) * (Number(product.salesPrice) || 0)
      untaxed += base
      tax += (base * (Number(product.taxRate) || 0)) / 100
    }
    return { untaxed, tax, total: untaxed + tax }
  }, [lines, catalogueById])

  const reset = () => {
    setSellerId('')
    setDealDate(today())
    setNotes('')
    setLines([emptyLine()])
    setFormError(null)
  }

  const validLines = lines.filter((l) => l.productId && Number(l.quantity) > 0)

  const create = useMutation({
    mutationFn: () =>
      portalApi.createOrder({
        sellerPartyId: Number(sellerId),
        dealDate,
        notes: notes.trim() || undefined,
        // Price and tax are deliberately omitted — the seller's catalogue is authoritative.
        lines: validLines.map((l) => ({ productId: Number(l.productId), quantity: l.quantity })),
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
    if (validLines.length === 0) {
      setFormError('Pick at least one item and give it a quantity')
      return
    }
    create.mutate()
  }

  const updateLine = (key: string, patch: Partial<DraftLine>) =>
    setLines((cur) => cur.map((l) => (l.key === key ? { ...l, ...patch } : l)))

  const rows = orders.data?.content ?? []
  const items = catalogue.data ?? []

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
        description="Pick items from the supplier's catalogue. They confirm the price before anything is owed."
        footer={
          <>
            <Button variant="outline" type="button" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button
              type="submit"
              form="portal-rfq"
              loading={create.isPending}
              disabled={items.length === 0}
            >
              Create request
            </Button>
          </>
        }
      >
        <form id="portal-rfq" onSubmit={submit} className="space-y-5">
          <div className="grid gap-4 sm:grid-cols-2">
            <FormRow label="Buy from" required>
              <Select
                value={sellerId}
                onChange={(e) => {
                  setSellerId(e.target.value)
                  // The old picks belong to the old catalogue, so start the lines again.
                  setLines([emptyLine()])
                }}
                required
              >
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

          {!sellerId ? (
            <div className="rounded-lg border border-dashed border-lilac-300 bg-lilac-50/50 px-4 py-8 text-center text-sm text-muted-ink">
              Choose a supplier to see what they sell.
            </div>
          ) : catalogue.isLoading ? (
            <div className="flex justify-center py-8">
              <InlineLoader />
            </div>
          ) : catalogue.isError ? (
            <ErrorState
              message={errorMessage(catalogue.error)}
              action={
                <Button variant="outline" size="sm" onClick={() => void catalogue.refetch()}>
                  Retry
                </Button>
              }
            />
          ) : items.length === 0 ? (
            <EmptyState
              icon={<Package className="h-6 w-6" />}
              title="This supplier has published nothing yet"
              description="There is no catalogue to order from. Pick a different supplier, or ask them to add products."
            />
          ) : (
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
                {lines.map((line) => {
                  const product = catalogueById.get(line.productId)
                  return (
                    <div
                      key={line.key}
                      className="rounded-lg border border-lilac-200 bg-lilac-50/40 p-2.5"
                    >
                      <div className="grid grid-cols-12 items-end gap-2">
                        <div className="col-span-12 sm:col-span-5">
                          <label className="mb-1 block text-[10px] text-muted-ink uppercase">
                            Item
                          </label>
                          <Select
                            value={line.productId}
                            onChange={(e) => updateLine(line.key, { productId: e.target.value })}
                          >
                            <option value="">Choose an item…</option>
                            {items.map((p) => (
                              <option key={p.id} value={p.id} disabled={outOfStock(p)}>
                                {optionLabel(p)}
                              </option>
                            ))}
                          </Select>
                        </div>
                        <div className="col-span-4 sm:col-span-2">
                          <label className="mb-1 block text-[10px] text-muted-ink uppercase">
                            Qty
                          </label>
                          <Input
                            type="number"
                            min="0.001"
                            step="0.001"
                            value={line.quantity}
                            onChange={(e) => updateLine(line.key, { quantity: e.target.value })}
                          />
                        </div>
                        <div className="col-span-4 sm:col-span-2">
                          <label className="mb-1 block text-[10px] text-muted-ink uppercase">
                            Price
                          </label>
                          <Input
                            readOnly
                            className="tabular bg-lilac-50 text-muted-ink"
                            value={product ? formatMoney(product.salesPrice) : '—'}
                          />
                        </div>
                        <div className="col-span-3 sm:col-span-2">
                          <label className="mb-1 block text-[10px] text-muted-ink uppercase">
                            Tax %
                          </label>
                          <Input
                            readOnly
                            className="tabular bg-lilac-50 text-muted-ink"
                            value={product ? formatNumber(product.taxRate, 2) : '—'}
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

                      {product?.type === 'COMBO' && (
                        <p className="mt-2 text-xs text-muted-ink">
                          <Badge tone="brand">Combo</Badge>{' '}
                          {product.components.length === 0
                            ? 'This bundle lists no components yet.'
                            : `Each bundle contains ${product.components
                                .map((c) => `${formatNumber(c.quantity, 0)} × ${c.componentName}`)
                                .join(', ')}.`}
                        </p>
                      )}
                    </div>
                  )
                })}
              </div>
            </div>
          )}

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
