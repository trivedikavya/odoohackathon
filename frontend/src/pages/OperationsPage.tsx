import { useMemo, useState, type ReactNode } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Banknote, Boxes, Plus, Receipt, Store, Trash2 } from 'lucide-react'
import { PageHeader } from '@/components/layout/AppLayout'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { FormRow, Input, Select, Textarea } from '@/components/ui/Field'
import { Badge } from '@/components/ui/Badge'
import { Modal } from '@/components/ui/Modal'
import { EmptyState, ErrorState, InlineLoader } from '@/components/ui/PageLoader'
import { useToast } from '@/components/ui/Toast'
import { analyticApi, masterApi, operationsApi } from '@/api/endpoints'
import { errorMessage } from '@/api/client'
import type { DealLineRequest, PaymentMethod, Product } from '@/api/types'
import { cn, formatMoney, formatNumber, titleCase, today } from '@/lib/utils'

type Dialog = 'capital' | 'openingStock' | 'expense' | 'directTrade' | null

interface TradeLine {
  key: string
  productId: string
  quantity: string
  unitPrice: string
}

const emptyTradeLine = (): TradeLine => ({
  key: crypto.randomUUID(),
  productId: '',
  quantity: '1',
  unitPrice: '',
})

function MethodSelect({
  value,
  onChange,
}: {
  value: PaymentMethod
  onChange: (value: PaymentMethod) => void
}) {
  return (
    <Select value={value} onChange={(e) => onChange(e.target.value as PaymentMethod)}>
      <option value="CASH">Cash</option>
      <option value="BANK">Bank</option>
    </Select>
  )
}

function Note({ children }: { children: ReactNode }) {
  return (
    <p className="rounded-lg border border-lilac-200 bg-lilac-50 px-3 py-2 text-xs text-muted-ink">
      {children}
    </p>
  )
}

function FormError({ message }: { message: string | null }) {
  if (!message) return null
  return (
    <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
      {message}
    </div>
  )
}

function OperationCard({
  icon,
  title,
  description,
  cta,
  onClick,
}: {
  icon: ReactNode
  title: string
  description: string
  cta: string
  onClick: () => void
}) {
  return (
    <Card className="flex flex-col">
      <CardHeader
        title={
          <span className="flex items-center gap-2">
            <span className="text-amethyst-600">{icon}</span>
            {title}
          </span>
        }
      />
      <CardBody className="flex flex-1 flex-col justify-between gap-4">
        <p className="text-sm text-muted-ink">{description}</p>
        <div>
          <Button size="sm" onClick={onClick}>
            {cta}
          </Button>
        </div>
      </CardBody>
    </Card>
  )
}

/**
 * Everything a book needs that is not a trade with a registered counterparty:
 * money the owner puts in, stock the book already had, overheads with no
 * vendor document, and trades with people who are not on the platform.
 */
