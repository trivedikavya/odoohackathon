import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Check, Plus, X } from 'lucide-react'
import { PageHeader } from '@/components/layout/AppLayout'
import { Card, CardBody } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { FormRow, Input, Select } from '@/components/ui/Field'
import { Badge } from '@/components/ui/Badge'
import { Modal } from '@/components/ui/Modal'
import { EmptyRow, TD, TH, THead, TR, Table } from '@/components/ui/Table'
import { ErrorState, PageLoader } from '@/components/ui/PageLoader'
import { useToast } from '@/components/ui/Toast'
import { usersApi } from '@/api/endpoints'
import { errorMessage } from '@/api/client'
import type { AccessLevel } from '@/api/types'
import { cn, titleCase } from '@/lib/utils'

/** Mirrors the server-side CredentialPolicy so the user is not told off after the fact. */
const PASSWORD_RULES: { label: string; test: (value: string) => boolean }[] = [
  { label: 'At least 8 characters', test: (v) => v.length >= 8 },
  { label: 'One lowercase letter', test: (v) => /[a-z]/.test(v) },
  { label: 'One uppercase letter', test: (v) => /[A-Z]/.test(v) },
  { label: 'One special character', test: (v) => /[^A-Za-z0-9]/.test(v) },
]

const ACCESS_LEVELS: AccessLevel[] = ['ADMIN', 'ACCOUNTANT']

const accessTones: Record<string, 'brand' | 'info' | 'neutral'> = {
  ADMIN: 'brand',
  ACCOUNTANT: 'info',
  USER: 'neutral',
}

interface FormState {
  loginId: string
  email: string
  password: string
  fullName: string
  accessLevel: AccessLevel
}

const EMPTY_FORM: FormState = {
  loginId: '',
  email: '',
  password: '',
  fullName: '',
  accessLevel: 'ACCOUNTANT',
}

