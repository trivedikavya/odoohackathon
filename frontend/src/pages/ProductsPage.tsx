import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Archive, ArchiveRestore, Pencil, Plus } from 'lucide-react'
import { errorMessage } from '@/api/client'
import { productsApi } from '@/api/endpoints'
import type { Product, ProductType } from '@/api/types'
import { useAuth } from '@/auth/AuthContext'
import { PageHeader } from '@/components/layout/AppLayout'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { FormRow, Input, Select } from '@/components/ui/Field'
import { Modal } from '@/components/ui/Modal'
import { ErrorState, PageLoader } from '@/components/ui/PageLoader'
import { EmptyRow, Table, TD, TH, THead, TR } from '@/components/ui/Table'
import { useToast } from '@/components/ui/Toast'
import { formatMoney, titleCase } from '@/lib/utils'

const blank = {
  name: '',
  type: 'GOODS' as ProductType,
  salesPrice: '0',
  cost: '0',
  category: '',
  hsnCode: '',
  taxRate: '0',
}

export function ProductsPage() {
  const qc = useQueryClient()
  const toast = useToast()
  const { isAdmin } = useAuth()

  const [search, setSearch] = useState('')
  const [includeArchived, setIncludeArchived] = useState(false)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Product | null>(null)
  const [form, setForm] = useState(blank)

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['products', search, includeArchived],
    queryFn: () => productsApi.search({ search: search || undefined, includeArchived, size: 100 }),
  })

  useEffect(() => {
    if (!open) return
    setForm(
      editing
        ? {
            name: editing.name,
            type: editing.type,
            salesPrice: editing.salesPrice,
            cost: editing.cost,
            category: editing.category ?? '',
            hsnCode: editing.hsnCode ?? '',
            taxRate: editing.taxRate,
          }
        : blank,
    )
  }, [open, editing])

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['products'] })
    qc.invalidateQueries({ queryKey: ['product-options'] })
    qc.invalidateQueries({ queryKey: ['dashboard'] })
  }

  const save = useMutation({
    mutationFn: (body: typeof blank) =>
      editing ? productsApi.update(editing.id, body) : productsApi.create(body),
    onSuccess: (p) => {
      invalidate()
      setOpen(false)
      setEditing(null)
      toast.success(`${p.name} saved`)
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  const archive = useMutation({
    mutationFn: ({ id, archived }: { id: number; archived: boolean }) =>
      archived ? productsApi.restore(id) : productsApi.archive(id),
    onSuccess: () => {
      invalidate()
      toast.success('Product updated')
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  const products = data?.content ?? []

  return (
    <>
      <PageHeader
        title="Products"
        description="Goods, services and combos available on orders and invoices."
        action={
          <Button
            onClick={() => {
              setEditing(null)
              setOpen(true)
            }}
          >
            <Plus className="h-4 w-4" />
            New product
          </Button>
        }
      />

      <div className="mb-4 flex flex-wrap items-center gap-3">
        <Input
          placeholder="Search by name or category…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="w-full sm:max-w-xs"
        />
        <label className="flex items-center gap-2 text-sm text-muted-ink">
          <input
            type="checkbox"
            checked={includeArchived}
            onChange={(e) => setIncludeArchived(e.target.checked)}
            className="h-4 w-4 rounded border-lilac-300 accent-[#663399]"
          />
          Show archived
        </label>
      </div>

      <Card>
        {isLoading ? (
          <PageLoader />
        ) : isError ? (
          <ErrorState message={errorMessage(error)} />
        ) : (
          <Table>
            <THead>
              <tr>
                <TH>Name</TH>
                <TH>Type</TH>
                <TH>Category</TH>
                <TH className="text-right">Sales price</TH>
                <TH className="text-right">Cost</TH>
                <TH className="text-right">Tax %</TH>
                <TH className="text-right">Actions</TH>
              </tr>
            </THead>
            <tbody>
              {products.length === 0 ? (
                <EmptyRow colSpan={7}>No products found.</EmptyRow>
              ) : (
                products.map((p) => (
                  <TR key={p.id} className={p.active ? '' : 'opacity-55'}>
                    <TD className="font-medium">
                      {p.name}
                      {!p.active && (
                        <Badge tone="neutral" className="ml-2">
                          Archived
                        </Badge>
                      )}
                    </TD>
                    <TD>
                      <Badge tone={p.type === 'GOODS' ? 'brand' : 'info'}>
                        {titleCase(p.type)}
                      </Badge>
                    </TD>
                    <TD className="text-muted-ink">{p.category || '—'}</TD>
                    <TD className="tabular text-right font-medium">{formatMoney(p.salesPrice)}</TD>
                    <TD className="tabular text-right text-muted-ink">{formatMoney(p.cost)}</TD>
                    <TD className="tabular text-right text-muted-ink">{Number(p.taxRate)}%</TD>
                    <TD>
                      <div className="flex justify-end gap-1">
                        <Button
                          size="sm"
                          variant="ghost"
                          aria-label="Edit"
                          onClick={() => {
                            setEditing(p)
                            setOpen(true)
                          }}
                        >
                          <Pencil className="h-3.5 w-3.5" />
                        </Button>
                        {isAdmin && (
                          <Button
                            size="sm"
                            variant="ghost"
                            aria-label={p.active ? 'Archive' : 'Restore'}
                            onClick={() => archive.mutate({ id: p.id, archived: !p.active })}
                          >
                            {p.active ? (
                              <Archive className="h-3.5 w-3.5" />
                            ) : (
                              <ArchiveRestore className="h-3.5 w-3.5" />
                            )}
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
        onClose={() => {
          setOpen(false)
          setEditing(null)
        }}
        title={editing ? `Edit ${editing.name}` : 'New product'}
        footer={
          <>
            <Button
              variant="outline"
              onClick={() => {
                setOpen(false)
                setEditing(null)
              }}
            >
              Cancel
            </Button>
            <Button type="submit" form="product-form" loading={save.isPending}>
              Save product
            </Button>
          </>
        }
      >
        <form
          id="product-form"
          onSubmit={(e) => {
            e.preventDefault()
            save.mutate(form)
          }}
          className="grid gap-4 sm:grid-cols-2"
        >
          <FormRow label="Name" required className="sm:col-span-2">
            <Input
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
              required
              maxLength={180}
            />
          </FormRow>

          <FormRow label="Type" required>
            <Select
              value={form.type}
              onChange={(e) => setForm({ ...form, type: e.target.value as ProductType })}
            >
              <option value="GOODS">Goods</option>
              <option value="SERVICE">Service</option>
              <option value="COMBO">Combo</option>
            </Select>
          </FormRow>

          <FormRow label="Category">
            <Input
              value={form.category}
              onChange={(e) => setForm({ ...form, category: e.target.value })}
            />
          </FormRow>

          <FormRow label="Sales price" required>
            <Input
              type="number"
              min="0"
              step="0.01"
              value={form.salesPrice}
              onChange={(e) => setForm({ ...form, salesPrice: e.target.value })}
              required
            />
          </FormRow>

          <FormRow label="Cost" required>
            <Input
              type="number"
              min="0"
              step="0.01"
              value={form.cost}
              onChange={(e) => setForm({ ...form, cost: e.target.value })}
              required
            />
          </FormRow>

          <FormRow label="HSN code">
            <Input
              value={form.hsnCode}
              onChange={(e) => setForm({ ...form, hsnCode: e.target.value })}
              maxLength={20}
            />
          </FormRow>

          <FormRow label="Default tax rate (%)">
            <Input
              type="number"
              min="0"
              max="100"
              step="0.01"
              value={form.taxRate}
              onChange={(e) => setForm({ ...form, taxRate: e.target.value })}
            />
          </FormRow>
        </form>
      </Modal>
    </>
  )
}
