import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Check, Info, Loader2, Store, Truck, UserRound, X } from 'lucide-react'
import { errorMessage } from '@/api/client'
import { authApi } from '@/api/endpoints'
import type { PartyType, RegisterRequest } from '@/api/types'
import { useAuth } from '@/auth/AuthContext'
import { landingFor } from '@/auth/landing'
import { Button } from '@/components/ui/Button'
import { FormRow, Input, Label, Select } from '@/components/ui/Field'
import { cn } from '@/lib/utils'

/** Indian states and union territories, for the place-of-supply select. */
const INDIAN_STATES = [
  'Andaman and Nicobar Islands',
  'Andhra Pradesh',
  'Arunachal Pradesh',
  'Assam',
  'Bihar',
  'Chandigarh',
  'Chhattisgarh',
  'Dadra and Nagar Haveli and Daman and Diu',
  'Delhi',
  'Goa',
  'Gujarat',
  'Haryana',
  'Himachal Pradesh',
  'Jammu and Kashmir',
  'Jharkhand',
  'Karnataka',
  'Kerala',
  'Ladakh',
  'Lakshadweep',
  'Madhya Pradesh',
  'Maharashtra',
  'Manipur',
  'Meghalaya',
  'Mizoram',
  'Nagaland',
  'Odisha',
  'Puducherry',
  'Punjab',
  'Rajasthan',
  'Sikkim',
  'Tamil Nadu',
  'Telangana',
  'Tripura',
  'Uttar Pradesh',
  'Uttarakhand',
  'West Bengal',
]

const ROLES: { value: PartyType; label: string; icon: typeof Store; blurb: string }[] = [
  {
    value: 'SELLER',
    label: 'Seller',
    icon: Store,
    blurb: 'You sell to customers and buy from vendors. You get a full set of books.',
  },
  {
    value: 'VENDOR',
    label: 'Vendor',
    icon: Truck,
    blurb: 'You supply sellers and can sell direct to customers. You get a full set of books.',
  },
  {
    value: 'CUSTOMER',
    label: 'Customer',
    icon: UserRound,
    blurb: 'You buy from sellers and vendors. You view and pay invoices; no books to keep.',
  },
]

const LOGIN_ID_PATTERN = /^[A-Za-z0-9._-]{6,12}$/
const GSTIN_PATTERN = /^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$/

const passwordRules: { label: string; test: (v: string) => boolean }[] = [
  { label: 'At least 8 characters', test: (v) => v.length >= 8 },
  { label: 'A lowercase letter', test: (v) => /[a-z]/.test(v) },
  { label: 'An uppercase letter', test: (v) => /[A-Z]/.test(v) },
  { label: 'A special character', test: (v) => /[^A-Za-z0-9]/.test(v) },
]

type LoginIdState =
  | { kind: 'idle' }
  | { kind: 'invalid'; message: string }
  | { kind: 'checking' }
  | { kind: 'available'; message: string }
  | { kind: 'taken'; message: string }

/** An availability answer, tagged with the value it was an answer for. */
interface LoginIdCheck {
  loginId: string
  kind: 'available' | 'taken' | 'invalid'
  message: string
}

const LOGIN_ID_HINT = '6–12 characters, using letters, digits, dot, underscore or hyphen only.'

interface FormState {
  fullName: string
  organisationName: string
  loginId: string
  email: string
  password: string
  confirmPassword: string
  state: string
  city: string
  gstin: string
  phone: string
}

const EMPTY_FORM: FormState = {
  fullName: '',
  organisationName: '',
  loginId: '',
  email: '',
  password: '',
  confirmPassword: '',
  state: '',
  city: '',
  gstin: '',
  phone: '',
}

