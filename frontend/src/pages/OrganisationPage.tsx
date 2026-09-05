import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, Lock } from 'lucide-react'
import { PageHeader } from '@/components/layout/AppLayout'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { FormRow, Input } from '@/components/ui/Field'
import { Badge } from '@/components/ui/Badge'
import { ErrorState, PageLoader } from '@/components/ui/PageLoader'
import { useToast } from '@/components/ui/Toast'
import { masterApi } from '@/api/endpoints'
import { errorMessage } from '@/api/client'
import { useAuth } from '@/auth/AuthContext'
import { titleCase } from '@/lib/utils'

interface FormState {
  name: string
  gstin: string
  addressLine: string
  city: string
  state: string
  pincode: string
  phone: string
}

const EMPTY_FORM: FormState = {
  name: '',
  gstin: '',
  addressLine: '',
  city: '',
  state: '',
  pincode: '',
  phone: '',
}

export function OrganisationPage() {
  const { isAdmin } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()

  const [form, setForm] = useState<FormState>(EMPTY_FORM)
  const [errors, setErrors] = useState<Partial<Record<keyof FormState, string>>>({})

  const query = useQuery({ queryKey: ['organisation'], queryFn: () => masterApi.organisation() })

  // Seed the form once the record arrives, and again after a refetch.
  useEffect(() => {
    const org = query.data
    if (!org) return
    setForm({
      name: org.name,
      gstin: org.gstin ?? '',
      addressLine: org.addressLine ?? '',
      city: org.city ?? '',
      state: org.state ?? '',
      pincode: org.pincode ?? '',
      phone: org.phone ?? '',
    })
  }, [query.data])

  const save = useMutation({
    mutationFn: () =>
      masterApi.updateOrganisation({
        name: form.name.trim(),
        gstin: form.gstin.trim() || undefined,
        addressLine: form.addressLine.trim() || undefined,
        city: form.city.trim() || undefined,
        state: form.state.trim(),
        pincode: form.pincode.trim() || undefined,
        phone: form.phone.trim() || undefined,
      }),
    onSuccess: () => {
      toast.success('Organisation updated')
      void queryClient.invalidateQueries({ queryKey: ['organisation'] })
    },
    onError: (error) => toast.error(errorMessage(error)),
  })

  const submit = () => {
    const next: Partial<Record<keyof FormState, string>> = {}
    if (!form.name.trim()) next.name = 'A name is required'
    if (!form.state.trim()) next.state = 'A state is required to work out GST'
    setErrors(next)
    if (Object.keys(next).length > 0) return
    save.mutate()
  }

  const disabled = !isAdmin

  return (
    <div>
      <PageHeader
        title="Organisation"
        description="Your own party record. It appears on every document you issue."
        action={
          query.data ? (
            <Badge tone="brand">{titleCase(query.data.type)}</Badge>
          ) : undefined
        }
      />

      <div className="mb-4 flex items-start gap-2.5 rounded-xl border border-amber-300 bg-amber-50 px-4 py-3.5">
        <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-amber-600" />
        <div>
          <p className="text-sm font-semibold text-amber-900">
            Your state decides how GST is split
          </p>
          <p className="mt-0.5 text-xs text-amber-800">
            When a deal is raised, your state is compared against the counterparty's state. Same
            state means the tax is split into CGST and SGST; a different state means a single IGST
            charge. Changing the state here only affects deals raised from then on — documents
            already issued keep the treatment they were computed with, because reissuing them would
            rewrite history.
          </p>
        </div>
      </div>

      {query.isLoading ? (
        <PageLoader label="Loading your organisation…" />
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
          <CardHeader
            title="Details"
            description={
              disabled
                ? undefined
                : 'These values are used on invoices, bills and the GST computation.'
            }
          />
          <CardBody>
            {disabled && (
              <div className="mb-4 flex items-start gap-2 rounded-lg border border-lilac-200 bg-lilac-50 px-3 py-2.5">
                <Lock className="mt-0.5 h-4 w-4 shrink-0 text-muted-ink" />
                <p className="text-xs text-muted-ink">
                  Only an administrator of this organisation can change these details. You can read
                  them here.
                </p>
              </div>
            )}

            <fieldset disabled={disabled} className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <FormRow label="Name" required error={errors.name} className="sm:col-span-2">
                <Input
                  value={form.name}
                  onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
                />
              </FormRow>
              <FormRow label="GSTIN">
                <Input
                  value={form.gstin}
                  placeholder="27AAAAA0000A1Z5"
                  onChange={(e) => setForm((f) => ({ ...f, gstin: e.target.value }))}
                />
              </FormRow>
              <FormRow label="Phone">
                <Input
                  value={form.phone}
                  onChange={(e) => setForm((f) => ({ ...f, phone: e.target.value }))}
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
              <FormRow label="State" required error={errors.state}>
                <Input
                  value={form.state}
                  placeholder="Maharashtra"
                  onChange={(e) => setForm((f) => ({ ...f, state: e.target.value }))}
                />
              </FormRow>
              <FormRow label="Pincode">
                <Input
                  value={form.pincode}
                  onChange={(e) => setForm((f) => ({ ...f, pincode: e.target.value }))}
                />
              </FormRow>
            </fieldset>

            {!disabled && (
              <div className="mt-5 flex justify-end">
                <Button loading={save.isPending} onClick={submit}>
                  Save changes
                </Button>
              </div>
            )}
          </CardBody>
        </Card>
      )}
    </div>
  )
}
