import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Lock, Plus } from 'lucide-react'
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
import type { AccountType } from '@/api/types'
import { useAuth } from '@/auth/AuthContext'
import { titleCase } from '@/lib/utils'

const ACCOUNT_TYPES: AccountType[] = ['ASSET', 'LIABILITY', 'EQUITY', 'INCOME', 'EXPENSE']

const typeTones: Record<AccountType, 'brand' | 'warning' | 'info' | 'success' | 'danger'> = {
  ASSET: 'brand',
  LIABILITY: 'warning',
  EQUITY: 'info',
  INCOME: 'success',
  EXPENSE: 'danger',
}

interface FormState {
  code: string
  name: string
  type: AccountType
}

const EMPTY_FORM: FormState = { code: '', name: '', type: 'ASSET' }

export function AccountsPage() {
  const { isAdmin } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()

  const [includeArchived, setIncludeArchived] = useState(false)
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState<FormState>(EMPTY_FORM)
  const [errors, setErrors] = useState<Partial<Record<keyof FormState, string>>>({})

  const query = useQuery({
    queryKey: ['accounts', includeArchived],
    queryFn: () => masterApi.accounts(includeArchived),
  })

  const invalidate = () => void queryClient.invalidateQueries({ queryKey: ['accounts'] })

  const create = useMutation({
    mutationFn: () => masterApi.createAccount({ ...form }),
    onSuccess: () => {
      toast.success('Account created')
      setOpen(false)
      setForm(EMPTY_FORM)
      invalidate()
    },
    onError: (error) => toast.error(errorMessage(error)),
  })

  const archive = useMutation({
    mutationFn: (id: number) => masterApi.archiveAccount(id),
    onSuccess: () => {
      toast.success('Account archived')
      invalidate()
    },
    onError: (error) => toast.error(errorMessage(error)),
  })

  const submit = () => {
    const next: Partial<Record<keyof FormState, string>> = {}
    if (!form.code.trim()) next.code = 'A code is required'
    if (!form.name.trim()) next.name = 'A name is required'
    setErrors(next)
    if (Object.keys(next).length > 0) return
    create.mutate()
  }

  const accounts = query.data ?? []

  return (
    <div>
      <PageHeader
        title="Chart of accounts"
        description="The financial accounts your book posts into."
        action={
          <div className="flex items-end gap-3">
            <label className="flex items-center gap-2 text-sm text-plum-800">
              <input
                type="checkbox"
                className="h-4 w-4 rounded border-lilac-300 accent-amethyst-600"
                checked={includeArchived}
                onChange={(e) => setIncludeArchived(e.target.checked)}
              />
              Show archived
            </label>
            {isAdmin && (
              <Button
                size="sm"
                onClick={() => {
                  setForm(EMPTY_FORM)
                  setErrors({})
                  setOpen(true)
                }}
              >
                <Plus className="h-4 w-4" />
                Add account
              </Button>
            )}
          </div>
        }
      />

      <Card className="mb-4">
        <CardBody className="py-3">
          <p className="text-xs text-muted-ink">
            Accounts marked <span className="font-medium text-plum-800">System</span> are resolved by
            the posting engine through a stable internal code, not by their name or position. An
            invoice always finds its receivable account that way. That is why a system account cannot
            be archived or renamed out of existence — doing so would leave the posting engine with
            nowhere to write.
          </p>
        </CardBody>
      </Card>

      {query.isLoading ? (
        <PageLoader label="Loading the chart of accounts…" />
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
                <TH className="w-36">Type</TH>
                <TH className="w-32">System</TH>
                <TH className="w-32 text-right">Actions</TH>
              </tr>
            </THead>
            <tbody>
              {accounts.length === 0 ? (
                <EmptyRow colSpan={5}>No accounts found.</EmptyRow>
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
                    <TD>
                      {account.isSystem ? (
                        <Badge tone="info">
                          <Lock className="mr-1 h-3 w-3" />
                          System
                        </Badge>
                      ) : (
                        <span className="text-muted-ink">—</span>
                      )}
                    </TD>
                    <TD className="text-right">
                      {isAdmin && account.active && !account.isSystem ? (
                        <Button
                          variant="ghost"
                          size="sm"
                          loading={archive.isPending && archive.variables === account.id}
                          onClick={() => archive.mutate(account.id)}
                        >
                          Archive
                        </Button>
                      ) : (
                        <span className="text-xs text-muted-ink">—</span>
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
        title="Add account"
        description="A new account becomes available to manual entries straight away."
        size="sm"
        footer={
          <>
            <Button variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button loading={create.isPending} onClick={submit}>
              Create account
            </Button>
          </>
        }
      >
        <div className="space-y-4">
          <FormRow label="Code" required error={errors.code}>
            <Input
              value={form.code}
              placeholder="1200"
              onChange={(e) => setForm((f) => ({ ...f, code: e.target.value }))}
            />
          </FormRow>
          <FormRow label="Name" required error={errors.name}>
            <Input
              value={form.name}
              placeholder="Inventory"
              onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
            />
          </FormRow>
          <FormRow label="Type" required>
            <Select
              value={form.type}
              onChange={(e) => setForm((f) => ({ ...f, type: e.target.value as AccountType }))}
            >
              {ACCOUNT_TYPES.map((t) => (
                <option key={t} value={t}>
                  {titleCase(t)}
                </option>
              ))}
            </Select>
          </FormRow>
        </div>
      </Modal>
    </div>
  )
}