export function RegisterPage() {
  const { register } = useAuth()
  const navigate = useNavigate()

  const [partyType, setPartyType] = useState<PartyType>('SELLER')
  const [form, setForm] = useState<FormState>(EMPTY_FORM)
  const [touched, setTouched] = useState<Partial<Record<keyof FormState, boolean>>>({})
  const [loginIdCheck, setLoginIdCheck] = useState<LoginIdCheck | null>(null)
  const [serverError, setServerError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const isCustomer = partyType === 'CUSTOMER'

  const set = <K extends keyof FormState>(key: K, value: FormState[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }))

  const markTouched = (key: keyof FormState) => setTouched((prev) => ({ ...prev, [key]: true }))

  const trimmedLoginId = form.loginId.trim()
  const loginIdShapeValid = LOGIN_ID_PATTERN.test(trimmedLoginId)

  /**
   * Login ID availability. The shape is checked locally first so we never
   * spend a round trip on input the server would reject anyway. State is
   * only written from the async callback — the "checking" and "invalid"
   * states are derived during render from the value itself, so typing does
   * not cascade an extra render.
   */
  useEffect(() => {
    if (!loginIdShapeValid) return

    let cancelled = false
    const timer = window.setTimeout(() => {
      authApi
        .checkLoginId(trimmedLoginId)
        .then((result) => {
          if (cancelled) return
          setLoginIdCheck({
            loginId: trimmedLoginId,
            kind: result.available ? 'available' : 'taken',
            message: result.message || (result.available ? 'Available' : 'Already taken'),
          })
        })
        .catch((err: unknown) => {
          if (cancelled) return
          setLoginIdCheck({
            loginId: trimmedLoginId,
            kind: 'invalid',
            message: errorMessage(err, 'Could not check that ID'),
          })
        })
    }, 400)

    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
  }, [trimmedLoginId, loginIdShapeValid])

  const loginIdState = useMemo<LoginIdState>(() => {
    if (!trimmedLoginId) return { kind: 'idle' }
    if (!loginIdShapeValid) return { kind: 'invalid', message: LOGIN_ID_HINT }
    // A stale answer belongs to a value the user has already typed past.
    if (loginIdCheck?.loginId !== trimmedLoginId) return { kind: 'checking' }
    return { kind: loginIdCheck.kind, message: loginIdCheck.message }
  }, [trimmedLoginId, loginIdShapeValid, loginIdCheck])

  const passwordChecks = useMemo(
    () => passwordRules.map((rule) => ({ label: rule.label, ok: rule.test(form.password) })),
    [form.password],
  )
  const passwordStrong = passwordChecks.every((c) => c.ok)
  const confirmMatches = form.confirmPassword.length > 0 && form.confirmPassword === form.password

  const errors = useMemo(() => {
    const next: Partial<Record<keyof FormState, string>> = {}
    if (!form.fullName.trim()) next.fullName = 'Tell us your name.'
    if (!isCustomer && !form.organisationName.trim())
      next.organisationName = 'Your books are kept under this name.'
    if (!loginIdShapeValid) next.loginId = LOGIN_ID_HINT
    else if (loginIdState.kind === 'taken') next.loginId = loginIdState.message
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim()))
      next.email = 'Enter a valid email address.'
    if (!passwordStrong) next.password = 'Your password does not meet every rule yet.'
    if (!confirmMatches) next.confirmPassword = 'Passwords do not match.'
    if (!form.state) next.state = 'Pick your state — it decides the GST split.'
    if (form.gstin.trim() && !GSTIN_PATTERN.test(form.gstin.trim().toUpperCase()))
      next.gstin = 'That does not look like a 15-character GSTIN.'
    if (form.phone.trim() && !/^[0-9+\-\s()]{6,20}$/.test(form.phone.trim()))
      next.phone = 'Enter a valid phone number.'
    return next
  }, [form, isCustomer, loginIdShapeValid, loginIdState, passwordStrong, confirmMatches])

  const errorFor = (key: keyof FormState) => (touched[key] ? errors[key] : undefined)

  const onSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setServerError(null)

    if (Object.keys(errors).length > 0) {
      // Reveal every message at once rather than making them hunt field by field.
      setTouched({
        fullName: true,
        organisationName: true,
        loginId: true,
        email: true,
        password: true,
        confirmPassword: true,
        state: true,
        city: true,
        gstin: true,
        phone: true,
      })
      return
    }

    const body: RegisterRequest = {
      loginId: form.loginId.trim(),
      email: form.email.trim(),
      password: form.password,
      fullName: form.fullName.trim(),
      partyType,
      // A customer keeps no books, so there is no business to name — the
      // party is simply the person.
      organisationName: isCustomer ? form.fullName.trim() : form.organisationName.trim(),
      state: form.state,
      city: form.city.trim() || undefined,
      gstin: form.gstin.trim() ? form.gstin.trim().toUpperCase() : undefined,
      phone: form.phone.trim() || undefined,
    }

    setSubmitting(true)
    try {
      const profile = await register(body)
      navigate(landingFor(profile.accessLevel), { replace: true })
    } catch (err) {
      setServerError(errorMessage(err, 'Could not create your account'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="min-h-screen bg-lilac-50 px-4 py-10 sm:px-6 lg:py-14">
      <div className="mx-auto w-full max-w-3xl">
        <div className="mb-8 flex items-center gap-2.5">
          <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-amethyst-600 text-sm font-bold text-white">
            UF
          </div>
          <div>
            <p className="text-sm font-semibold text-plum-800">Urban Furniture</p>
            <p className="text-xs text-muted-ink">Multi-party accounting</p>
          </div>
        </div>

        <h1 className="text-2xl font-semibold text-plum-800">Create your account</h1>
        <p className="mt-1.5 text-sm text-muted-ink">
          Start by telling us what you do — it decides what you get.
        </p>

        {serverError && (
          <div
            role="alert"
            className="mt-6 rounded-lg border border-red-200 bg-red-50 px-3.5 py-3 text-sm text-red-700"
          >
            {serverError}
          </div>
        )}

        <form onSubmit={onSubmit} className="mt-7 space-y-7" noValidate>
          {/* Role picker */}
          <fieldset>
            <legend className="mb-2.5 block text-xs font-medium tracking-wide text-muted-ink uppercase">
              I am a
            </legend>
            <div className="grid gap-3 sm:grid-cols-3">
              {ROLES.map((role) => {
                const active = partyType === role.value
                return (
                  <button
                    key={role.value}
                    type="button"
                    aria-pressed={active}
                    onClick={() => setPartyType(role.value)}
                    className={cn(
                      'rounded-xl border p-4 text-left transition-colors',
                      active
                        ? 'border-amethyst-600 bg-lilac-100 ring-1 ring-amethyst-600'
                        : 'border-lilac-200 bg-white hover:border-amethyst-600/40 hover:bg-lilac-50',
                    )}
                  >
                    <div className="flex items-center gap-2">
                      <div
                        className={cn(
                          'flex h-8 w-8 items-center justify-center rounded-lg',
                          active ? 'bg-amethyst-600 text-white' : 'bg-lilac-100 text-amethyst-700',
                        )}
                      >
                        <role.icon className="h-4 w-4" />
                      </div>
                      <span className="text-sm font-semibold text-plum-800">{role.label}</span>
                    </div>
                    <p className="mt-2.5 text-xs leading-relaxed text-muted-ink">{role.blurb}</p>
                  </button>
                )
              })}
            </div>
          </fieldset>

          {/* Identity */}
          <div className="rounded-xl border border-lilac-200 bg-white p-5">
            <h2 className="text-sm font-semibold text-plum-800">Who you are</h2>
            <div className="mt-4 grid gap-4 sm:grid-cols-2">
              <FormRow label="Full name" required error={errorFor('fullName')}>
                <Input
                  value={form.fullName}
                  onChange={(e) => set('fullName', e.target.value)}
                  onBlur={() => markTouched('fullName')}
                  autoComplete="name"
                  placeholder="Riya Sharma"
                />
              </FormRow>

              {/* A customer has no separate business identity, so we do not ask. */}
              {!isCustomer && (
                <FormRow label="Business name" required error={errorFor('organisationName')}>
                  <Input
                    value={form.organisationName}
                    onChange={(e) => set('organisationName', e.target.value)}
                    onBlur={() => markTouched('organisationName')}
                    autoComplete="organization"
                    placeholder="Sharma Timber Works"
                  />
                </FormRow>
              )}

              <div>
                <Label required>Login ID</Label>
                <Input
                  value={form.loginId}
                  onChange={(e) => set('loginId', e.target.value)}
                  onBlur={() => markTouched('loginId')}
                  autoComplete="username"
                  placeholder="riya.sharma"
                  aria-invalid={loginIdState.kind === 'taken' || loginIdState.kind === 'invalid'}
                />
                <LoginIdHint state={loginIdState} />
              </div>

              <FormRow label="Email" required error={errorFor('email')}>
                <Input
                  type="email"
                  value={form.email}
                  onChange={(e) => set('email', e.target.value)}
                  onBlur={() => markTouched('email')}
                  autoComplete="email"
                  placeholder="riya@sharmatimber.in"
                />
              </FormRow>
            </div>
          </div>

          {/* Password */}
          <div className="rounded-xl border border-lilac-200 bg-white p-5">
            <h2 className="text-sm font-semibold text-plum-800">Choose a password</h2>
            <div className="mt-4 grid gap-4 sm:grid-cols-2">
              <FormRow label="Password" required>
                <Input
                  type="password"
                  value={form.password}
                  onChange={(e) => set('password', e.target.value)}
                  onBlur={() => markTouched('password')}
                  autoComplete="new-password"
                  placeholder="••••••••"
                />
              </FormRow>

              <FormRow
                label="Confirm password"
                required
                error={
                  touched.confirmPassword && !confirmMatches ? 'Passwords do not match.' : undefined
                }
              >
                <Input
                  type="password"
                  value={form.confirmPassword}
                  onChange={(e) => set('confirmPassword', e.target.value)}
                  onBlur={() => markTouched('confirmPassword')}
                  autoComplete="new-password"
                  placeholder="••••••••"
                />
              </FormRow>
            </div>

            <ul className="mt-4 grid gap-2 sm:grid-cols-2">
              {passwordChecks.map((check) => (
                <li
                  key={check.label}
                  className={cn(
                    'flex items-center gap-2 text-xs',
                    check.ok ? 'text-emerald-700' : 'text-muted-ink',
                  )}
                >
                  <span
                    className={cn(
                      'flex h-4 w-4 shrink-0 items-center justify-center rounded-full',
                      check.ok ? 'bg-emerald-100 text-emerald-700' : 'bg-lilac-100 text-muted-ink',
                    )}
                  >
                    {check.ok ? <Check className="h-3 w-3" /> : <X className="h-3 w-3" />}
                  </span>
                  {check.label}
                </li>
              ))}
            </ul>
          </div>

          {/* Place of supply */}
          <div className="rounded-xl border border-lilac-200 bg-white p-5">
            <h2 className="text-sm font-semibold text-plum-800">Where you are</h2>

            <div className="mt-4 grid gap-4 sm:grid-cols-2">
              <FormRow label="State" required error={errorFor('state')}>
                <Select
                  value={form.state}
                  onChange={(e) => set('state', e.target.value)}
                  onBlur={() => markTouched('state')}
                >
                  <option value="">Select a state…</option>
                  {INDIAN_STATES.map((state) => (
                    <option key={state} value={state}>
                      {state}
                    </option>
                  ))}
                </Select>
              </FormRow>

              <FormRow label="City" error={errorFor('city')}>
                <Input
                  value={form.city}
                  onChange={(e) => set('city', e.target.value)}
                  onBlur={() => markTouched('city')}
                  autoComplete="address-level2"
                  placeholder="Jaipur"
                />
              </FormRow>
            </div>

            <p className="mt-4 flex gap-2 rounded-lg bg-lilac-50 px-3.5 py-3 text-xs leading-relaxed text-muted-ink">
              <Info className="mt-0.5 h-3.5 w-3.5 shrink-0 text-amethyst-600" />
              <span>
                Your state decides the GST treatment on every deal. Trading with a counterparty in{' '}
                <span className="font-medium text-plum-800">the same state</span> splits tax into
                CGST + SGST; a{' '}
                <span className="font-medium text-plum-800">different state</span> is charged as
                IGST.
              </span>
            </p>

            <div className="mt-4 grid gap-4 sm:grid-cols-2">
              <FormRow label="GSTIN" error={errorFor('gstin')}>
                <Input
                  value={form.gstin}
                  onChange={(e) => set('gstin', e.target.value.toUpperCase())}
                  onBlur={() => markTouched('gstin')}
                  placeholder="08AAACS1234C1Z9"
                  maxLength={15}
                  className="uppercase"
                />
              </FormRow>

              <FormRow label="Phone" error={errorFor('phone')}>
                <Input
                  type="tel"
                  value={form.phone}
                  onChange={(e) => set('phone', e.target.value)}
                  onBlur={() => markTouched('phone')}
                  autoComplete="tel"
                  placeholder="+91 98765 43210"
                />
              </FormRow>
            </div>
          </div>

          <div className="flex flex-wrap items-center justify-between gap-4">
            <p className="text-sm text-muted-ink">
              Already have an account?{' '}
              <Link
                to="/login"
                className="font-medium text-amethyst-600 hover:text-amethyst-700 hover:underline"
              >
                Sign in
              </Link>
            </p>
            <Button type="submit" size="lg" loading={submitting}>
              Create account
            </Button>
          </div>
        </form>
      </div>
    </div>
  )
}

function LoginIdHint({ state }: { state: LoginIdState }) {
  if (state.kind === 'idle') {
    return <p className="mt-1 text-xs text-muted-ink">6–12 characters. Letters, digits, . _ -</p>
  }
  if (state.kind === 'checking') {
    return (
      <p className="mt-1 flex items-center gap-1.5 text-xs text-muted-ink">
        <Loader2 className="h-3 w-3 animate-spin text-amethyst-600" />
        Checking availability…
      </p>
    )
  }
  if (state.kind === 'available') {
    return (
      <p className="mt-1 flex items-center gap-1.5 text-xs text-emerald-700">
        <Check className="h-3 w-3" />
        {state.message}
      </p>
    )
  }
  return (
    <p className="mt-1 flex items-center gap-1.5 text-xs text-red-600">
      <X className="h-3 w-3" />
      {state.message}
    </p>
  )
}
