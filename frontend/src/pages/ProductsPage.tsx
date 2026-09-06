import { Fragment, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronDown, ChevronRight, Plus, Search, Trash2 } from 'lucide-react'
import { PageHeader } from '@/components/layout/AppLayout'
import { Card, CardBody } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { FormRow, Input, Select } from '@/components/ui/Field'
import { Badge } from '@/components/ui/Badge'
import { Modal } from '@/components/ui/Modal'
import { EmptyRow, TD, TH, THead, TR, Table } from '@/components/ui/Table'
import { ErrorState, PageLoader } from '@/components/ui/PageLoader'
import { useToast } from '@/components/ui/Toast'
import { masterApi, type ProductPayload } from '@/api/endpoints'
import { errorMessage } from '@/api/client'
import type { Product, ProductType } from '@/api/types'
import { formatMoney, formatNumber, titleCase } from '@/lib/utils'

interface ComponentRow {
  key: string
  componentProductId: string
  quantity: string
}

interface FormState {
  name: string
  type: ProductType
  category: string
  hsnCode: string
  salesPrice: string
  cost: string
  taxRate: string
  components: ComponentRow[]
}

const emptyComponent = (): ComponentRow => ({
  key: crypto.randomUUID(),
  componentProductId: '',
  quantity: '1',
})

const EMPTY_FORM: FormState = {
  name: '',
  type: 'GOODS',
  category: '',
  hsnCode: '',
  salesPrice: '0',
  cost: '0',
  taxRate: '0',
  components: [emptyComponent()],
}

const TYPE_TONES: Record<ProductType, 'neutral' | 'info' | 'brand'> = {
  GOODS: 'neutral',
  SERVICE: 'info',
  COMBO: 'brand',
}

function toForm(product: Product): FormState {
  return {
    name: product.name,
    type: product.type,
    category: product.category ?? '',
    hsnCode: product.hsnCode ?? '',
    salesPrice: product.salesPrice,
    cost: product.cost,
    taxRate: product.taxRate,
    components:
      product.components.length > 0
        ? product.components.map((c) => ({
            key: crypto.randomUUID(),
            componentProductId: String(c.componentProductId),
            quantity: c.quantity,
          }))
        : [emptyComponent()],
  }
}

/** A service holds no stock, so there is no figure to show rather than a zero. */
function OnHand({ product }: { product: Product }) {
  if (!product.tracksStock && product.type !== 'COMBO') {
    return <span className="text-muted-ink">—</span>
  }
  return <span className="tabular">{formatNumber(product.quantityOnHand ?? '0', 2)}</span>
}

