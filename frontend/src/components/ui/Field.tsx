import type { InputHTMLAttributes, SelectHTMLAttributes, TextareaHTMLAttributes, ReactNode } from 'react'
import { cn } from '@/lib/utils'

const control =
  'w-full rounded-lg border border-lilac-300 bg-white px-3 py-2 text-sm text-plum-800 ' +
  'placeholder:text-muted-ink/60 transition-colors ' +
  'focus:border-amethyst-600 focus:outline-none ' +
  'disabled:cursor-not-allowed disabled:bg-lilac-50 disabled:text-muted-ink'

export function Label({ children, required }: { children: ReactNode; required?: boolean }) {
  return (
    <label className="mb-1.5 block text-xs font-medium tracking-wide text-muted-ink uppercase">
      {children}
      {required && <span className="ml-0.5 text-red-500">*</span>}
    </label>
  )
}

export function FieldError({ children }: { children?: ReactNode }) {
  if (!children) return null
  return <p className="mt-1 text-xs text-red-600">{children}</p>
}

export function Input({ className, ...props }: InputHTMLAttributes<HTMLInputElement>) {
  return <input className={cn(control, className)} {...props} />
}

export function Select({
  className,
  children,
  ...props
}: SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <select className={cn(control, 'cursor-pointer', className)} {...props}>
      {children}
    </select>
  )
}

export function Textarea({ className, ...props }: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return <textarea className={cn(control, 'min-h-20 resize-y', className)} {...props} />
}

export function FormRow({
  label,
  required,
  error,
  children,
  className,
}: {
  label: ReactNode
  required?: boolean
  error?: ReactNode
  children: ReactNode
  className?: string
}) {
  return (
    <div className={className}>
      <Label required={required}>{label}</Label>
      {children}
      <FieldError>{error}</FieldError>
    </div>
  )
}
