import { useEffect, useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Plus, Trash2 } from 'lucide-react'
import { contactsApi, productsApi } from '@/api/endpoints'
import type { OrderRequest, OrderResponse } from '@/api/types'
import { Button } from '@/components/ui/Button'
import { FormRow, Input, Select, Textarea } from '@/components/ui/Field'
import { Modal } from '@/components/ui/Modal'
import { formatMoney, today } from '@/lib/utils'
import type { WorkflowConfig } from './workflowConfig'

interface DraftLine {
  key: string
  productId: string
  quantity: string
  unitPrice: string
  taxRate: string
}

const emptyLine = (): DraftLine => ({
  key: crypto.randomUUID(),
  productId: '',
  quantity: '1',
  unitPrice: '',
  taxRate: '0',
})

export function OrderFormModal({
  config,
  open,
  order,
  onClose,
  onSubmit,
  submitting,
}: {
  config: WorkflowConfig
  open: boolean
  order?: OrderResponse | null
  onClose: () => void
  onSubmit: (body: OrderRequest) => void
  submitting: boolean
}) {
  const [contactId, setContactId] = useState('')
  const [orderDate, setOrderDate] = useState(today())
  const [notes, setNotes] = useState('')
  const [lines, setLines] = useState<DraftLine[]>([emptyLine()])
  const [error, setError] = useState<string | null>(null)

  const { data: contacts = [] } = useQuery({
    queryKey: ['contact-options'],
    queryFn: contactsApi.options,
    enabled: open,
  })
  const { data: products = [] } = useQuery({
    queryKey: ['product-options'],
    queryFn: productsApi.options,
    enabled: open,
  })

  // Only contacts that can act in this role may be selected.
  const eligibleContacts = useMemo(
    () => contacts.filter((c) => c.type === config.contactFilter || c.type === 'BOTH'),
    [contacts, config.contactFilter],
  )

  useEffect(() => {
    if (!open) return
    setError(null)
    if (order) {
      setContactId(String(order.contactId))
      setOrderDate(order.orderDate)
      setNotes(order.notes ?? '')
      setLines(
        order.lines.map((l) => ({
          key: crypto.randomUUID(),
          productId: String(l.productId),
          quantity: l.quantity,
          unitPrice: l.unitPrice,
          taxRate: l.taxRate,
        })),
      )
    } else {
      setContactId('')
      setOrderDate(today())
      setNotes('')
      setLines([emptyLine()])
    }
  }, [open, order])

  const updateLine = (key: string, patch: Partial<DraftLine>) => {
    setLines((current) => current.map((l) => (l.key === key ? { ...l, ...patch } : l)))
  }

  // Picking a product pre-fills price and tax rate from the product master.
  const onProductChange = (key: string, productId: string) => {
    const product = products.find((p) => String(p.id) === productId)
    updateLine(key, {
      productId,
      unitPrice: product ? product[config.priceField] : '',
      taxRate: product ? product.taxRate : '0',
    })
  }

  const lineTotal = (l: DraftLine) => {
    const qty = Number(l.quantity) || 0
    const price = Number(l.unitPrice) || 0
    const rate = Number(l.taxRate) || 0
    const untaxed = qty * price
    return untaxed + (untaxed * rate) / 100
  }

  // Mirrors the server-side computation so the user sees the total before saving.
  // The server recomputes it and is the source of truth.
  const totals = useMemo(() => {
    let untaxed = 0
    let tax = 0
    for (const l of lines) {
      const qty = Number(l.quantity) || 0
      const price = Number(l.unitPrice) || 0
      const rate = Number(l.taxRate) || 0
      const base = qty * price
      untaxed += base
      tax += (base * rate) / 100
    }
    return { untaxed, tax, total: untaxed + tax }
  }, [lines])

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)

    if (!contactId) {
      setError(`Select a ${config.partyLabel.toLowerCase()}`)
      return
    }
    const valid = lines.filter((l) => l.productId && Number(l.quantity) > 0)
    if (valid.length === 0) {
      setError('Add at least one line with a product and quantity')
      return
    }

    onSubmit({
      contactId: Number(contactId),
      orderDate,
      notes: notes.trim() || undefined,
      lines: valid.map((l) => ({
        productId: Number(l.productId),
        quantity: l.quantity,
        unitPrice: l.unitPrice || '0',
        taxRate: l.taxRate || '0',
      })),
    })
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      size="lg"
      title={order ? `Edit ${order.orderNo}` : `New ${config.orderLabel}`}
      description={`Totals are recalculated on the server from quantity × unit price.`}
      footer={
        <>
          <Button variant="outline" onClick={onClose} type="button">
            Cancel
          </Button>
          <Button type="submit" form="order-form" loading={submitting}>
            {order ? 'Save changes' : `Create ${config.orderLabel}`}
          </Button>
        </>
      }
    >
      <form id="order-form" onSubmit={handleSubmit} className="space-y-5">
        <div className="grid gap-4 sm:grid-cols-2">
          <FormRow label={config.partyLabel} required>
            <Select value={contactId} onChange={(e) => setContactId(e.target.value)} required>
              <option value="">Select a {config.partyLabel.toLowerCase()}…</option>
              {eligibleContacts.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </Select>
          </FormRow>

          <FormRow label="Order date" required>
            <Input
              type="date"
              value={orderDate}
              onChange={(e) => setOrderDate(e.target.value)}
              required
            />
          </FormRow>
        </div>

        <div>
          <div className="mb-2 flex items-center justify-between">
            <p className="text-xs font-medium tracking-wide text-muted-ink uppercase">Lines</p>
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => setLines((c) => [...c, emptyLine()])}
            >
              <Plus className="h-3.5 w-3.5" />
              Add line
            </Button>
          </div>

          <div className="space-y-2">
            {lines.map((line) => (
              <div
                key={line.key}
                className="grid grid-cols-12 items-end gap-2 rounded-lg border border-lilac-200 bg-lilac-50/40 p-2.5"
              >
                <div className="col-span-12 sm:col-span-4">
                  <label className="mb-1 block text-[10px] text-muted-ink uppercase">Product</label>
                  <Select
                    value={line.productId}
                    onChange={(e) => onProductChange(line.key, e.target.value)}
                  >
                    <option value="">Select…</option>
                    {products.map((p) => (
                      <option key={p.id} value={p.id}>
                        {p.name}
                      </option>
                    ))}
                  </Select>
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

                <div className="col-span-4 sm:col-span-1">
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

                <div className="col-span-10 sm:col-span-2">
                  <label className="mb-1 block text-[10px] text-muted-ink uppercase">Total</label>
                  <p className="tabular px-1 py-2 text-sm font-medium text-plum-800">
                    {formatMoney(lineTotal(line))}
                  </p>
                </div>

                <div className="col-span-2 sm:col-span-1 flex justify-end">
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    aria-label="Remove line"
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

        <div className="rounded-lg border border-lilac-200 bg-lilac-50 p-3">
          <div className="space-y-1 text-sm">
            <div className="flex justify-between text-muted-ink">
              <span>Untaxed</span>
              <span className="tabular">{formatMoney(totals.untaxed)}</span>
            </div>
            <div className="flex justify-between text-muted-ink">
              <span>Tax</span>
              <span className="tabular">{formatMoney(totals.tax)}</span>
            </div>
            <div className="flex justify-between border-t border-lilac-200 pt-1 font-semibold text-plum-800">
              <span>Total</span>
              <span className="tabular">{formatMoney(totals.total)}</span>
            </div>
          </div>
        </div>

        {error && (
          <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
            {error}
          </div>
        )}
      </form>
    </Modal>
  )
}