export function ProductsPage() {
  const toast = useToast()
  const queryClient = useQueryClient()

  const [search, setSearch] = useState('')
  const [includeArchived, setIncludeArchived] = useState(false)
  const [editing, setEditing] = useState<Product | null>(null)
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState<FormState>(EMPTY_FORM)
  const [errors, setErrors] = useState<Partial<Record<keyof FormState, string>>>({})
  const [expanded, setExpanded] = useState<Record<number, boolean>>({})

  const query = useQuery({
    queryKey: ['products', search, includeArchived],
    queryFn: () => masterApi.products({ search: search || undefined, includeArchived }),
  })

  // The builder needs every product that could be a component, not just the filtered view.
  const allProducts = useQuery({
    queryKey: ['products', '', false],
    queryFn: () => masterApi.products(),
  })

  const invalidate = () => void queryClient.invalidateQueries({ queryKey: ['products'] })

  const payload = (): ProductPayload => ({
    name: form.name.trim(),
    type: form.type,
    category: form.category.trim() || null,
    hsnCode: form.hsnCode.trim() || null,
    salesPrice: form.salesPrice || '0',
    cost: form.cost || '0',
    taxRate: form.taxRate || '0',
    components:
      form.type === 'COMBO'
        ? form.components
            .filter((c) => c.componentProductId && Number(c.quantity) > 0)
            .map((c) => ({
              componentProductId: Number(c.componentProductId),
              quantity: c.quantity,
            }))
        : [],
  })

  const save = useMutation({
    mutationFn: () =>
      editing ? masterApi.updateProduct(editing.id, payload()) : masterApi.createProduct(payload()),
    onSuccess: () => {
      toast.success(editing ? 'Product updated' : 'Product created')
      setOpen(false)
      setEditing(null)
      invalidate()
    },
    onError: (error) => toast.error(errorMessage(error)),
  })

  const archive = useMutation({
    mutationFn: (id: number) => masterApi.archiveProduct(id),
    onSuccess: () => {
      toast.success('Product archived')
      invalidate()
    },
    onError: (error) => toast.error(errorMessage(error)),
  })

  const restore = useMutation({
    mutationFn: (id: number) => masterApi.restoreProduct(id),
    onSuccess: () => {
      toast.success('Product restored')
      invalidate()
    },
    onError: (error) => toast.error(errorMessage(error)),
  })

  const openCreate = () => {
    setEditing(null)
    setForm(EMPTY_FORM)
    setErrors({})
    setOpen(true)
  }

  const openEdit = (product: Product) => {
    setEditing(product)
    setForm(toForm(product))
    setErrors({})
    setOpen(true)
  }

  const submit = () => {
    const next: Partial<Record<keyof FormState, string>> = {}
    if (!form.name.trim()) next.name = 'A name is required'
    if (Number.isNaN(Number(form.salesPrice))) next.salesPrice = 'Must be a number'
    if (Number.isNaN(Number(form.cost))) next.cost = 'Must be a number'
    if (Number.isNaN(Number(form.taxRate))) next.taxRate = 'Must be a number'
    if (
      form.type === 'COMBO' &&
      !form.components.some((c) => c.componentProductId && Number(c.quantity) > 0)
    ) {
      next.components = 'A combo needs at least one component'
    }
    setErrors(next)
    if (Object.keys(next).length > 0) return
    save.mutate()
  }

  const setComponent = (key: string, patch: Partial<ComponentRow>) =>
    setForm((f) => ({
      ...f,
      components: f.components.map((c) => (c.key === key ? { ...c, ...patch } : c)),
    }))

  const products = query.data ?? []
  // A combo cannot contain another combo, so those are never offered as parts.
  const componentChoices = (allProducts.data ?? []).filter(
    (p) => p.type !== 'COMBO' && p.id !== editing?.id,
  )

  const toggle = (id: number) => setExpanded((cur) => ({ ...cur, [id]: !cur[id] }))

  return (
    <div>
      <PageHeader
        title="Products"
        description="Goods, services and combos you buy and sell, with their default pricing, tax rate and stock. A combo carries no stock of its own — selling one consumes its components, so its on-hand figure is however many whole bundles the scarcest component can build."
        action={
          <Button size="sm" onClick={openCreate}>
            <Plus className="h-4 w-4" />
            Add product
          </Button>
        }
      />

      <Card className="mb-4">
        <CardBody className="flex flex-wrap items-end gap-3 py-4">
          <div>
            <label className="mb-1 block text-[10px] font-medium text-muted-ink uppercase">
              Search
            </label>
            <div className="relative">
              <Search className="absolute top-1/2 left-2.5 h-4 w-4 -translate-y-1/2 text-muted-ink" />
              <Input
                className="w-64 pl-8"
                placeholder="Name, HSN or category"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
            </div>
          </div>
          <label className="flex h-10 items-center gap-2 text-sm text-plum-800">
            <input
              type="checkbox"
              className="h-4 w-4 rounded border-lilac-300 accent-amethyst-600"
              checked={includeArchived}
              onChange={(e) => setIncludeArchived(e.target.checked)}
            />
            Show archived
          </label>
        </CardBody>
      </Card>

      {query.isLoading ? (
        <PageLoader label="Loading products…" />
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
      ) : (
        <Card>
          <Table>
            <THead>
              <tr>
                <TH className="w-10" />
                <TH>Name</TH>
                <TH className="w-28">Type</TH>
                <TH className="w-36">Category</TH>
                <TH className="w-24">HSN</TH>
                <TH className="w-32 text-right">Sales price</TH>
                <TH className="w-32 text-right">Cost</TH>
                <TH className="w-28 text-right">On hand</TH>
                <TH className="w-32 text-right">Avg cost</TH>
                <TH className="w-20 text-right">Tax %</TH>
                <TH className="w-44 text-right">Actions</TH>
              </tr>
            </THead>
            <tbody>
              {products.length === 0 ? (
                <EmptyRow colSpan={11}>
                  {search ? 'No products match that search.' : 'No products yet.'}
                </EmptyRow>
              ) : (
                products.map((product) => {
                  const isCombo = product.type === 'COMBO'
                  const isOpen = Boolean(expanded[product.id])
                  return (
                    <Fragment key={product.id}>
                      <TR className={product.active ? undefined : 'opacity-60'}>
                        <TD>
                          {isCombo && (
                            <button
                              type="button"
                              aria-label={isOpen ? 'Hide recipe' : 'Show recipe'}
                              onClick={() => toggle(product.id)}
                              className="rounded p-0.5 hover:bg-lilac-100"
                            >
                              {isOpen ? (
                                <ChevronDown className="h-4 w-4 text-amethyst-600" />
                              ) : (
                                <ChevronRight className="h-4 w-4 text-muted-ink" />
                              )}
                            </button>
                          )}
                        </TD>
                        <TD className="font-medium">
                          {product.name}
                          {!product.active && (
                            <Badge tone="neutral" className="ml-2">
                              Archived
                            </Badge>
                          )}
                        </TD>
                        <TD>
                          <Badge tone={TYPE_TONES[product.type]}>{titleCase(product.type)}</Badge>
                        </TD>
                        <TD>{product.category ?? <span className="text-muted-ink">—</span>}</TD>
                        <TD className="tabular text-xs">
                          {product.hsnCode ?? <span className="text-muted-ink">—</span>}
                        </TD>
                        <TD className="tabular text-right">{formatMoney(product.salesPrice)}</TD>
                        <TD className="tabular text-right">{formatMoney(product.cost)}</TD>
                        <TD className="text-right">
                          <OnHand product={product} />
                        </TD>
                        <TD className="text-right">
                          {product.averageCost === null ? (
                            <span className="text-muted-ink">—</span>
                          ) : (
                            <span className="tabular">{formatMoney(product.averageCost)}</span>
                          )}
                        </TD>
                        <TD className="tabular text-right">{formatNumber(product.taxRate, 2)}</TD>
                        <TD className="text-right whitespace-nowrap">
                          <Button variant="ghost" size="sm" onClick={() => openEdit(product)}>
                            Edit
                          </Button>
                          {product.active ? (
                            <Button
                              variant="ghost"
                              size="sm"
                              loading={archive.isPending && archive.variables === product.id}
                              onClick={() => archive.mutate(product.id)}
                            >
                              Archive
                            </Button>
                          ) : (
                            <Button
                              variant="ghost"
                              size="sm"
                              loading={restore.isPending && restore.variables === product.id}
                              onClick={() => restore.mutate(product.id)}
                            >
                              Restore
                            </Button>
                          )}
                        </TD>
                      </TR>
                      {isCombo && isOpen && (
                        <tr>
                          <td colSpan={11} className="border-b border-lilac-100 bg-lilac-50/60 p-0">
                            <div className="px-4 py-3">
                              <p className="mb-2 text-xs font-medium tracking-wide text-muted-ink uppercase">
                                What one {product.name} contains
                              </p>
                              {product.components.length === 0 ? (
                                <p className="text-xs text-muted-ink">
                                  This combo has no components yet, so no bundle can be built.
                                </p>
                              ) : (
                                <table className="w-full max-w-xl text-xs">
                                  <thead>
                                    <tr className="text-left text-muted-ink">
                                      <th className="py-1 pr-4 font-medium">Component</th>
                                      <th className="py-1 pr-4 text-right font-medium">
                                        Per bundle
                                      </th>
                                      <th className="py-1 text-right font-medium">
                                        Component on hand
                                      </th>
                                    </tr>
                                  </thead>
                                  <tbody>
                                    {product.components.map((c) => (
                                      <tr key={c.componentProductId} className="text-plum-800">
                                        <td className="py-1.5 pr-4 font-medium">
                                          {c.componentName}
                                        </td>
                                        <td className="tabular py-1.5 pr-4 text-right">
                                          {formatNumber(c.quantity, 2)}
                                        </td>
                                        <td className="tabular py-1.5 text-right">
                                          {formatNumber(c.quantityOnHand, 2)}
                                        </td>
                                      </tr>
                                    ))}
                                  </tbody>
                                </table>
                              )}
                              <p className="mt-2 text-xs text-muted-ink">
                                Buildable now:{' '}
                                <span className="tabular font-medium text-plum-800">
                                  {formatNumber(product.quantityOnHand ?? '0', 2)}
                                </span>{' '}
                                bundle(s) — limited by the scarcest component.
                              </p>
                            </div>
                          </td>
                        </tr>
                      )}
                    </Fragment>
                  )
                })
              )}
            </tbody>
          </Table>
        </Card>
      )}

      <Modal
        open={open}
        onClose={() => setOpen(false)}
        title={editing ? `Edit ${editing.name}` : 'Add product'}
        description="Defaults used when this product is added to a deal line."
        footer={
          <>
            <Button variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button loading={save.isPending} onClick={submit}>
              {editing ? 'Save changes' : 'Create product'}
            </Button>
          </>
        }
      >
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <FormRow label="Name" required error={errors.name} className="sm:col-span-2">
            <Input
              value={form.name}
              onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
            />
          </FormRow>
          <FormRow label="Type" required>
            <Select
              value={form.type}
              onChange={(e) => setForm((f) => ({ ...f, type: e.target.value as ProductType }))}
            >
              <option value="GOODS">Goods</option>
              <option value="SERVICE">Service</option>
              <option value="COMBO">Combo</option>
            </Select>
          </FormRow>
          <FormRow label="Category">
            <Input
              value={form.category}
              placeholder="Seating"
              onChange={(e) => setForm((f) => ({ ...f, category: e.target.value }))}
            />
          </FormRow>
          <FormRow label="HSN code">
            <Input
              value={form.hsnCode}
              placeholder="9401"
              onChange={(e) => setForm((f) => ({ ...f, hsnCode: e.target.value }))}
            />
          </FormRow>
          <FormRow label="Tax rate %" error={errors.taxRate}>
            <Input
              type="number"
              step="0.01"
              value={form.taxRate}
              onChange={(e) => setForm((f) => ({ ...f, taxRate: e.target.value }))}
            />
          </FormRow>
          <FormRow label="Sales price" error={errors.salesPrice}>
            <Input
              type="number"
              step="0.01"
              value={form.salesPrice}
              onChange={(e) => setForm((f) => ({ ...f, salesPrice: e.target.value }))}
            />
          </FormRow>
          <FormRow label="Cost" error={errors.cost}>
            <Input
              type="number"
              step="0.01"
              value={form.cost}
              onChange={(e) => setForm((f) => ({ ...f, cost: e.target.value }))}
            />
          </FormRow>

          {form.type === 'COMBO' && (
            <div className="sm:col-span-2">
              <div className="mb-2 flex items-center justify-between">
                <p className="text-xs font-medium tracking-wide text-muted-ink uppercase">
                  Bundle contents
                </p>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() =>
                    setForm((f) => ({ ...f, components: [...f.components, emptyComponent()] }))
                  }
                >
                  <Plus className="h-3.5 w-3.5" />
                  Add component
                </Button>
              </div>

              {componentChoices.length === 0 ? (
                <p className="rounded-lg border border-dashed border-lilac-300 bg-lilac-50/50 px-3 py-4 text-center text-xs text-muted-ink">
                  There are no goods or services to bundle yet. Create those first.
                </p>
              ) : (
                <div className="space-y-2">
                  {form.components.map((row) => (
                    <div
                      key={row.key}
                      className="grid grid-cols-12 items-end gap-2 rounded-lg border border-lilac-200 bg-lilac-50/40 p-2.5"
                    >
                      <div className="col-span-7 sm:col-span-8">
                        <label className="mb-1 block text-[10px] text-muted-ink uppercase">
                          Component
                        </label>
                        <Select
                          value={row.componentProductId}
                          onChange={(e) =>
                            setComponent(row.key, { componentProductId: e.target.value })
                          }
                        >
                          <option value="">Choose a product…</option>
                          {componentChoices.map((p) => (
                            <option key={p.id} value={p.id}>
                              {p.name} — {titleCase(p.type)}
                            </option>
                          ))}
                        </Select>
                      </div>
                      <div className="col-span-4 sm:col-span-3">
                        <label className="mb-1 block text-[10px] text-muted-ink uppercase">
                          Qty per bundle
                        </label>
                        <Input
                          type="number"
                          min="0.001"
                          step="0.001"
                          value={row.quantity}
                          onChange={(e) => setComponent(row.key, { quantity: e.target.value })}
                        />
                      </div>
                      <div className="col-span-1 flex justify-end">
                        <Button
                          type="button"
                          variant="ghost"
                          size="icon"
                          aria-label="Remove component"
                          disabled={form.components.length === 1}
                          onClick={() =>
                            setForm((f) => ({
                              ...f,
                              components: f.components.filter((c) => c.key !== row.key),
                            }))
                          }
                        >
                          <Trash2 className="h-4 w-4 text-muted-ink" />
                        </Button>
                      </div>
                    </div>
                  ))}
                </div>
              )}

              {errors.components && (
                <p className="mt-1 text-xs text-red-600">{errors.components}</p>
              )}
              <p className="mt-2 text-xs text-muted-ink">
                A combo holds no stock of its own. Selling one takes the components out of stock
                instead, so it can only be sold as often as its scarcest component allows.
              </p>
            </div>
          )}
        </div>
      </Modal>
    </div>
  )
}