export function OperationsPage() {
  const toast = useToast()
  const queryClient = useQueryClient()

  const [dialog, setDialog] = useState<Dialog>(null)
  const close = () => setDialog(null)

  // Anything posted here moves the ledger, so the whole cached view is stale after.
  const invalidateAll = () => {
    for (const key of [
      'dashboard',
      'products',
      'stock-ledger',
      'trial-balance',
      'balance-sheet',
      'profit-and-loss',
      'ledger-entries',
      'deals',
    ]) {
      void queryClient.invalidateQueries({ queryKey: [key] })
    }
  }

  /** Capital */
  const [capMethod, setCapMethod] = useState<PaymentMethod>('BANK')
  const [capDate, setCapDate] = useState(today())
  const [capAmount, setCapAmount] = useState('')
  const [capNote, setCapNote] = useState('')
  const [capError, setCapError] = useState<string | null>(null)

  const capital = useMutation({
    mutationFn: () =>
      operationsApi.capital({
        method: capMethod,
        date: capDate,
        amount: capAmount,
        note: capNote.trim() || undefined,
      }),
    onSuccess: (res) => {
      toast.success(`Capital introduced — entry ${res.entryNo}`)
      setCapAmount('')
      setCapNote('')
      setCapError(null)
      close()
      invalidateAll()
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  /** Opening stock */
  const [osProductId, setOsProductId] = useState('')
  const [osDate, setOsDate] = useState(today())
  const [osQuantity, setOsQuantity] = useState('1')
  const [osUnitCost, setOsUnitCost] = useState('')
  const [osError, setOsError] = useState<string | null>(null)

  const openingStock = useMutation({
    mutationFn: () =>
      operationsApi.openingStock({
        productId: Number(osProductId),
        date: osDate,
        quantity: osQuantity,
        unitCost: osUnitCost,
      }),
    onSuccess: (res) => {
      toast.success(`Opening stock recorded — entry ${res.entryNo}`)
      setOsProductId('')
      setOsQuantity('1')
      setOsUnitCost('')
      setOsError(null)
      close()
      invalidateAll()
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  /** Expense */
  const [exAccountId, setExAccountId] = useState('')
  const [exMethod, setExMethod] = useState<PaymentMethod>('BANK')
  const [exDate, setExDate] = useState(today())
  const [exAmount, setExAmount] = useState('')
  const [exDescription, setExDescription] = useState('')
  const [exProjectId, setExProjectId] = useState('')
  const [exError, setExError] = useState<string | null>(null)

  const expense = useMutation({
    mutationFn: () =>
      operationsApi.expense({
        expenseAccountId: Number(exAccountId),
        method: exMethod,
        date: exDate,
        amount: exAmount,
        description: exDescription.trim(),
        analyticAccountId: exProjectId ? Number(exProjectId) : null,
      }),
    onSuccess: (res) => {
      toast.success(`Expense recorded — entry ${res.entryNo}`)
      setExAmount('')
      setExDescription('')
      setExProjectId('')
      setExError(null)
      close()
      invalidateAll()
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  /** Over-the-counter trade */
  const [dtDirection, setDtDirection] = useState<'PURCHASE' | 'SALE'>('PURCHASE')
  const [dtCounterpartyId, setDtCounterpartyId] = useState('')
  const [dtDate, setDtDate] = useState(today())
  const [dtNotes, setDtNotes] = useState('')
  const [dtLines, setDtLines] = useState<TradeLine[]>([emptyTradeLine()])
  const [dtError, setDtError] = useState<string | null>(null)

  const products = useQuery({
    queryKey: ['products', '', false],
    queryFn: () => masterApi.products(),
    enabled: dialog === 'openingStock' || dialog === 'directTrade',
  })
  const accounts = useQuery({
    queryKey: ['accounts', false],
    queryFn: () => masterApi.accounts(),
    enabled: dialog === 'expense',
  })
  const projects = useQuery({
    queryKey: ['analytic-accounts', false],
    queryFn: () => analyticApi.list(),
    enabled: dialog === 'expense',
  })
  const contacts = useQuery({
    queryKey: ['contacts', undefined, false],
    queryFn: () => masterApi.contacts(),
    enabled: dialog === 'directTrade',
  })

  const productList = products.data ?? []
  const stockedProducts = productList.filter((p) => p.tracksStock)
  const expenseAccounts = (accounts.data ?? []).filter((a) => a.type === 'EXPENSE')
  // Only unregistered parties may be traded with directly — a registered one
  // keeps books of their own, so their side has to be agreed, not asserted.
  const offlineContacts = (contacts.data ?? []).filter((c) => !c.keepsBooks)

  // Keyed off products.data rather than the derived array, which is new every render.
  const productById = useMemo(() => {
    const map = new Map<string, Product>()
    for (const p of products.data ?? []) map.set(String(p.id), p)
    return map
  }, [products.data])

  const dtValidLines = dtLines.filter((l) => l.productId && Number(l.quantity) > 0)

  const dtTotal = useMemo(() => {
    let total = 0
    for (const l of dtLines) {
      const product = productById.get(l.productId)
      if (!product) continue
      const fallback = dtDirection === 'SALE' ? product.salesPrice : product.cost
      const price = l.unitPrice !== '' ? Number(l.unitPrice) : Number(fallback)
      total += (Number(l.quantity) || 0) * (Number.isNaN(price) ? 0 : price)
    }
    return total
  }, [dtLines, productById, dtDirection])

  const directTrade = useMutation({
    mutationFn: () => {
      const lines: DealLineRequest[] = dtValidLines.map((l) => ({
        productId: Number(l.productId),
        quantity: l.quantity,
        ...(l.unitPrice !== '' ? { unitPrice: l.unitPrice } : {}),
      }))
      const body = {
        counterpartyPartyId: Number(dtCounterpartyId),
        date: dtDate,
        notes: dtNotes.trim() || undefined,
        lines,
      }
      return dtDirection === 'PURCHASE'
        ? operationsApi.directPurchase(body)
        : operationsApi.directSale(body)
    },
    onSuccess: (deal) => {
      const docNo = deal.myDocuments[0]?.docNo
      toast.success(
        `${dtDirection === 'PURCHASE' ? 'Purchase' : 'Sale'} recorded — entry ${docNo ?? deal.dealNo}`,
      )
      setDtCounterpartyId('')
      setDtNotes('')
      setDtLines([emptyTradeLine()])
      setDtError(null)
      close()
      invalidateAll()
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  const updateTradeLine = (key: string, patch: Partial<TradeLine>) =>
    setDtLines((cur) => cur.map((l) => (l.key === key ? { ...l, ...patch } : l)))

  return (
    <div>
      <PageHeader
        title="Operations"
        description="The entries that are not a trade with a registered counterparty — money the owner puts in, stock the book already had, overheads, and business done over the counter."
      />

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <OperationCard
          icon={<Banknote className="h-4 w-4" />}
          title="Owner capital"
          description="Money the owner puts into the business. Without this a book starts with no money at all, so nothing can be bought and no bill can be paid."
          cta="Introduce capital"
          onClick={() => setDialog('capital')}
        />
        <OperationCard
          icon={<Boxes className="h-4 w-4" />}
          title="Opening stock"
          description="Goods the business already held on the day the books opened. Booked as Dr Inventory / Cr Owner's Capital — the stock exists, and the owner is who it came from."
          cta="Record opening stock"
          onClick={() => setDialog('openingStock')}
        />
        <OperationCard
          icon={<Receipt className="h-4 w-4" />}
          title="Operating expense"
          description="Overheads like rent, salary or electricity that have no vendor document behind them. Paid straight out of cash or bank and expensed on the spot."
          cta="Record expense"
          onClick={() => setDialog('expense')}
        />
        <OperationCard
          icon={<Store className="h-4 w-4" />}
          title="Over-the-counter trade"
          description="A purchase or sale settled immediately with someone who is not registered here. Registered parties must go through the request and accept flow instead, so both ledgers agree."
          cta="Record a trade"
          onClick={() => setDialog('directTrade')}
        />
      </div>

      {/* Owner capital */}
      <Modal
        open={dialog === 'capital'}
        onClose={close}
        title="Introduce owner capital"
        description="Dr Cash or Bank, Cr Owner's Capital."
        footer={
          <>
            <Button variant="outline" onClick={close}>
              Cancel
            </Button>
            <Button
              loading={capital.isPending}
              onClick={() => {
                if (!(Number(capAmount) > 0)) {
                  setCapError('Enter an amount greater than zero')
                  return
                }
                setCapError(null)
                capital.mutate()
              }}
            >
              Post entry
            </Button>
          </>
        }
      >
        <div className="space-y-4">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <FormRow label="Received into" required>
              <MethodSelect value={capMethod} onChange={setCapMethod} />
            </FormRow>
            <FormRow label="Date" required>
              <Input type="date" value={capDate} onChange={(e) => setCapDate(e.target.value)} />
            </FormRow>
            <FormRow label="Amount" required className="sm:col-span-2">
              <Input
                type="number"
                min="0.01"
                step="0.01"
                value={capAmount}
                onChange={(e) => setCapAmount(e.target.value)}
              />
            </FormRow>
            <FormRow label="Note" className="sm:col-span-2">
              <Textarea rows={2} value={capNote} onChange={(e) => setCapNote(e.target.value)} />
            </FormRow>
          </div>
          <Note>
            A book with no capital has no money at all — every purchase and every payment would
            fail. This is usually the first entry a new book ever gets.
          </Note>
          <FormError message={capError} />
        </div>
      </Modal>

      {/* Opening stock */}
      <Modal
        open={dialog === 'openingStock'}
        onClose={close}
        title="Record opening stock"
        description="Goods already on the shelf when the books opened."
        footer={
          <>
            <Button variant="outline" onClick={close}>
              Cancel
            </Button>
            <Button
              loading={openingStock.isPending}
              disabled={stockedProducts.length === 0}
              onClick={() => {
                if (!osProductId) {
                  setOsError('Choose which product you are counting in')
                  return
                }
                if (!(Number(osQuantity) > 0)) {
                  setOsError('Enter a quantity greater than zero')
                  return
                }
                if (!(Number(osUnitCost) > 0)) {
                  setOsError('Enter a unit cost greater than zero')
                  return
                }
                setOsError(null)
                openingStock.mutate()
              }}
            >
              Post entry
            </Button>
          </>
        }
      >
        {products.isLoading ? (
          <div className="flex justify-center py-8">
            <InlineLoader />
          </div>
        ) : products.isError ? (
          <ErrorState
            message={errorMessage(products.error)}
            action={
              <Button variant="outline" size="sm" onClick={() => void products.refetch()}>
                Retry
              </Button>
            }
          />
        ) : stockedProducts.length === 0 ? (
          <EmptyState
            title="Nothing to stock"
            description="Only goods that track stock can have an opening balance. Services and combos never hold stock of their own."
          />
        ) : (
          <div className="space-y-4">
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <FormRow label="Product" required className="sm:col-span-2">
                <Select value={osProductId} onChange={(e) => setOsProductId(e.target.value)}>
                  <option value="">Choose a product…</option>
                  {stockedProducts.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name} — {formatNumber(p.quantityOnHand ?? '0', 2)} on hand
                    </option>
                  ))}
                </Select>
              </FormRow>
              <FormRow label="Date" required>
                <Input type="date" value={osDate} onChange={(e) => setOsDate(e.target.value)} />
              </FormRow>
              <FormRow label="Quantity" required>
                <Input
                  type="number"
                  min="0.001"
                  step="0.001"
                  value={osQuantity}
                  onChange={(e) => setOsQuantity(e.target.value)}
                />
              </FormRow>
              <FormRow label="Unit cost" required>
                <Input
                  type="number"
                  min="0.01"
                  step="0.01"
                  value={osUnitCost}
                  onChange={(e) => setOsUnitCost(e.target.value)}
                />
              </FormRow>
              <div className="flex items-end">
                <p className="text-sm text-muted-ink">
                  Value:{' '}
                  <span className="tabular font-medium text-plum-800">
                    {formatMoney((Number(osQuantity) || 0) * (Number(osUnitCost) || 0))}
                  </span>
                </p>
              </div>
            </div>
            <Note>
              This books <span className="font-medium text-plum-800">Dr Inventory</span> /{' '}
              <span className="font-medium text-plum-800">Cr Owner&rsquo;s Capital</span>: the stock
              is an asset the business holds, and the owner is who it came from. It is not an
              expense, and it folds into the weighted average cost of the product.
            </Note>
            <FormError message={osError} />
          </div>
        )}
      </Modal>

      {/* Operating expense */}
      <Modal
        open={dialog === 'expense'}
        onClose={close}
        title="Record an operating expense"
        description="Overheads paid straight out of cash or bank."
        footer={
          <>
            <Button variant="outline" onClick={close}>
              Cancel
            </Button>
            <Button
              loading={expense.isPending}
              disabled={expenseAccounts.length === 0}
              onClick={() => {
                if (!exAccountId) {
                  setExError('Choose which expense account this belongs to')
                  return
                }
                if (!(Number(exAmount) > 0)) {
                  setExError('Enter an amount greater than zero')
                  return
                }
                if (!exDescription.trim()) {
                  setExError('Describe what the money was spent on')
                  return
                }
                setExError(null)
                expense.mutate()
              }}
            >
              Post entry
            </Button>
          </>
        }
      >
        {accounts.isLoading ? (
          <div className="flex justify-center py-8">
            <InlineLoader />
          </div>
        ) : accounts.isError ? (
          <ErrorState
            message={errorMessage(accounts.error)}
            action={
              <Button variant="outline" size="sm" onClick={() => void accounts.refetch()}>
                Retry
              </Button>
            }
          />
        ) : expenseAccounts.length === 0 ? (
          <EmptyState
            title="No expense accounts"
            description="Add an account of type Expense in the chart of accounts first."
          />
        ) : (
          <div className="space-y-4">
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <FormRow label="Expense account" required className="sm:col-span-2">
                <Select value={exAccountId} onChange={(e) => setExAccountId(e.target.value)}>
                  <option value="">Choose an account…</option>
                  {expenseAccounts.map((a) => (
                    <option key={a.id} value={a.id}>
                      {a.code} — {a.name}
                    </option>
                  ))}
                </Select>
              </FormRow>
              <FormRow label="Paid from" required>
                <MethodSelect value={exMethod} onChange={setExMethod} />
              </FormRow>
              <FormRow label="Date" required>
                <Input type="date" value={exDate} onChange={(e) => setExDate(e.target.value)} />
              </FormRow>
              <FormRow label="Amount" required>
                <Input
                  type="number"
                  min="0.01"
                  step="0.01"
                  value={exAmount}
                  onChange={(e) => setExAmount(e.target.value)}
                />
              </FormRow>
              <FormRow label="Project">
                <Select
                  value={exProjectId}
                  onChange={(e) => setExProjectId(e.target.value)}
                  disabled={projects.isLoading}
                >
                  <option value="">Not tagged</option>
                  {(projects.data ?? []).map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.code} — {p.name}
                    </option>
                  ))}
                </Select>
              </FormRow>
              <FormRow label="Description" required className="sm:col-span-2">
                <Input
                  value={exDescription}
                  placeholder="October office rent"
                  onChange={(e) => setExDescription(e.target.value)}
                />
              </FormRow>
            </div>
            <Note>
              These are the overheads of running the place — rent, salary, electricity — the ones
              with no vendor document behind them. They hit the profit and loss immediately, below
              gross profit, because they are not the cost of any particular sale.
            </Note>
            <FormError message={exError} />
          </div>
        )}
      </Modal>

      {/* Over-the-counter trade */}
      <Modal
        open={dialog === 'directTrade'}
        onClose={close}
        size="lg"
        title="Over-the-counter trade"
        description="A purchase or sale with someone who is not registered on the platform."
        footer={
          <>
            <Button variant="outline" onClick={close}>
              Cancel
            </Button>
            <Button
              loading={directTrade.isPending}
              disabled={offlineContacts.length === 0 || productList.length === 0}
              onClick={() => {
                if (!dtCounterpartyId) {
                  setDtError('Choose who you traded with')
                  return
                }
                if (dtValidLines.length === 0) {
                  setDtError('Add at least one product with a quantity')
                  return
                }
                setDtError(null)
                directTrade.mutate()
              }}
            >
              Record {dtDirection === 'PURCHASE' ? 'purchase' : 'sale'}
            </Button>
          </>
        }
      >
        {contacts.isLoading || products.isLoading ? (
          <div className="flex justify-center py-8">
            <InlineLoader />
          </div>
        ) : contacts.isError ? (
          <ErrorState
            message={errorMessage(contacts.error)}
            action={
              <Button variant="outline" size="sm" onClick={() => void contacts.refetch()}>
                Retry
              </Button>
            }
          />
        ) : products.isError ? (
          <ErrorState
            message={errorMessage(products.error)}
            action={
              <Button variant="outline" size="sm" onClick={() => void products.refetch()}>
                Retry
              </Button>
            }
          />
        ) : offlineContacts.length === 0 ? (
          <EmptyState
            title="No offline counterparties"
            description="Every contact you have keeps books here, so each of them must go through the request and accept flow. Add an offline contact first."
          />
        ) : productList.length === 0 ? (
          <EmptyState
            title="No products"
            description="Add a product before recording a trade over the counter."
          />
        ) : (
          <div className="space-y-4">
            <div className="inline-flex rounded-lg border border-lilac-300 p-0.5">
              {(['PURCHASE', 'SALE'] as const).map((d) => (
                <button
                  key={d}
                  type="button"
                  onClick={() => setDtDirection(d)}
                  className={cn(
                    'rounded-md px-4 py-1.5 text-sm font-medium transition-colors',
                    dtDirection === d
                      ? 'bg-amethyst-600 text-white'
                      : 'text-muted-ink hover:bg-lilac-100',
                  )}
                >
                  {titleCase(d)}
                </button>
              ))}
            </div>

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <FormRow
                label={dtDirection === 'PURCHASE' ? 'Bought from' : 'Sold to'}
                required
              >
                <Select
                  value={dtCounterpartyId}
                  onChange={(e) => setDtCounterpartyId(e.target.value)}
                >
                  <option value="">Choose a counterparty…</option>
                  {offlineContacts.map((c) => (
                    <option key={c.partyId} value={c.partyId}>
                      {c.name} — {titleCase(c.relationship)}
                    </option>
                  ))}
                </Select>
              </FormRow>
              <FormRow label="Date" required>
                <Input type="date" value={dtDate} onChange={(e) => setDtDate(e.target.value)} />
              </FormRow>
            </div>

            <Note>
              <Badge tone="warning">Offline only</Badge> Only counterparties who are not registered
              here appear in this list. A registered party keeps their own books, so a deal with
              them has to be requested and accepted — you cannot write their side of it for them.
            </Note>

            <div>
              <div className="mb-2 flex items-center justify-between">
                <p className="text-xs font-medium tracking-wide text-muted-ink uppercase">Items</p>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => setDtLines((c) => [...c, emptyTradeLine()])}
                >
                  <Plus className="h-3.5 w-3.5" />
                  Add item
                </Button>
              </div>

              <div className="space-y-2">
                {dtLines.map((line) => {
                  const product = productById.get(line.productId)
                  const fallback = product
                    ? dtDirection === 'SALE'
                      ? product.salesPrice
                      : product.cost
                    : null
                  return (
                    <div
                      key={line.key}
                      className="grid grid-cols-12 items-end gap-2 rounded-lg border border-lilac-200 bg-lilac-50/40 p-2.5"
                    >
                      <div className="col-span-12 sm:col-span-6">
                        <label className="mb-1 block text-[10px] text-muted-ink uppercase">
                          Product
                        </label>
                        <Select
                          value={line.productId}
                          onChange={(e) => updateTradeLine(line.key, { productId: e.target.value })}
                        >
                          <option value="">Choose a product…</option>
                          {productList.map((p) => (
                            <option key={p.id} value={p.id}>
                              {p.name} — {titleCase(p.type)}
                              {p.tracksStock || p.type === 'COMBO'
                                ? ` · ${formatNumber(p.quantityOnHand ?? '0', 0)} on hand`
                                : ''}
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
                          onChange={(e) => updateTradeLine(line.key, { quantity: e.target.value })}
                        />
                      </div>
                      <div className="col-span-7 sm:col-span-3">
                        <label className="mb-1 block text-[10px] text-muted-ink uppercase">
                          Price override
                        </label>
                        <Input
                          type="number"
                          min="0"
                          step="0.01"
                          value={line.unitPrice}
                          placeholder={fallback ? formatNumber(fallback, 2) : 'From product'}
                          onChange={(e) => updateTradeLine(line.key, { unitPrice: e.target.value })}
                        />
                      </div>
                      <div className="col-span-1 flex justify-end">
                        <Button
                          type="button"
                          variant="ghost"
                          size="icon"
                          aria-label="Remove item"
                          disabled={dtLines.length === 1}
                          onClick={() =>
                            setDtLines((c) => c.filter((l) => l.key !== line.key))
                          }
                        >
                          <Trash2 className="h-4 w-4 text-muted-ink" />
                        </Button>
                      </div>
                    </div>
                  )
                })}
              </div>
            </div>

            <FormRow label="Notes">
              <Textarea rows={2} value={dtNotes} onChange={(e) => setDtNotes(e.target.value)} />
            </FormRow>

            <div className="flex justify-between rounded-lg border border-lilac-200 bg-lilac-50 p-3 text-sm font-semibold text-plum-800">
              <span>Untaxed total</span>
              <span className="tabular">{formatMoney(dtTotal)}</span>
            </div>

            <Note>
              {dtDirection === 'PURCHASE'
                ? 'Buying stocked goods capitalises into Inventory rather than becoming an expense. The cost only becomes cost of sales once the goods are delivered out.'
                : 'A sale takes the goods out of stock at their weighted-average cost, and that cost lands in cost of sales at the same moment the revenue does. Delivery is refused if stock is short.'}
            </Note>

            <FormError message={dtError} />
          </div>
        )}
      </Modal>
    </div>
  )
}
