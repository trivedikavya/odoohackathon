import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Search } from 'lucide-react'
import { PageHeader } from '@/components/layout/AppLayout'
import { Card, CardBody } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { FormRow, Input, Select } from '@/components/ui/Field'
import { Badge } from '@/components/ui/Badge'
import { Modal } from '@/components/ui/Modal'
import { EmptyRow, TD, TH, THead, TR, Table } from '@/components/ui/Table'
import { ErrorState, PageLoader } from '@/components/ui/PageLoader'
import { useToast } from '@/components/ui/Toast'
import { masterApi } from '@/api/endpoints'
import { errorMessage } from '@/api/client'
import type { Product, ProductType } from '@/api/types'
import { formatMoney, formatNumber } from '@/lib/utils'

interface FormState {
  name: string
  type: ProductType
  category: string
  hsnCode: string
  salesPrice: string
  cost: string
  taxRate: string
}

const EMPTY_FORM: FormState = {
  name: '',
  type: 'GOODS',
  category: '',
  hsnCode: '',
  salesPrice: '0',
  cost: '0',
  taxRate: '0',
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
  }
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

  const query = useQuery({
    queryKey: ['products', search, includeArchived],
    queryFn: () => masterApi.products({ search: search || undefined, includeArchived }),
  })

  const invalidate = () => void queryClient.invalidateQueries({ queryKey: ['products'] })

  const payload = (): Partial<Product> => ({
    name: form.name.trim(),
    type: form.type,
    category: form.category.trim() || null,
    hsnCode: form.hsnCode.trim() || null,
    salesPrice: form.salesPrice || '0',
    cost: form.cost || '0',
    taxRate: form.taxRate || '0',
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
    setErrors(next)
    if (Object.keys(next).length > 0) return
    save.mutate()
  }

  const products = query.data ?? []

  return (
    <div>
      <PageHeader
        title="Products"
        description="Goods and services you buy and sell, with their default pricing and tax rate."
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
                <TH>Name</TH>
                <TH className="w-28">Type</TH>
                <TH className="w-40">Category</TH>
                <TH className="w-28">HSN</TH>
                <TH className="w-36 text-right">Sales price</TH>
                <TH className="w-36 text-right">Cost</TH>
                <TH className="w-24 text-right">Tax %</TH>
                <TH className="w-44 text-right">Actions</TH>
              </tr>
            </THead>
            <tbody>
              {products.length === 0 ? (
                <EmptyRow colSpan={8}>
                  {search ? 'No products match that search.' : 'No products yet.'}
                </EmptyRow>
              ) : (
                products.map((product) => (
                  <TR key={product.id} className={product.active ? undefined : 'opacity-60'}>
                    <TD className="font-medium">
                      {product.name}
                      {!product.active && (
                        <Badge tone="neutral" className="ml-2">
                          Archived
                        </Badge>
                      )}
                    </TD>
                    <TD>
                      <Badge tone={product.type === 'GOODS' ? 'brand' : 'info'}>
                        {product.type === 'GOODS' ? 'Goods' : 'Service'}
                      </Badge>
                    </TD>
                    <TD>{product.category ?? <span className="text-muted-ink">—</span>}</TD>
                    <TD className="tabular text-xs">
                      {product.hsnCode ?? <span className="text-muted-ink">—</span>}
                    </TD>
                    <TD className="tabular text-right">{formatMoney(product.salesPrice)}</TD>
                    <TD className="tabular text-right">{formatMoney(product.cost)}</TD>
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
                ))
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
        </div>
      </Modal>
    </div>
  )
}
