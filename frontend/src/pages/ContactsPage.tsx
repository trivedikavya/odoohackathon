import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link2, Plus, Search } from 'lucide-react'
import { PageHeader } from '@/components/layout/AppLayout'
import { Card, CardBody } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { FormRow, Input, Select } from '@/components/ui/Field'
import { Badge } from '@/components/ui/Badge'
import { Modal } from '@/components/ui/Modal'
import { EmptyRow, TD, TH, THead, TR, Table } from '@/components/ui/Table'
import { EmptyState, ErrorState, InlineLoader, PageLoader } from '@/components/ui/PageLoader'
import { useToast } from '@/components/ui/Toast'
import { masterApi } from '@/api/endpoints'
import { errorMessage } from '@/api/client'
import type { ContactRelationship, PartyType } from '@/api/types'
import { titleCase } from '@/lib/utils'

const PARTY_TYPES: PartyType[] = ['SELLER', 'VENDOR', 'CUSTOMER']

const RELATIONSHIPS: ContactRelationship[] = ['CUSTOMER', 'VENDOR', 'BOTH']

const typeTones: Record<PartyType, 'brand' | 'info' | 'warning'> = {
  SELLER: 'brand',
  VENDOR: 'warning',
  CUSTOMER: 'info',
}

const relationshipTones: Record<ContactRelationship, 'brand' | 'info' | 'warning'> = {
  CUSTOMER: 'info',
  VENDOR: 'warning',
  BOTH: 'brand',
}

interface OfflineForm {
  name: string
  type: PartyType
  relationship: ContactRelationship
  email: string
  phone: string
  gstin: string
  addressLine: string
  city: string
  state: string
  pincode: string
  creditDays: string
}

const EMPTY_OFFLINE: OfflineForm = {
  name: '',
  type: 'CUSTOMER',
  relationship: 'CUSTOMER',
  email: '',
  phone: '',
  gstin: '',
  addressLine: '',
  city: '',
  state: '',
  pincode: '',
  creditDays: '30',
}

