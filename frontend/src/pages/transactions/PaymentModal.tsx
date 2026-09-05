import { useEffect, useState } from 'react'
import type { DocumentResponse, PaymentMethod, PaymentRequest } from '@/api/types'
import { Button } from '@/components/ui/Button'
import { FormRow, Input, Select } from '@/components/ui/Field'
import { Modal } from '@/components/ui/Modal'
import { formatMoney, today } from '@/lib/utils'

export function PaymentModal({
  open,
  document: doc,
  onClose,
  onSubmit,
  submitting,
}: {
  open: boolean
  document: DocumentResponse | null
  onClose: () => void
  onSubmit: (body: PaymentRequest) => void
  submitting: boolean
}) {
  const [method, setMethod] = useState<PaymentMethod>('BANK')
  const [paymentDate, setPaymentDate] = useState(today())
  const [amount, setAmount] = useState('')
  const [reference, setReference] = useState('')
  const [error, setError] = useState<string | null>(null)

  // Default to settling the document in full.
  useEffect(() => {
    if (!open || !doc) return
    setMethod('BANK')
    setPaymentDate(today())
    setAmount(doc.amountDue)
    setReference('')
    setError(null)
  }, [open, doc])

  if (!doc) return null

  const due = Number(doc.amountDue)

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    const value = Number(amount)
    if (!value || value <= 0) {
      setError('Enter an amount greater than zero')
      return
    }
    // The server enforces this too; catching it here saves a round trip.
    if (value > due) {
      setError(`Amount cannot exceed the outstanding balance of ${formatMoney(due)}`)
      return
    }
    setError(null)
    onSubmit({ method, paymentDate, amount, reference: reference.trim() || undefined })
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      size="sm"
      title="Register payment"
      description={`${doc.documentNo} · ${doc.contactName}`}
      footer={
        <>
          <Button variant="outline" type="button" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" form="payment-form" loading={submitting}>
            Record payment
          </Button>
        </>
      }
    >
      <form id="payment-form" onSubmit={handleSubmit} className="space-y-4">
        <div className="rounded-lg border border-lilac-200 bg-lilac-50 p-3 text-sm">
          <div className="flex justify-between text-muted-ink">
            <span>Document total</span>
            <span className="tabular">{formatMoney(doc.totalAmount)}</span>
          </div>
          <div className="flex justify-between text-muted-ink">
            <span>Already paid</span>
            <span className="tabular">{formatMoney(doc.amountPaid)}</span>
          </div>
          <div className="mt-1 flex justify-between border-t border-lilac-200 pt-1 font-semibold text-plum-800">
            <span>Outstanding</span>
            <span className="tabular">{formatMoney(doc.amountDue)}</span>
          </div>
        </div>

        <FormRow label="Method" required>
          <Select value={method} onChange={(e) => setMethod(e.target.value as PaymentMethod)}>
            <option value="BANK">Bank</option>
            <option value="CASH">Cash</option>
          </Select>
        </FormRow>

        <FormRow label="Payment date" required>
          <Input
            type="date"
            value={paymentDate}
            onChange={(e) => setPaymentDate(e.target.value)}
            required
          />
        </FormRow>

        <FormRow label="Amount" required>
          <Input
            type="number"
            min="0.01"
            step="0.01"
            max={due}
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            required
          />
        </FormRow>

        <FormRow label="Reference">
          <Input
            value={reference}
            placeholder="Cheque no., UTR, receipt…"
            onChange={(e) => setReference(e.target.value)}
          />
        </FormRow>

        {error && (
          <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
            {error}
          </div>
        )}
      </form>
    </Modal>
  )
}
