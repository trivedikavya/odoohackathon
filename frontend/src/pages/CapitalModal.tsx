import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { errorMessage } from '@/api/client'
import { capitalApi } from '@/api/endpoints'
import type { PaymentMethod } from '@/api/types'
import { Button } from '@/components/ui/Button'
import { FormRow, Input, Select } from '@/components/ui/Field'
import { Modal } from '@/components/ui/Modal'
import { useToast } from '@/components/ui/Toast'
import { today } from '@/lib/utils'

/**
 * Records the owner funding the business. This is what puts a balance on the
 * Equity side of the balance sheet, alongside retained earnings.
 */
export function CapitalModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const qc = useQueryClient()
  const toast = useToast()

  const [method, setMethod] = useState<PaymentMethod>('BANK')
  const [date, setDate] = useState(today())
  const [amount, setAmount] = useState('')
  const [note, setNote] = useState('')

  const contribute = useMutation({
    mutationFn: () => capitalApi.contribute({ method, date, amount, note: note.trim() || undefined }),
    onSuccess: (r) => {
      qc.invalidateQueries({ queryKey: ['dashboard'] })
      qc.invalidateQueries({ queryKey: ['balance-sheet'] })
      qc.invalidateQueries({ queryKey: ['ledger'] })
      toast.success(`Capital recorded · journal entry ${r.journalEntryNo}`)
      setAmount('')
      setNote('')
      onClose()
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  return (
    <Modal
      open={open}
      onClose={onClose}
      size="sm"
      title="Record capital contribution"
      description="Money the owner puts into the business. Posts Dr Cash/Bank, Cr Owner's Capital."
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" form="capital-form" loading={contribute.isPending}>
            Record capital
          </Button>
        </>
      }
    >
      <form
        id="capital-form"
        onSubmit={(e) => {
          e.preventDefault()
          contribute.mutate()
        }}
        className="space-y-4"
      >
        <FormRow label="Deposited into" required>
          <Select value={method} onChange={(e) => setMethod(e.target.value as PaymentMethod)}>
            <option value="BANK">Bank</option>
            <option value="CASH">Cash</option>
          </Select>
        </FormRow>

        <FormRow label="Date" required>
          <Input type="date" value={date} onChange={(e) => setDate(e.target.value)} required />
        </FormRow>

        <FormRow label="Amount" required>
          <Input
            type="number"
            min="0.01"
            step="0.01"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            required
          />
        </FormRow>

        <FormRow label="Note">
          <Input
            value={note}
            placeholder="Opening capital"
            onChange={(e) => setNote(e.target.value)}
          />
        </FormRow>
      </form>
    </Modal>
  )
}
