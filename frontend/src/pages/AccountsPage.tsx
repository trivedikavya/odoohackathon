import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Lock, Plus } from 'lucide-react'
import { errorMessage } from '@/api/client'
import { accountsApi } from '@/api/endpoints'
import type { AccountType } from '@/api/types'
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

const typeTone: Record<AccountType, 'brand' | 'warning' | 'info' | 'success' | 'danger'> = {
  ASSET: 'brand',
  LIABILITY: 'warning',
  EQUITY: 'info',
  INCOME: 'success',
  EXPENSE: 'danger',
}

export function AccountsPage() {
  const qc = useQueryClient()
  const toast = useToast()
  const { isAdmin } = useAuth()

  const [open, setOpen] = useState(false)
  const [form, setForm] = useState({ code: '', name: '', type: 'ASSET' as AccountType })

  const { data: accounts = [], isLoading, isError, error } = useQuery({
    queryKey: ['accounts'],
    queryFn: () => accountsApi.list(true),
  })

  const create = useMutation({
    mutationFn: () => accountsApi.create(form),
    onSuccess: (a) => {
      qc.invalidateQueries({ queryKey: ['accounts'] })
      setOpen(false)
      setForm({ code: '', name: '', type: 'ASSET' })
      toast.success(`Account ${a.code} created`)
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  return (
    <>
      <PageHeader
        title="Chart of Accounts"
        description="The account structure every journal entry posts against. System accounts are locked because the posting rules depend on them."
        action={
          isAdmin ? (
            <Button onClick={() => setOpen(true)}>
              <Plus className="h-4 w-4" />
              New account
            </Button>
          ) : undefined
        }
      />

      <Card>
        {isLoading ? (
          <PageLoader />
        ) : isError ? (
          <ErrorState message={errorMessage(error)} />
        ) : (
          <Table>
            <THead>
              <tr>
                <TH>Code</TH>
                <TH>Name</TH>
                <TH>Type</TH>
                <TH>System role</TH>
              </tr>
            </THead>
            <tbody>
              {accounts.length === 0 ? (
                <EmptyRow colSpan={4}>No accounts configured.</EmptyRow>
              ) : (
                accounts.map((a) => (
                  <TR key={a.id} className={a.active ? '' : 'opacity-55'}>
                    <TD className="tabular font-medium">{a.code}</TD>
                    <TD>
                      <div className="flex items-center gap-2">
                        {a.name}
                        {a.isSystem && <Lock className="h-3 w-3 text-violet-soft-500" />}
                      </div>
                    </TD>
                    <TD>
                      <Badge tone={typeTone[a.type]}>{titleCase(a.type)}</Badge>
                    </TD>
                    <TD className="text-xs text-muted-ink">
                      {a.systemCode ? titleCase(a.systemCode) : '—'}
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
        size="sm"
        title="New account"
        description="Added accounts are available immediately for reporting."
        footer={
          <>
            <Button variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" form="account-form" loading={create.isPending}>
              Create account
            </Button>
          </>
        }
      >
        <form
          id="account-form"
          onSubmit={(e) => {
            e.preventDefault()
            create.mutate()
          }}
          className="space-y-4"
        >
          <FormRow label="Code" required>
            <Input
              value={form.code}
              onChange={(e) => setForm({ ...form, code: e.target.value })}
              placeholder="e.g. 5200"
              required
              maxLength={20}
            />
          </FormRow>

          <FormRow label="Name" required>
            <Input
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
              required
              maxLength={150}
            />
          </FormRow>

          <FormRow label="Type" required>
            <Select
              value={form.type}
              onChange={(e) => setForm({ ...form, type: e.target.value as AccountType })}
            >
              <option value="ASSET">Asset</option>
              <option value="LIABILITY">Liability</option>
              <option value="EQUITY">Equity</option>
              <option value="INCOME">Income</option>
              <option value="EXPENSE">Expense</option>
            </Select>
          </FormRow>
        </form>
      </Modal>
    </>
  )
}