export function UsersPage() {
  const toast = useToast()
  const queryClient = useQueryClient()

  const [open, setOpen] = useState(false)
  const [form, setForm] = useState<FormState>(EMPTY_FORM)
  const [errors, setErrors] = useState<Partial<Record<keyof FormState, string>>>({})

  const query = useQuery({ queryKey: ['users'], queryFn: () => usersApi.list() })

  const invalidate = () => void queryClient.invalidateQueries({ queryKey: ['users'] })

  const create = useMutation({
    mutationFn: () =>
      usersApi.create({
        loginId: form.loginId.trim(),
        email: form.email.trim(),
        password: form.password,
        fullName: form.fullName.trim(),
        accessLevel: form.accessLevel,
      }),
    onSuccess: () => {
      toast.success('Colleague added')
      setOpen(false)
      setForm(EMPTY_FORM)
      invalidate()
    },
    onError: (error) => toast.error(errorMessage(error)),
  })

  const deactivate = useMutation({
    mutationFn: (id: number) => usersApi.deactivate(id),
    onSuccess: () => {
      toast.success('User deactivated')
      invalidate()
    },
    onError: (error) => toast.error(errorMessage(error)),
  })

  const activate = useMutation({
    mutationFn: (id: number) => usersApi.activate(id),
    onSuccess: () => {
      toast.success('User activated')
      invalidate()
    },
    onError: (error) => toast.error(errorMessage(error)),
  })

  const submit = () => {
    const next: Partial<Record<keyof FormState, string>> = {}
    if (!/^[A-Za-z0-9._-]{6,12}$/.test(form.loginId.trim()))
      next.loginId = '6–12 characters: letters, digits, dot, underscore or hyphen'
    if (!form.email.trim()) next.email = 'An email is required'
    if (!form.fullName.trim()) next.fullName = 'A name is required'
    if (PASSWORD_RULES.some((r) => !r.test(form.password)))
      next.password = 'The password does not meet every rule'
    setErrors(next)
    if (Object.keys(next).length > 0) return
    create.mutate()
  }

  const users = query.data ?? []

  return (
    <div>
      <PageHeader
        title="Users"
        description="Colleagues who can sign in to your organisation's books. A user belongs to your organisation only — they can never see another party's ledger."
        action={
          <Button
            size="sm"
            onClick={() => {
              setForm(EMPTY_FORM)
              setErrors({})
              setOpen(true)
            }}
          >
            <Plus className="h-4 w-4" />
            Add colleague
          </Button>
        }
      />

      {query.isLoading ? (
        <PageLoader label="Loading users…" />
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
                <TH className="w-40">Login ID</TH>
                <TH>Name</TH>
                <TH>Email</TH>
                <TH className="w-36">Access level</TH>
                <TH className="w-28">Status</TH>
                <TH className="w-32 text-right">Actions</TH>
              </tr>
            </THead>
            <tbody>
              {users.length === 0 ? (
                <EmptyRow colSpan={6}>No users yet.</EmptyRow>
              ) : (
                users.map((user) => (
                  <TR key={user.id} className={user.active ? undefined : 'opacity-60'}>
                    <TD className="tabular font-medium">{user.loginId}</TD>
                    <TD>{user.fullName}</TD>
                    <TD className="text-muted-ink">{user.email}</TD>
                    <TD>
                      <Badge tone={accessTones[user.accessLevel] ?? 'neutral'}>
                        {titleCase(user.accessLevel)}
                      </Badge>
                    </TD>
                    <TD>
                      {user.active ? (
                        <Badge tone="success">Active</Badge>
                      ) : (
                        <Badge tone="neutral">Inactive</Badge>
                      )}
                    </TD>
                    <TD className="text-right">
                      {user.active ? (
                        <Button
                          variant="ghost"
                          size="sm"
                          loading={deactivate.isPending && deactivate.variables === user.id}
                          onClick={() => deactivate.mutate(user.id)}
                        >
                          Deactivate
                        </Button>
                      ) : (
                        <Button
                          variant="ghost"
                          size="sm"
                          loading={activate.isPending && activate.variables === user.id}
                          onClick={() => activate.mutate(user.id)}
                        >
                          Activate
                        </Button>
                      )}
                    </TD>
                  </TR>
                ))
              )}
            </tbody>
          </Table>
          <CardBody className="border-t border-lilac-200 py-3">
            <p className="text-xs text-muted-ink">
              An administrator manages master data and the organisation record. An accountant works
              the deals, documents and ledger. Both are scoped to this organisation's book alone.
            </p>
          </CardBody>
        </Card>
      )}

      <Modal
        open={open}
        onClose={() => setOpen(false)}
        title="Add colleague"
        description="They will be able to sign in immediately with the password you set."
        footer={
          <>
            <Button variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button loading={create.isPending} onClick={submit}>
              Add colleague
            </Button>
          </>
        }
      >
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <FormRow label="Login ID" required error={errors.loginId}>
            <Input
              value={form.loginId}
              placeholder="asha.rao"
              onChange={(e) => setForm((f) => ({ ...f, loginId: e.target.value }))}
            />
          </FormRow>
          <FormRow label="Full name" required error={errors.fullName}>
            <Input
              value={form.fullName}
              onChange={(e) => setForm((f) => ({ ...f, fullName: e.target.value }))}
            />
          </FormRow>
          <FormRow label="Email" required error={errors.email}>
            <Input
              type="email"
              value={form.email}
              onChange={(e) => setForm((f) => ({ ...f, email: e.target.value }))}
            />
          </FormRow>
          <FormRow label="Access level" required>
            <Select
              value={form.accessLevel}
              onChange={(e) => setForm((f) => ({ ...f, accessLevel: e.target.value as AccessLevel }))}
            >
              {ACCESS_LEVELS.map((level) => (
                <option key={level} value={level}>
                  {titleCase(level)}
                </option>
              ))}
            </Select>
          </FormRow>
          <FormRow label="Password" required error={errors.password} className="sm:col-span-2">
            <Input
              type="password"
              value={form.password}
              onChange={(e) => setForm((f) => ({ ...f, password: e.target.value }))}
            />
            <ul className="mt-2 grid grid-cols-1 gap-1 sm:grid-cols-2">
              {PASSWORD_RULES.map((rule) => {
                const met = rule.test(form.password)
                return (
                  <li
                    key={rule.label}
                    className={cn(
                      'flex items-center gap-1.5 text-xs',
                      met ? 'text-emerald-700' : 'text-muted-ink',
                    )}
                  >
                    {met ? (
                      <Check className="h-3.5 w-3.5 shrink-0" />
                    ) : (
                      <X className="h-3.5 w-3.5 shrink-0" />
                    )}
                    {rule.label}
                  </li>
                )
              })}
            </ul>
          </FormRow>
        </div>
      </Modal>
    </div>
  )
}
