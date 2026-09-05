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
import { ErrorState, InlineLoader, PageLoader } from '@/components/ui/PageLoader'
import { useToast } from '@/components/ui/Toast'
import { analyticApi } from '@/api/endpoints'
import { errorMessage } from '@/api/client'
import { addDays, formatDate, formatMoney, today } from '@/lib/utils'

interface FormState {
  name: string
  analyticAccountId: string
  periodStart: string
  periodEnd: string
  plannedAmount: string
  responsible: string
  notes: string
}

function emptyForm(): FormState {
  return {
    name: '',
    analyticAccountId: '',
    periodStart: today(),
    periodEnd: addDays(today(), 90),
    plannedAmount: '0',
    responsible: '',
    notes: '',
  }
}

export function BudgetsPage() {
  const toast = useToast()
  const queryClient = useQueryClient()

  const [includeArchived, setIncludeArchived] = useState(false)
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState<FormState>(emptyForm)
  const [errors, setErrors] = useState<Partial<Record<keyof FormState, string>>>({})

  const query = useQuery({
    queryKey: ['budgets', includeArchived],
    queryFn: () => analyticApi.budgets(includeArchived),
  })

  const analytics = useQuery({
    queryKey: ['analytic-accounts', false],
    queryFn: () => analyticApi.list(false),
  })

  const invalidate = () => void queryClient.invalidateQueries({ queryKey: ['budgets'] })

  const create = useMutation({
    mutationFn: () =>
      analyticApi.createBudget({
        name: form.name.trim(),
        analyticAccountId: Number(form.analyticAccountId),
        periodStart: form.periodStart,
        periodEnd: form.periodEnd,
        plannedAmount: form.plannedAmount || '0',
        responsible: form.responsible.trim() || undefined,
        notes: form.notes.trim() || undefined,
      }),
    onSuccess: () => {
      toast.success('Budget created')
      setOpen(false)
      setForm(emptyForm())
      invalidate()
    },
    onError: (error) => toast.error(errorMessage(error)),
  })

  const archive = useMutation({
    mutationFn: (id: number) => analyticApi.archiveBudget(id),
    onSuccess: () => {
      toast.success('Budget archived')
      invalidate()
    },
    onError: (error) => toast.error(errorMessage(error)),
  })

  const submit = () => {
    const next: Partial<Record<keyof FormState, string>> = {}
    if (!form.name.trim()) next.name = 'A name is required'
    if (!form.analyticAccountId) next.analyticAccountId = 'Pick an analytic account'
    if (!form.periodStart) next.periodStart = 'A start date is required'
    if (!form.periodEnd) next.periodEnd = 'An end date is required'
    if (form.periodStart && form.periodEnd && form.periodEnd < form.periodStart)
      next.periodEnd = 'The end date cannot be before the start date'
    if (Number.isNaN(Number(form.plannedAmount))) next.plannedAmount = 'Must be a number'
    setErrors(next)
    if (Object.keys(next).length > 0) return
    create.mutate()
  }

  const budgets = query.data ?? []
  const analyticOptions = analytics.data ?? []

  return (
    <div>
      <PageHeader
        title="Budgets"
        description="A planned figure for an analytic account over a period."
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
            <Button
              size="sm"
              onClick={() => {
                setForm(emptyForm())
                setErrors({})
                setOpen(true)
              }}
            >
              <Plus className="h-4 w-4" />
              Add budget
            </Button>
          </div>
        }
      />

      {query.isLoading ? (
        <PageLoader label="Loading budgets…" />
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
                <TH>Analytic account</TH>
                <TH className="w-56">Period</TH>
                <TH className="w-40 text-right">Planned</TH>
                <TH className="w-40">Responsible</TH>
                <TH className="w-32 text-right">Actions</TH>
              </tr>
            </THead>
            <tbody>
              {budgets.length === 0 ? (
                <EmptyRow colSpan={6}>No budgets yet.</EmptyRow>
              ) : (
                budgets.map((budget) => (
                  <TR key={budget.id} className={budget.active ? undefined : 'opacity-60'}>
                    <TD className="font-medium">
                      {budget.name}
                      {!budget.active && (
                        <Badge tone="neutral" className="ml-2">
                          Archived
                        </Badge>
                      )}
                    </TD>
                    <TD>
                      <span className="tabular mr-1.5 text-xs text-muted-ink">
                        {budget.analyticAccountCode}
                      </span>
                      {budget.analyticAccountName}
                    </TD>
                    <TD className="text-xs text-muted-ink">
                      {formatDate(budget.periodStart)} — {formatDate(budget.periodEnd)}
                    </TD>
                    <TD className="tabular text-right">{formatMoney(budget.plannedAmount)}</TD>
                    <TD>{budget.responsible ?? <span className="text-muted-ink">—</span>}</TD>
                    <TD className="text-right">
                      {budget.active ? (
                        <Button
                          variant="ghost"
                          size="sm"
                          loading={archive.isPending && archive.variables === budget.id}
                          onClick={() => archive.mutate(budget.id)}
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
          <CardBody className="border-t border-lilac-200 py-3">
            <p className="text-xs text-muted-ink">
              Only the planned amount is stored here. Actual spend is never entered — it is read back
              from the ledger for the tagged analytic account, so the comparison cannot drift away
              from what was really posted.
            </p>
          </CardBody>
        </Card>
      )}

      <Modal
        open={open}
        onClose={() => setOpen(false)}
        title="Add budget"
        description="Actuals will be measured against this figure automatically."
        footer={
          <>
            <Button variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button loading={create.isPending} onClick={submit}>
              Create budget
            </Button>
          </>
        }
      >
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <FormRow label="Name" required error={errors.name} className="sm:col-span-2">
            <Input
              value={form.name}
              placeholder="Andheri fit-out — Q1"
              onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
            />
          </FormRow>

          <FormRow
            label="Analytic account"
            required
            error={errors.analyticAccountId}
            className="sm:col-span-2"
          >
            {analytics.isLoading ? (
              <div className="flex h-10 items-center gap-2 text-sm text-muted-ink">
                <InlineLoader /> Loading analytic accounts…
              </div>
            ) : analytics.isError ? (
              <p className="text-xs text-red-600">{errorMessage(analytics.error)}</p>
            ) : analyticOptions.length === 0 ? (
              <p className="text-xs text-muted-ink">
                Create an analytic account first — a budget must be attached to one.
              </p>
            ) : (
              <Select
                value={form.analyticAccountId}
                onChange={(e) => setForm((f) => ({ ...f, analyticAccountId: e.target.value }))}
              >
                <option value="">Select an analytic account</option>
                {analyticOptions.map((a) => (
                  <option key={a.id} value={String(a.id)}>
                    {a.code} — {a.name}
                  </option>
                ))}
              </Select>
            )}
          </FormRow>

          <FormRow label="Period start" required error={errors.periodStart}>
            <Input
              type="date"
              value={form.periodStart}
              onChange={(e) => setForm((f) => ({ ...f, periodStart: e.target.value }))}
            />
          </FormRow>
          <FormRow label="Period end" required error={errors.periodEnd}>
            <Input
              type="date"
              value={form.periodEnd}
              onChange={(e) => setForm((f) => ({ ...f, periodEnd: e.target.value }))}
            />
          </FormRow>
          <FormRow label="Planned amount" required error={errors.plannedAmount}>
            <Input
              type="number"
              step="0.01"
              value={form.plannedAmount}
              onChange={(e) => setForm((f) => ({ ...f, plannedAmount: e.target.value }))}
            />
          </FormRow>
          <FormRow label="Responsible">
            <Input
              value={form.responsible}
              placeholder="Asha Rao"
              onChange={(e) => setForm((f) => ({ ...f, responsible: e.target.value }))}
            />
          </FormRow>
          <FormRow label="Notes" className="sm:col-span-2">
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
