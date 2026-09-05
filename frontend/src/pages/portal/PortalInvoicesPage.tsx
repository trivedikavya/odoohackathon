import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { errorMessage } from '@/api/client'
import { portalApi } from '@/api/endpoints'
import type { PaymentMethod, TradeDocument } from '@/api/types'
import { PageHeader } from '@/components/layout/AppLayout'
import { Badge, StatusBadge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { FormRow, Input, Select } from '@/components/ui/Field'
import { Modal } from '@/components/ui/Modal'
import { ErrorState, PageLoader } from '@/components/ui/PageLoader'
import { EmptyRow, Table, TD, TH, THead, TR } from '@/components/ui/Table'
import { useToast } from '@/components/ui/Toast'
import { formatDate, formatMoney, formatNumber, today } from '@/lib/utils'

export function PortalInvoicesPage() {
  const qc = useQueryClient()
  const toast = useToast()

  const [viewing, setViewing] = useState<TradeDocument | null>(null)
  const [paying, setPaying] = useState<TradeDocument | null>(null)
  const [method, setMethod] = useState<PaymentMethod>('BANK')
  const [payDate, setPayDate] = useState(today())
  const [amount, setAmount] = useState('')
  const [reference, setReference] = useState('')

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['portal-documents'],
    queryFn: () => portalApi.myDocuments({ size: 100 }),
  })

  const openPayment = (doc: TradeDocument) => {
    setPaying(doc)
    setMethod('BANK')
    setPayDate(today())
    // Default to settling in full, which is what most people are here for.
    setAmount(doc.amountDue)
    setReference('')
  }

  const pay = useMutation({
    mutationFn: () =>
      portalApi.pay(paying!.id, {
        method,
        settlementDate: payDate,
        amount,
        reference: reference || undefined,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['portal-documents'] })
      qc.invalidateQueries({ queryKey: ['portal-summary'] })
      qc.invalidateQueries({ queryKey: ['portal-payments'] })
      setPaying(null)
      toast.success('Payment recorded')
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  const docs = data?.content ?? []

  return (
    <>
      <PageHeader
        title="My Invoices"
        description="Invoices issued to you. Only documents that have actually been issued appear here."
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
                <TH>Invoice</TH>
                <TH>From</TH>
                <TH>Date</TH>
                <TH>Due</TH>
                <TH>Status</TH>
                <TH className="text-right">Total</TH>
                <TH className="text-right">Paid</TH>
                <TH className="text-right">Due</TH>
                <TH className="text-right">Actions</TH>
              </tr>
            </THead>
            <tbody>
              {docs.length === 0 ? (
                <EmptyRow colSpan={9}>You have no invoices yet.</EmptyRow>
              ) : (
                docs.map((doc) => {
                  const outstanding = Number(doc.amountDue) > 0
                  const overdue =
                    outstanding && !!doc.dueDate && new Date(doc.dueDate) < new Date()
                  return (
                    <TR key={doc.id}>
                      <TD className="font-medium">{doc.docNo}</TD>
                      <TD>{doc.counterpartyName ?? '—'}</TD>
                      <TD>{formatDate(doc.docDate)}</TD>
                      <TD className={overdue ? 'text-red-600' : undefined}>
                        {formatDate(doc.dueDate)}
                      </TD>
                      <TD>
                        <StatusBadge status={doc.status} />
                      </TD>
                      <TD className="tabular text-right">{formatMoney(doc.totalAmount)}</TD>
                      <TD className="tabular text-right">{formatMoney(doc.amountSettled)}</TD>
                      <TD className="tabular text-right font-medium">
                        {formatMoney(doc.amountDue)}
                      </TD>
                      <TD className="text-right">
                        <div className="flex justify-end gap-2">
                          <Button size="sm" variant="ghost" onClick={() => setViewing(doc)}>
                            View
                          </Button>
                          {outstanding && (
                            <Button size="sm" onClick={() => openPayment(doc)}>
                              Pay
                            </Button>
                          )}
                        </div>
                      </TD>
                    </TR>
                  )
                })
              )}
            </tbody>
          </Table>
        )}
      </Card>

      <Modal
        open={!!viewing}
        onClose={() => setViewing(null)}
        size="lg"
        title={viewing ? `Invoice ${viewing.docNo}` : ''}
        description={viewing?.placeOfSupply ? `Place of supply: ${viewing.placeOfSupply}` : undefined}
      >
        {viewing && (
          <div className="space-y-5">
            <div className="flex flex-wrap gap-3">
              <StatusBadge status={viewing.status} />
              {viewing.taxTreatment !== 'UNSPECIFIED' && (
                <Badge tone={viewing.taxTreatment === 'INTRA_STATE' ? 'brand' : 'info'}>
                  {viewing.taxTreatment === 'INTRA_STATE'
                    ? 'Intra-state · CGST + SGST'
                    : 'Inter-state · IGST'}
                </Badge>
              )}
            </div>

            <Table>
              <THead>
                <tr>
                  <TH>Item</TH>
                  <TH className="text-right">Qty</TH>
                  <TH className="text-right">Rate</TH>
                  <TH className="text-right">Tax %</TH>
                  <TH className="text-right">Amount</TH>
                </tr>
              </THead>
              <tbody>
                {viewing.lines.map((line) => (
                  <TR key={line.id}>
                    <TD className="font-medium">{line.description}</TD>
                    <TD className="tabular text-right">{formatNumber(line.quantity, 3)}</TD>
                    <TD className="tabular text-right">{formatMoney(line.unitPrice)}</TD>
                    <TD className="tabular text-right">{formatNumber(line.taxRate, 2)}%</TD>
                    <TD className="tabular text-right">{formatMoney(line.lineTotal)}</TD>
                  </TR>
                ))}
              </tbody>
            </Table>

            <div className="ml-auto w-full max-w-xs space-y-1.5 text-sm">
              <Row label="Subtotal" value={viewing.untaxedAmount} />
              {Number(viewing.cgstAmount) > 0 && <Row label="CGST" value={viewing.cgstAmount} />}
              {Number(viewing.sgstAmount) > 0 && <Row label="SGST" value={viewing.sgstAmount} />}
              {Number(viewing.igstAmount) > 0 && <Row label="IGST" value={viewing.igstAmount} />}
              <div className="flex justify-between border-t border-lilac-200 pt-1.5 font-semibold text-plum-800">
                <span>Total</span>
                <span className="tabular">{formatMoney(viewing.totalAmount)}</span>
              </div>
              <Row label="Paid" value={viewing.amountSettled} />
              <div className="flex justify-between font-medium text-amethyst-700">
                <span>Amount due</span>
                <span className="tabular">{formatMoney(viewing.amountDue)}</span>
              </div>
            </div>
          </div>
        )}
      </Modal>

      <Modal
        open={!!paying}
        onClose={() => setPaying(null)}
        title={`Pay ${paying?.docNo ?? ''}`}
        description={`Outstanding: ${formatMoney(paying?.amountDue)}`}
        footer={
          <>
            <Button variant="outline" onClick={() => setPaying(null)}>
              Cancel
            </Button>
            <Button onClick={() => pay.mutate()} loading={pay.isPending}>
              Record payment
            </Button>
          </>
        }
      >
        <div className="space-y-4">
          <FormRow label="Method" required>
            <Select value={method} onChange={(e) => setMethod(e.target.value as PaymentMethod)}>
              <option value="BANK">Bank</option>
              <option value="CASH">Cash</option>
            </Select>
          </FormRow>
          <FormRow label="Payment date" required>
            <Input type="date" value={payDate} onChange={(e) => setPayDate(e.target.value)} />
          </FormRow>
          <FormRow label="Amount" required>
            <Input
              type="number"
              step="0.01"
              min="0.01"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
            />
          </FormRow>
          <FormRow label="Reference">
            <Input
              value={reference}
              onChange={(e) => setReference(e.target.value)}
              placeholder="Cheque or transaction number"
            />
          </FormRow>
          <p className="text-xs text-muted-ink">
            The server re-checks the outstanding amount before accepting this, so an overpayment is
            rejected even if this form is bypassed.
          </p>
        </div>
      </Modal>
    </>
  )
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between text-muted-ink">
      <span>{label}</span>
      <span className="tabular text-plum-800">{formatMoney(value)}</span>
    </div>
  )
}
