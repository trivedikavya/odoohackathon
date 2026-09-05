import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { PageHeader } from '@/components/layout/AppLayout'
import { Card, CardBody } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { FormRow, Input, Select, Textarea } from '@/components/ui/Field'
import { Badge } from '@/components/ui/Badge'
import { Modal } from '@/components/ui/Modal'
import { EmptyRow, TD, TH, THead, TR, Table } from '@/components/ui/Table'
import { ErrorState, PageLoader } from '@/components/ui/PageLoader'
import { useToast } from '@/components/ui/Toast'
import { analyticApi } from '@/api/endpoints'
import { errorMessage } from '@/api/client'
import type { AnalyticAccount, AnalyticAccountType } from '@/api/types'
import { titleCase } from '@/lib/utils'

const TYPES: AnalyticAccountType[] = ['PROJECT', 'DEPARTMENT', 'COST_CENTER']

const typeTones: Record<AnalyticAccountType, 'brand' | 'info' | 'warning'> = {
  PROJECT: 'brand',
  DEPARTMENT: 'info',
  COST_CENTER: 'warning',
}

interface FormState {
  code: string
  name: string
  type: AnalyticAccountType
  notes: string
}

const EMPTY_FORM: FormState = { code: '', name: '', type: 'PROJECT', notes: '' }

export function AnalyticAccountsPage() {
  const toast = useToast()
  const queryClient = useQueryClient()

  const [includeArchived, setIncludeArchived] = useState(false)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<AnalyticAccount | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY_FORM)
  const [errors, setErrors] = useState<Partial<Record<keyof FormState, string>>>({})

  const query = useQuery({
    queryKey: ['analytic-accounts', includeArchived],
    queryFn: () => analyticApi.list(includeArchived),
  })

  const invalidate = () => void queryClient.invalidateQueries({ queryKey: ['analytic-accounts'] })

  const save = useMutation({
    mutationFn: () => {
      const body = {
        code: form.code.trim(),
        name: form.name.trim(),
        type: form.type,
        notes: form.notes.trim() || undefined,
      }
      return editing ? analyticApi.update(editing.id, body) : analyticApi.create(body)
    },
    onSuccess: () => {
      toast.success(editing ? 'Analytic account updated' : 'Analytic account created')
      setOpen(false)
      setEditing(null)
      invalidate()
    },
    onError: (error) => toast.error(errorMessage(error)),
  })

  const archive = useMutation({
    mutationFn: (id: number) => analyticApi.archive(id),
    onSuccess: () => {
      toast.success('Analytic account archived')
      invalidate()
    },
    onError: (error) => toast.error(errorMessage(error)),
  })

  const restore = useMutation({
    mutationFn: (id: number) => analyticApi.restore(id),
    onSuccess: () => {
      toast.success('Analytic account restored')
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

  const openEdit = (account: AnalyticAccount) => {
    setEditing(account)
    setForm({
      code: account.code,
      name: account.name,
      type: account.type,
      notes: account.notes ?? '',
    })
    setErrors({})
    setOpen(true)
  }

  const submit = () => {
    const next: Partial<Record<keyof FormState, string>> = {}
    if (!form.code.trim()) next.code = 'A code is required'
    if (!form.name.trim()) next.name = 'A name is required'
    setErrors(next)
    if (Object.keys(next).length > 0) return
    save.mutate()
  }

  const accounts = query.data ?? []

  return (
    <div>
      <PageHeader
        title="Analytic accounts"
        description="Projects, departments and cost centres you can tag lines against."
        action={
          <div className="flex items-center gap-3">
            <label className="flex items-center gap-2 text-sm text-plum-800">
              <input
                type="checkbox"
                className="h-4 w-4 rounded border-lilac-300 accent-amethyst-600"
                checked={includeArchived}
                onChange={(e) => setIncludeArchived(e.target.checked)}
              />
              Show archived
            </label>
            <Button size="sm" onClick={openCreate}>
              <Plus className="h-4 w-4" />
              Add analytic account
            </Button>
          </div>
        }
      />

      <Card className="mb-4">
        <CardBody className="space-y-2 py-4">
          <p className="text-sm text-plum-800">
            An analytic account is a second classification that runs alongside the chart of accounts.
            The financial account says <span className="font-medium">what kind of money moved</span> —
            timber, freight, consultancy. The analytic account says{' '}
            <span className="font-medium">whose project it belongs to</span> — the Andheri fit-out,
            the workshop, the design team.
          </p>
          <p className="text-xs text-muted-ink">
            Tagging is purely a label carried on the journal line. It never changes a debit or a
            credit, so no amount of retagging can put the double-entry out of balance.
          </p>
        </CardBody>
      </Card>

      {query.isLoading ? (
        <PageLoader label="Loading analytic accounts…" />
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
                <TH className="w-32">Code</TH>
                <TH>Name</TH>
                <TH className="w-40">Type</TH>
                <TH>Notes</TH>
                <TH className="w-44 text-right">Actions</TH>
              </tr>
            </THead>
            <tbody>
              {accounts.length === 0 ? (
                <EmptyRow colSpan={5}>No analytic accounts yet.</EmptyRow>
              ) : (
                accounts.map((account) => (
                  <TR key={account.id} className={account.active ? undefined : 'opacity-60'}>
                    <TD className="tabular text-muted-ink">{account.code}</TD>
                    <TD className="font-medium">
                      {account.name}
                      {!account.active && (
                        <Badge tone="neutral" className="ml-2">
                          Archived
                        </Badge>
                      )}
                    </TD>
                    <TD>
                      <Badge tone={typeTones[account.type]}>{titleCase(account.type)}</Badge>
                    </TD>
                    <TD className="text-xs text-muted-ink">{account.notes ?? '—'}</TD>
                    <TD className="text-right whitespace-nowrap">
                      <Button variant="ghost" size="sm" onClick={() => openEdit(account)}>
                        Edit
                      </Button>
                      {account.active ? (
                        <Button
                          variant="ghost"
                          size="sm"
                          loading={archive.isPending && archive.variables === account.id}
                          onClick={() => archive.mutate(account.id)}
                        >
                          Archive
                        </Button>
                      ) : (
                        <Button
                          variant="ghost"
                          size="sm"
                          loading={restore.isPending && restore.variables === account.id}
                          onClick={() => restore.mutate(account.id)}
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
        title={editing ? `Edit ${editing.name}` : 'Add analytic account'}
        size="sm"
        footer={
          <>
            <Button variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button loading={save.isPending} onClick={submit}>
              {editing ? 'Save changes' : 'Create'}
            </Button>
          </>
        }
      >
        <div className="space-y-4">
          <FormRow label="Code" required error={errors.code}>
            <Input
              value={form.code}
              placeholder="PRJ-ANDHERI"
              onChange={(e) => setForm((f) => ({ ...f, code: e.target.value }))}
            />
          </FormRow>
          <FormRow label="Name" required error={errors.name}>
            <Input
              value={form.name}
              placeholder="Andheri showroom fit-out"
              onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
            />
          </FormRow>
          <FormRow label="Type" required>
            <Select
              value={form.type}
              onChange={(e) =>
                setForm((f) => ({ ...f, type: e.target.value as AnalyticAccountType }))
              }
            >
              {TYPES.map((t) => (
                <option key={t} value={t}>
                  {titleCase(t)}
                </option>
              ))}
            </Select>
          </FormRow>
          <FormRow label="Notes">
            <Textarea
              value={form.notes}
              onChange={(e) => setForm((f) => ({ ...f, notes: e.target.value }))}
            />
          </FormRow>
        </div>
      </Modal>
    </div>
  )
}