export function ContactsPage() {
  const toast = useToast()
  const queryClient = useQueryClient()

  const [search, setSearch] = useState('')
  const [includeArchived, setIncludeArchived] = useState(false)
  const [offlineOpen, setOfflineOpen] = useState(false)
  const [linkOpen, setLinkOpen] = useState(false)
  const [form, setForm] = useState<OfflineForm>(EMPTY_OFFLINE)
  const [errors, setErrors] = useState<Partial<Record<keyof OfflineForm, string>>>({})
  const [linkCreditDays, setLinkCreditDays] = useState('30')

  const query = useQuery({
    queryKey: ['contacts', search, includeArchived],
    queryFn: () => masterApi.contacts({ search: search || undefined, includeArchived }),
  })

  // Only fetched while the link dialog is open — it is a directory-wide lookup.
  const suppliers = useQuery({
    queryKey: ['suppliers'],
    queryFn: () => masterApi.suppliers(),
    enabled: linkOpen,
  })

  const invalidate = () => void queryClient.invalidateQueries({ queryKey: ['contacts'] })

  const createOffline = useMutation({
    mutationFn: () =>
      masterApi.createContact({
        name: form.name.trim(),
        type: form.type,
        relationship: form.relationship,
        email: form.email.trim() || undefined,
        phone: form.phone.trim() || undefined,
        gstin: form.gstin.trim() || undefined,
        addressLine: form.addressLine.trim() || undefined,
        city: form.city.trim() || undefined,
        state: form.state.trim() || undefined,
        pincode: form.pincode.trim() || undefined,
        creditDays: form.creditDays ? Number(form.creditDays) : undefined,
      }),
    onSuccess: () => {
      toast.success('Contact added')
      setOfflineOpen(false)
      setForm(EMPTY_OFFLINE)
      invalidate()
    },
    onError: (error) => toast.error(errorMessage(error)),
  })

  const link = useMutation({
    mutationFn: (partyId: number) =>
      masterApi.linkContact(partyId, linkCreditDays ? Number(linkCreditDays) : undefined),
    onSuccess: () => {
      toast.success('Counterparty linked')
      setLinkOpen(false)
      invalidate()
    },
    onError: (error) => toast.error(errorMessage(error)),
  })

  const archive = useMutation({
    mutationFn: (id: number) => masterApi.archiveContact(id),
    onSuccess: () => {
      toast.success('Contact archived')
      invalidate()
    },
    onError: (error) => toast.error(errorMessage(error)),
  })

  const restore = useMutation({
    mutationFn: (id: number) => masterApi.restoreContact(id),
    onSuccess: () => {
      toast.success('Contact restored')
      invalidate()
    },
    onError: (error) => toast.error(errorMessage(error)),
  })

  const submitOffline = () => {
    const next: Partial<Record<keyof OfflineForm, string>> = {}
    if (!form.name.trim()) next.name = 'A name is required'
    if (form.creditDays && Number.isNaN(Number(form.creditDays)))
      next.creditDays = 'Must be a number'
    setErrors(next)
    if (Object.keys(next).length > 0) return
    createOffline.mutate()
  }

  const contacts = query.data ?? []
  const linkedPartyIds = new Set(contacts.map((c) => c.partyId))
  const linkable = (suppliers.data ?? []).filter((s) => !linkedPartyIds.has(s.id))

  return (
    <div>
      <PageHeader
        title="Contacts"
        description="Everyone you trade with. Counterparties marked “On platform” keep their own books, so any deal you agree with them is mirrored into their ledger automatically. The relationship label is only there for filtering — the direction of a trade is decided deal by deal, so a contact marked Customer can still supply you."
        action={
          <div className="flex gap-2">
            <Button variant="outline" size="sm" onClick={() => setLinkOpen(true)}>
              <Link2 className="h-4 w-4" />
              Link registered party
            </Button>
            <Button
              size="sm"
              onClick={() => {
                setForm(EMPTY_OFFLINE)
                setErrors({})
                setOfflineOpen(true)
              }}
            >
              <Plus className="h-4 w-4" />
              Add offline contact
            </Button>
          </div>
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
                placeholder="Name, email or city"
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
        <PageLoader label="Loading contacts…" />
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
                <TH className="w-32">Relationship</TH>
                <TH>Email</TH>
                <TH className="w-36">Phone</TH>
                <TH className="w-44">City / State</TH>
                <TH className="w-28 text-right">Credit days</TH>
                <TH className="w-36">Platform</TH>
                <TH className="w-32 text-right">Actions</TH>
              </tr>
            </THead>
            <tbody>
              {contacts.length === 0 ? (
                <EmptyRow colSpan={9}>
                  {search ? 'No contacts match that search.' : 'No contacts yet.'}
                </EmptyRow>
              ) : (
                contacts.map((contact) => (
                  <TR key={contact.id} className={contact.active ? undefined : 'opacity-60'}>
                    <TD className="font-medium">
                      {contact.name}
                      {!contact.active && (
                        <Badge tone="neutral" className="ml-2">
                          Archived
                        </Badge>
                      )}
                    </TD>
                    <TD>
                      <Badge tone={typeTones[contact.type]}>{titleCase(contact.type)}</Badge>
                    </TD>
                    <TD>
                      <Badge tone={relationshipTones[contact.relationship]}>
                        {titleCase(contact.relationship)}
                      </Badge>
                    </TD>
                    <TD>{contact.email ?? <span className="text-muted-ink">—</span>}</TD>
                    <TD>{contact.phone ?? <span className="text-muted-ink">—</span>}</TD>
                    <TD className="text-xs">
                      {contact.city || contact.state ? (
                        [contact.city, contact.state].filter(Boolean).join(', ')
                      ) : (
                        <span className="text-muted-ink">—</span>
                      )}
                    </TD>
                    <TD className="tabular text-right">{contact.creditDays}</TD>
                    <TD>
                      {contact.keepsBooks ? (
                        <Badge tone="success">On platform</Badge>
                      ) : (
                        <span className="text-xs text-muted-ink">Offline</span>
                      )}
                    </TD>
                    <TD className="text-right">
                      {contact.active ? (
                        <Button
                          variant="ghost"
                          size="sm"
                          loading={archive.isPending && archive.variables === contact.id}
                          onClick={() => archive.mutate(contact.id)}
                        >
                          Archive
                        </Button>
                      ) : (
                        <Button
                          variant="ghost"
                          size="sm"
                          loading={restore.isPending && restore.variables === contact.id}
                          onClick={() => restore.mutate(contact.id)}
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
        open={offlineOpen}
        onClose={() => setOfflineOpen(false)}
        title="Add offline contact"
        description="For counterparties who are not registered on the platform. Their side of a deal is not mirrored anywhere."
        footer={
          <>
            <Button variant="outline" onClick={() => setOfflineOpen(false)}>
              Cancel
            </Button>
            <Button loading={createOffline.isPending} onClick={submitOffline}>
              Add contact
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
              onChange={(e) => setForm((f) => ({ ...f, type: e.target.value as PartyType }))}
            >
              {PARTY_TYPES.map((t) => (
                <option key={t} value={t}>
                  {titleCase(t)}
                </option>
              ))}
            </Select>
          </FormRow>
          <FormRow label="Relationship" required>
            <Select
              value={form.relationship}
              onChange={(e) =>
                setForm((f) => ({ ...f, relationship: e.target.value as ContactRelationship }))
              }
            >
              {RELATIONSHIPS.map((r) => (
                <option key={r} value={r}>
                  {titleCase(r)}
                </option>
              ))}
            </Select>
          </FormRow>
          <FormRow label="Credit days" error={errors.creditDays}>
            <Input
              type="number"
              value={form.creditDays}
              onChange={(e) => setForm((f) => ({ ...f, creditDays: e.target.value }))}
            />
          </FormRow>
          <p className="text-xs text-muted-ink sm:col-span-2">
            The relationship is a filing label, not a rule. Whether a deal is a purchase or a sale
            is decided when you raise it, so a contact marked Customer can still supply you.
          </p>
          <FormRow label="Email">
            <Input
              type="email"
              value={form.email}
              onChange={(e) => setForm((f) => ({ ...f, email: e.target.value }))}
            />
          </FormRow>
          <FormRow label="Phone">
            <Input
              value={form.phone}
              onChange={(e) => setForm((f) => ({ ...f, phone: e.target.value }))}
            />
          </FormRow>
          <FormRow label="GSTIN" className="sm:col-span-2">
            <Input
              value={form.gstin}
              onChange={(e) => setForm((f) => ({ ...f, gstin: e.target.value }))}
            />
          </FormRow>
          <FormRow label="Address" className="sm:col-span-2">
            <Input
              value={form.addressLine}
              onChange={(e) => setForm((f) => ({ ...f, addressLine: e.target.value }))}
            />
          </FormRow>
          <FormRow label="City">
            <Input
              value={form.city}
              onChange={(e) => setForm((f) => ({ ...f, city: e.target.value }))}
            />
          </FormRow>
          <FormRow label="State">
            <Input
              value={form.state}
              onChange={(e) => setForm((f) => ({ ...f, state: e.target.value }))}
            />
          </FormRow>
          <FormRow label="Pincode">
            <Input
              value={form.pincode}
              onChange={(e) => setForm((f) => ({ ...f, pincode: e.target.value }))}
            />
          </FormRow>
        </div>
      </Modal>

      <Modal
        open={linkOpen}
        onClose={() => setLinkOpen(false)}
        title="Link registered party"
        description="These parties already keep books here. Linking one means every deal you agree is written into both ledgers."
        footer={
          <Button variant="outline" onClick={() => setLinkOpen(false)}>
            Done
          </Button>
        }
      >
        <div className="mb-4 max-w-40">
          <FormRow label="Credit days">
            <Input
              type="number"
              value={linkCreditDays}
              onChange={(e) => setLinkCreditDays(e.target.value)}
            />
          </FormRow>
        </div>

        {suppliers.isLoading ? (
          <div className="flex justify-center py-8">
            <InlineLoader />
          </div>
        ) : suppliers.isError ? (
          <ErrorState
            message={errorMessage(suppliers.error)}
            action={
              <Button variant="outline" size="sm" onClick={() => void suppliers.refetch()}>
                Retry
              </Button>
            }
          />
        ) : linkable.length === 0 ? (
          <EmptyState
            title="Nothing left to link"
            description="Every registered party is already in your contact list."
          />
        ) : (
          <div className="divide-y divide-lilac-100 rounded-lg border border-lilac-200">
            {linkable.map((party) => (
              <div key={party.id} className="flex items-center justify-between gap-3 px-3 py-2.5">
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium text-plum-800">{party.name}</p>
                  <p className="text-xs text-muted-ink">
                    {titleCase(party.type)}
                    {party.city || party.state
                      ? ` — ${[party.city, party.state].filter(Boolean).join(', ')}`
                      : ''}
                  </p>
                </div>
                <Button
                  size="sm"
                  variant="outline"
                  loading={link.isPending && link.variables === party.id}
                  onClick={() => link.mutate(party.id)}
                >
                  Link
                </Button>
              </div>
            ))}
          </div>
        )}
      </Modal>
    </div>
  )
}
