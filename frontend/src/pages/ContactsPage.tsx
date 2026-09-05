import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Archive, ArchiveRestore, Pencil, Plus, Users } from 'lucide-react'
import { errorMessage } from '@/api/client'
import { contactsApi } from '@/api/endpoints'
import type { Contact, ContactType } from '@/api/types'
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
import { titleCase } from '@/lib/utils'

const blank = {
  name: '',
  type: 'CUSTOMER' as ContactType,
  email: '',
  mobile: '',
  addressLine: '',
  city: '',
  state: '',
  pincode: '',
  gstin: '',
}

export function ContactsPage() {
  const qc = useQueryClient()
  const toast = useToast()
  const { isAdmin } = useAuth()

  const [search, setSearch] = useState('')
  const [includeArchived, setIncludeArchived] = useState(false)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Contact | null>(null)
  const [form, setForm] = useState(blank)

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['contacts', search, includeArchived],
    queryFn: () => contactsApi.search({ search: search || undefined, includeArchived, size: 100 }),
  })

  useEffect(() => {
    if (!open) return
    setForm(
      editing
        ? {
            name: editing.name,
            type: editing.type,
            email: editing.email ?? '',
            mobile: editing.mobile ?? '',
            addressLine: editing.addressLine ?? '',
            city: editing.city ?? '',
            state: editing.state ?? '',
            pincode: editing.pincode ?? '',
            gstin: editing.gstin ?? '',
          }
        : blank,
    )
  }, [open, editing])

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['contacts'] })
    qc.invalidateQueries({ queryKey: ['contact-options'] })
    qc.invalidateQueries({ queryKey: ['dashboard'] })
  }

  const save = useMutation({
    mutationFn: (body: typeof blank) =>
      editing ? contactsApi.update(editing.id, body) : contactsApi.create(body),
    onSuccess: (c) => {
      invalidate()
      setOpen(false)
      setEditing(null)
      toast.success(`${c.name} saved`)
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  const archive = useMutation({
    mutationFn: ({ id, archived }: { id: number; archived: boolean }) =>
      archived ? contactsApi.restore(id) : contactsApi.archive(id),
    onSuccess: () => {
      invalidate()
      toast.success('Contact updated')
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  const contacts = data?.content ?? []

  return (
    <>
      <PageHeader
        title="Contacts"
        description="Customers and vendors used across sales and purchase transactions."
        action={
          <Button
            onClick={() => {
              setEditing(null)
              setOpen(true)
            }}
          >
            <Plus className="h-4 w-4" />
            New contact
          </Button>
        }
      />

      <div className="mb-4 flex flex-wrap items-center gap-3">
        <Input
          placeholder="Search by name, email or mobile…"
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
                <TH>Email</TH>
                <TH>Mobile</TH>
                <TH>City / State</TH>
                <TH className="text-right">Actions</TH>
              </tr>
            </THead>
            <tbody>
              {contacts.length === 0 ? (
                <EmptyRow colSpan={6}>No contacts found.</EmptyRow>
              ) : (
                contacts.map((c) => (
                  <TR key={c.id} className={c.active ? '' : 'opacity-55'}>
                    <TD className="font-medium">
                      <div className="flex items-center gap-2">
                        <div className="flex h-7 w-7 items-center justify-center rounded-full bg-lilac-200 text-xs font-semibold text-amethyst-800">
                          {c.name.charAt(0).toUpperCase()}
                        </div>
                        {c.name}
                        {!c.active && <Badge tone="neutral">Archived</Badge>}
                      </div>
                    </TD>
                    <TD>
                      <Badge tone="brand">{titleCase(c.type)}</Badge>
                    </TD>
                    <TD className="text-muted-ink">{c.email || '—'}</TD>
                    <TD className="text-muted-ink">{c.mobile || '—'}</TD>
                    <TD className="text-muted-ink">
                      {[c.city, c.state].filter(Boolean).join(', ') || '—'}
                    </TD>
                    <TD>
                      <div className="flex justify-end gap-1">
                        <Button
                          size="sm"
                          variant="ghost"
                          aria-label="Edit"
                          onClick={() => {
                            setEditing(c)
                            setOpen(true)
                          }}
                        >
                          <Pencil className="h-3.5 w-3.5" />
                        </Button>
                        {/* Archiving is Admin-only; the server rejects it for
                            accountants regardless of what the UI shows. */}
                        {isAdmin && (
                          <Button
                            size="sm"
                            variant="ghost"
                            aria-label={c.active ? 'Archive' : 'Restore'}
                            onClick={() => archive.mutate({ id: c.id, archived: !c.active })}
                          >
                            {c.active ? (
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
        title={editing ? `Edit ${editing.name}` : 'New contact'}
        description="A contact can act as a customer, a vendor, or both."
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
            <Button type="submit" form="contact-form" loading={save.isPending}>
              Save contact
            </Button>
          </>
        }
      >
        <form
          id="contact-form"
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
              onChange={(e) => setForm({ ...form, type: e.target.value as ContactType })}
            >
              <option value="CUSTOMER">Customer</option>
              <option value="VENDOR">Vendor</option>
              <option value="BOTH">Both</option>
            </Select>
          </FormRow>

          <FormRow label="Email">
            <Input
              type="email"
              value={form.email}
              onChange={(e) => setForm({ ...form, email: e.target.value })}
            />
          </FormRow>

          <FormRow label="Mobile">
            <Input
              value={form.mobile}
              onChange={(e) => setForm({ ...form, mobile: e.target.value })}
            />
          </FormRow>

          <FormRow label="GSTIN">
            <Input
              value={form.gstin}
              onChange={(e) => setForm({ ...form, gstin: e.target.value })}
              maxLength={20}
            />
          </FormRow>

          <FormRow label="Address" className="sm:col-span-2">
            <Input
              value={form.addressLine}
              onChange={(e) => setForm({ ...form, addressLine: e.target.value })}
            />
          </FormRow>

          <FormRow label="City">
            <Input value={form.city} onChange={(e) => setForm({ ...form, city: e.target.value })} />
          </FormRow>

          <FormRow label="State">
            <Input
              value={form.state}
              onChange={(e) => setForm({ ...form, state: e.target.value })}
            />
          </FormRow>

          <FormRow label="Pincode">
            <Input
              value={form.pincode}
              onChange={(e) => setForm({ ...form, pincode: e.target.value })}
            />
          </FormRow>
        </form>
      </Modal>
    </>
  )
}

export const ContactsIcon = Users
