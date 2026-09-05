import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, ShieldCheck } from 'lucide-react'
import { errorMessage } from '@/api/client'
import { usersApi } from '@/api/endpoints'
import type { Role } from '@/api/types'
import { PageHeader } from '@/components/layout/AppLayout'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { FormRow, Input, Select } from '@/components/ui/Field'
import { Modal } from '@/components/ui/Modal'
import { ErrorState, PageLoader } from '@/components/ui/PageLoader'
import { EmptyRow, Table, TD, TH, THead, TR } from '@/components/ui/Table'
import { useToast } from '@/components/ui/Toast'

const blank = { email: '', password: '', fullName: '', role: 'ACCOUNTANT' as Role }

export function UsersPage() {
  const qc = useQueryClient()
  const toast = useToast()
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState(blank)

  const { data: users = [], isLoading, isError, error } = useQuery({
    queryKey: ['users'],
    queryFn: usersApi.list,
  })

  const create = useMutation({
    mutationFn: () => usersApi.create(form),
    onSuccess: (u) => {
      qc.invalidateQueries({ queryKey: ['users'] })
      setOpen(false)
      setForm(blank)
      toast.success(`${u.email} created`)
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  const toggleActive = useMutation({
    mutationFn: ({ id, active }: { id: number; active: boolean }) =>
      usersApi.update(id, { active }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['users'] })
      toast.success('User updated')
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  return (
    <>
      <PageHeader
        title="Users"
        description="Staff logins. Only the business owner can reach this screen — the server enforces it independently of the UI."
        action={
          <Button onClick={() => setOpen(true)}>
            <Plus className="h-4 w-4" />
            New user
          </Button>
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
                <TH>Name</TH>
                <TH>Email</TH>
                <TH>Role</TH>
                <TH>Status</TH>
                <TH className="text-right">Actions</TH>
              </tr>
            </THead>
            <tbody>
              {users.length === 0 ? (
                <EmptyRow colSpan={5}>No users.</EmptyRow>
              ) : (
                users.map((u) => (
                  <TR key={u.id} className={u.active ? '' : 'opacity-55'}>
                    <TD className="font-medium">{u.fullName}</TD>
                    <TD className="text-muted-ink">{u.email}</TD>
                    <TD>
                      <Badge tone={u.role === 'ADMIN' ? 'brand' : u.role === 'CONTACT' ? 'info' : 'neutral'}>
                        {u.role === 'ADMIN' && <ShieldCheck className="mr-1 h-3 w-3" />}
                        {u.role}
                      </Badge>
                    </TD>
                    <TD>
                      <Badge tone={u.active ? 'success' : 'neutral'}>
                        {u.active ? 'Active' : 'Disabled'}
                      </Badge>
                    </TD>
                    <TD>
                      <div className="flex justify-end">
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => toggleActive.mutate({ id: u.id, active: !u.active })}
                        >
                          {u.active ? 'Disable' : 'Enable'}
                        </Button>
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
        onClose={() => setOpen(false)}
        size="sm"
        title="New user"
        description="Accountants can create master data and record transactions, but cannot archive records or manage users."
        footer={
          <>
            <Button variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" form="user-form" loading={create.isPending}>
              Create user
            </Button>
          </>
        }
      >
        <form
          id="user-form"
          onSubmit={(e) => {
            e.preventDefault()
            create.mutate()
          }}
          className="space-y-4"
        >
          <FormRow label="Full name" required>
            <Input
              value={form.fullName}
              onChange={(e) => setForm({ ...form, fullName: e.target.value })}
              required
            />
          </FormRow>

          <FormRow label="Email" required>
            <Input
              type="email"
              value={form.email}
              onChange={(e) => setForm({ ...form, email: e.target.value })}
              required
            />
          </FormRow>

          <FormRow label="Password" required>
            <Input
              type="password"
              value={form.password}
              onChange={(e) => setForm({ ...form, password: e.target.value })}
              minLength={8}
              required
            />
          </FormRow>

          <FormRow label="Role" required>
            <Select
              value={form.role}
              onChange={(e) => setForm({ ...form, role: e.target.value as Role })}
            >
              <option value="ACCOUNTANT">Accountant (invoicing user)</option>
              <option value="ADMIN">Admin (business owner)</option>
            </Select>
          </FormRow>
        </form>
      </Modal>
    </>
  )
}
