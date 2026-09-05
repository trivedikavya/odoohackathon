import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowRight, CheckCircle2, Eye, FileUp, Plus, Wallet, X } from 'lucide-react'
import { errorMessage } from '@/api/client'
import type { DocumentResponse, OrderRequest, OrderResponse, PaymentRequest } from '@/api/types'
import { PageHeader } from '@/components/layout/AppLayout'
import { StatusBadge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { Input } from '@/components/ui/Field'
import { ErrorState, PageLoader } from '@/components/ui/PageLoader'
import { EmptyRow, Table, TD, TH, THead, TR } from '@/components/ui/Table'
import { useToast } from '@/components/ui/Toast'
import { cn, formatDate, formatMoney, today } from '@/lib/utils'
import { DocumentDetailModal } from './DocumentDetailModal'
import { OrderFormModal } from './OrderFormModal'
import { PaymentModal } from './PaymentModal'
import type { WorkflowConfig } from './workflowConfig'

type Tab = 'orders' | 'documents'

export function TransactionWorkflowPage({ config }: { config: WorkflowConfig }) {
  const qc = useQueryClient()
  const toast = useToast()

  const [tab, setTab] = useState<Tab>('orders')
  const [search, setSearch] = useState('')
  const [formOpen, setFormOpen] = useState(false)
  const [editingOrder, setEditingOrder] = useState<OrderResponse | null>(null)
  const [detailDoc, setDetailDoc] = useState<DocumentResponse | null>(null)
  const [payingDoc, setPayingDoc] = useState<DocumentResponse | null>(null)

  const ordersQuery = useQuery({
    queryKey: [config.kind, 'orders', search],
    queryFn: () => config.searchOrders({ search: search || undefined, size: 50 }),
  })

  const documentsQuery = useQuery({
    queryKey: [config.kind, 'documents', search],
    queryFn: () => config.searchDocuments({ search: search || undefined, size: 50 }),
  })

  // Anything that touches the ledger invalidates the reports and dashboard too.
  const invalidateAll = () => {
    qc.invalidateQueries({ queryKey: [config.kind] })
    qc.invalidateQueries({ queryKey: ['ledger'] })
    qc.invalidateQueries({ queryKey: ['dashboard'] })
    qc.invalidateQueries({ queryKey: ['balance-sheet'] })
    qc.invalidateQueries({ queryKey: ['profit-and-loss'] })
    qc.invalidateQueries({ queryKey: ['payments'] })
  }

  const saveOrder = useMutation({
    mutationFn: (body: OrderRequest) =>
      editingOrder ? config.updateOrder(editingOrder.id, body) : config.createOrder(body),
    onSuccess: (order) => {
      invalidateAll()
      setFormOpen(false)
      setEditingOrder(null)
      toast.success(`${order.orderNo} saved`)
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  const confirmOrder = useMutation({
    mutationFn: (id: number) => config.confirmOrder(id),
    onSuccess: (o) => {
      invalidateAll()
      toast.success(`${o.orderNo} confirmed`)
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  const cancelOrder = useMutation({
    mutationFn: (id: number) => config.cancelOrder(id),
    onSuccess: (o) => {
      invalidateAll()
      toast.success(`${o.orderNo} cancelled`)
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  const convertOrder = useMutation({
    mutationFn: (id: number) => config.convertOrder(id, { documentDate: today() }),
    onSuccess: (doc) => {
      invalidateAll()
      setTab('documents')
      toast.success(`${doc.documentNo} created as a draft`)
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  const postDocument = useMutation({
    mutationFn: (id: number) => config.postDocument(id),
    onSuccess: (doc) => {
      invalidateAll()
      toast.success(`${doc.documentNo} posted · journal entry ${doc.journalEntryNo}`)
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  const cancelDocument = useMutation({
    mutationFn: (id: number) => config.cancelDocument(id),
    onSuccess: (doc) => {
      invalidateAll()
      toast.success(`${doc.documentNo} cancelled`)
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  const payDocument = useMutation({
    mutationFn: ({ id, body }: { id: number; body: PaymentRequest }) => config.payDocument(id, body),
    onSuccess: (payment) => {
      invalidateAll()
      setPayingDoc(null)
      toast.success(`Payment ${payment.paymentNo} recorded · journal entry ${payment.journalEntryNo}`)
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  const orders = ordersQuery.data?.content ?? []
  const documents = documentsQuery.data?.content ?? []

  return (
    <>
      <PageHeader
        title={config.title}
        description={config.description}
        action={
          <Button
            onClick={() => {
              setEditingOrder(null)
              setFormOpen(true)
            }}
          >
            <Plus className="h-4 w-4" />
            New {config.orderLabel}
          </Button>
        }
      />

      <div className="mb-4 flex flex-wrap items-center gap-3">
        <div className="inline-flex rounded-lg border border-lilac-200 bg-white p-0.5">
          {(
            [
              ['orders', config.orderLabelPlural, ordersQuery.data?.totalElements],
              ['documents', config.documentLabelPlural, documentsQuery.data?.totalElements],
            ] as const
          ).map(([key, label, count]) => (
            <button
              key={key}
              onClick={() => setTab(key)}
              className={cn(
                'rounded-md px-3 py-1.5 text-sm font-medium transition-colors',
                tab === key
                  ? 'bg-amethyst-600 text-white'
                  : 'text-muted-ink hover:bg-lilac-100 hover:text-plum-800',
              )}
            >
              {label}
              {count !== undefined && (
                <span className={cn('ml-1.5 text-xs', tab === key ? 'text-lilac-200' : 'text-muted-ink/70')}>
                  {count}
                </span>
              )}
            </button>
          ))}
        </div>

        <Input
          placeholder="Search…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="w-full sm:max-w-xs"
        />
      </div>

      <Card>
        {tab === 'orders' ? (
          ordersQuery.isLoading ? (
            <PageLoader />
          ) : ordersQuery.isError ? (
            <ErrorState message={errorMessage(ordersQuery.error)} />
          ) : (
            <Table>
              <THead>
                <tr>
                  <TH>Number</TH>
                  <TH>{config.partyLabel}</TH>
                  <TH>Date</TH>
                  <TH>Status</TH>
                  <TH className="text-right">Total</TH>
                  <TH className="text-right">Actions</TH>
                </tr>
              </THead>
              <tbody>
                {orders.length === 0 ? (
                  <EmptyRow colSpan={6}>
                    No {config.orderLabelPlural.toLowerCase()} yet.
                  </EmptyRow>
                ) : (
                  orders.map((o) => (
                    <TR key={o.id}>
                      <TD className="font-medium">{o.orderNo}</TD>
                      <TD>{o.contactName}</TD>
                      <TD className="text-muted-ink">{formatDate(o.orderDate)}</TD>
                      <TD>
                        <StatusBadge status={o.status} />
                        {o.generatedDocumentNo && (
                          <span className="ml-2 text-xs text-muted-ink">
                            → {o.generatedDocumentNo}
                          </span>
                        )}
                      </TD>
                      <TD className="tabular text-right font-medium">
                        {formatMoney(o.totalAmount)}
                      </TD>
                      <TD>
                        <div className="flex justify-end gap-1">
                          {o.status === 'DRAFT' && (
                            <>
                              <Button
                                size="sm"
                                variant="outline"
                                onClick={() => {
                                  setEditingOrder(o)
                                  setFormOpen(true)
                                }}
                              >
                                Edit
                              </Button>
                              <Button
                                size="sm"
                                onClick={() => confirmOrder.mutate(o.id)}
                                loading={confirmOrder.isPending && confirmOrder.variables === o.id}
                              >
                                <CheckCircle2 className="h-3.5 w-3.5" />
                                Confirm
                              </Button>
                            </>
                          )}
                          {o.status === 'CONFIRMED' && (
                            <>
                              <Button
                                size="sm"
                                onClick={() => convertOrder.mutate(o.id)}
                                loading={convertOrder.isPending && convertOrder.variables === o.id}
                              >
                                <ArrowRight className="h-3.5 w-3.5" />
                                {config.convertActionLabel}
                              </Button>
                              <Button
                                size="sm"
                                variant="ghost"
                                onClick={() => cancelOrder.mutate(o.id)}
                                aria-label="Cancel order"
                              >
                                <X className="h-3.5 w-3.5" />
                              </Button>
                            </>
                          )}
                        </div>
                      </TD>
                    </TR>
                  ))
                )}
              </tbody>
            </Table>
          )
        ) : documentsQuery.isLoading ? (
          <PageLoader />
        ) : documentsQuery.isError ? (
          <ErrorState message={errorMessage(documentsQuery.error)} />
        ) : (
          <Table>
            <THead>
              <tr>
                <TH>Number</TH>
                <TH>{config.partyLabel}</TH>
                <TH>Date</TH>
                <TH>Status</TH>
                <TH>Journal entry</TH>
                <TH className="text-right">Total</TH>
                <TH className="text-right">Due</TH>
                <TH className="text-right">Actions</TH>
              </tr>
            </THead>
            <tbody>
              {documents.length === 0 ? (
                <EmptyRow colSpan={8}>
                  No {config.documentLabelPlural.toLowerCase()} yet. Confirm an order and convert
                  it.
                </EmptyRow>
              ) : (
                documents.map((d) => (
                  <TR key={d.id}>
                    <TD className="font-medium">{d.documentNo}</TD>
                    <TD>{d.contactName}</TD>
                    <TD className="text-muted-ink">{formatDate(d.documentDate)}</TD>
                    <TD>
                      <StatusBadge status={d.status} />
                    </TD>
                    <TD className="tabular text-xs text-muted-ink">{d.journalEntryNo ?? '—'}</TD>
                    <TD className="tabular text-right font-medium">{formatMoney(d.totalAmount)}</TD>
                    <TD className="tabular text-right">{formatMoney(d.amountDue)}</TD>
                    <TD>
                      <div className="flex justify-end gap-1">
                        <Button
                          size="sm"
                          variant="ghost"
                          aria-label="View details"
                          onClick={() => setDetailDoc(d)}
                        >
                          <Eye className="h-3.5 w-3.5" />
                        </Button>
                        {d.status === 'DRAFT' && (
                          <>
                            <Button
                              size="sm"
                              onClick={() => postDocument.mutate(d.id)}
                              loading={postDocument.isPending && postDocument.variables === d.id}
                            >
                              <FileUp className="h-3.5 w-3.5" />
                              Post
                            </Button>
                            <Button
                              size="sm"
                              variant="ghost"
                              aria-label="Cancel"
                              onClick={() => cancelDocument.mutate(d.id)}
                            >
                              <X className="h-3.5 w-3.5" />
                            </Button>
                          </>
                        )}
                        {(d.status === 'POSTED' || d.status === 'PARTIALLY_PAID') && (
                          <Button size="sm" onClick={() => setPayingDoc(d)}>
                            <Wallet className="h-3.5 w-3.5" />
                            Pay
                          </Button>
                        )}
                      </div>
                    </TD>
                  </TR>
                ))
              )}
            </tbody>
          </Table>
        )}
      </Card>

      <OrderFormModal
        config={config}
        open={formOpen}
        order={editingOrder}
        submitting={saveOrder.isPending}
        onClose={() => {
          setFormOpen(false)
          setEditingOrder(null)
        }}
        onSubmit={(body) => saveOrder.mutate(body)}
      />

      <DocumentDetailModal
        open={!!detailDoc}
        document={detailDoc}
        onClose={() => setDetailDoc(null)}
      />

      <PaymentModal
        open={!!payingDoc}
        document={payingDoc}
        submitting={payDocument.isPending}
        onClose={() => setPayingDoc(null)}
        onSubmit={(body) => payingDoc && payDocument.mutate({ id: payingDoc.id, body })}
      />
    </>
  )
}
