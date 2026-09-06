import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { CheckCircle2, Package, XCircle } from 'lucide-react'
import { PageHeader } from '@/components/layout/AppLayout'
import { Card, CardBody } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Field'
import { Badge } from '@/components/ui/Badge'
import { TD, TH, THead, TR, Table } from '@/components/ui/Table'
import { EmptyState, ErrorState, PageLoader } from '@/components/ui/PageLoader'
import { reportsApi } from '@/api/endpoints'
import { errorMessage } from '@/api/client'
import type { ProductType } from '@/api/types'
import { cn, formatDate, formatMoney, formatNumber, titleCase, today } from '@/lib/utils'

const TYPE_TONES: Record<ProductType, 'neutral' | 'info' | 'brand'> = {
  GOODS: 'neutral',
  SERVICE: 'info',
  COMBO: 'brand',
}

export function StockLedgerPage() {
  const [asOf, setAsOf] = useState(today())

  const query = useQuery({
    queryKey: ['stock-ledger', asOf],
    queryFn: () => reportsApi.stockLedger(asOf),
  })

  return (
    <div>
      <PageHeader
        title="Stock ledger"
        description="What is physically on hand, valued at weighted-average cost, cross-checked against the Inventory account."
        action={
          <div>
            <label className="mb-1 block text-[10px] font-medium text-muted-ink uppercase">
              As of
            </label>
            <Input
              type="date"
              className="w-44"
              value={asOf}
              onChange={(e) => setAsOf(e.target.value)}
            />
          </div>
        }
      />

      {query.isLoading ? (
        <PageLoader label="Counting and valuing stock…" />
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
      ) : query.data ? (
        <div className="space-y-4">
          <div>
            <div
              className={cn(
                'flex items-start gap-2.5 rounded-xl border px-4 py-3 text-sm',
                query.data.reconciled
                  ? 'border-emerald-200 bg-emerald-50 text-emerald-800'
                  : 'border-red-200 bg-red-50 text-red-800',
              )}
            >
              {query.data.reconciled ? (
                <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-emerald-600" />
              ) : (
                <XCircle className="mt-0.5 h-4 w-4 shrink-0 text-red-600" />
              )}
              <span>
                {query.data.reconciled ? (
                  <>
                    Stock ledger reconciles. Stock on hand ({formatMoney(query.data.totalValue)})
                    matches the Inventory account ({formatMoney(query.data.ledgerBalance)}).
                  </>
                ) : (
                  <>
                    Stock ledger does not reconcile. Stock on hand (
                    {formatMoney(query.data.totalValue)}) differs from the Inventory account (
                    {formatMoney(query.data.ledgerBalance)}) by{' '}
                    {formatMoney(query.data.difference)}.
                  </>
                )}
              </span>
            </div>
            <p className="mt-2 text-xs text-muted-ink">
              Stock is counted in the stock ledger and valued in the Inventory account by two
              different code paths — one moves quantities, the other posts journal lines. Rather
              than assume the two agree, the comparison is made and published above.
            </p>
          </div>

          {query.data.rows.length === 0 ? (
            <Card>
              <EmptyState
                icon={<Package className="h-6 w-6" />}
                title="Nothing is stocked"
                description={`No product held stock as of ${formatDate(query.data.asOf)}. Buy goods or record opening stock and they will appear here.`}
              />
            </Card>
          ) : (
            <Card>
              <Table>
                <THead>
                  <tr>
                    <TH>Product</TH>
                    <TH className="w-28">Type</TH>
                    <TH className="w-40 text-right">Quantity on hand</TH>
                    <TH className="w-40 text-right">Average cost</TH>
                    <TH className="w-44 text-right">Value</TH>
                  </tr>
                </THead>
                <tbody>
                  {query.data.rows.map((row) => (
                    <TR key={row.productId}>
                      <TD className="font-medium">{row.productName}</TD>
                      <TD>
                        <Badge tone={TYPE_TONES[row.type]}>{titleCase(row.type)}</Badge>
                      </TD>
                      <TD className="text-right">
                        <span className="tabular">{formatNumber(row.quantityOnHand, 2)}</span>
                      </TD>
                      <TD className="text-right">
                        <span className="tabular">{formatMoney(row.averageCost)}</span>
                      </TD>
                      <TD className="text-right">
                        <span className="tabular font-medium">{formatMoney(row.value)}</span>
                      </TD>
                    </TR>
                  ))}
                </tbody>
                <tfoot>
                  <tr>
                    <td colSpan={4} className="border-t-2 border-lilac-300 px-4 py-3">
                      <span className="text-sm font-semibold text-plum-800">Total stock value</span>
                    </td>
                    <td className="tabular border-t-2 border-lilac-300 px-4 py-3 text-right text-sm font-semibold text-plum-800">
                      {formatMoney(query.data.totalValue)}
                    </td>
                  </tr>
                </tfoot>
              </Table>
              <CardBody className="border-t border-lilac-200 py-3">
                <p className="text-xs text-muted-ink">
                  Average cost is weighted: buying 10 at ₹1,000 and then 10 at ₹1,200 leaves 20 on
                  hand at ₹1,100 each. Buying stock capitalises into Inventory rather than becoming
                  an expense — the cost only reaches the profit and loss as cost of sales when the
                  goods are delivered. A combo appears at the number of whole bundles its scarcest
                  component can build; it holds no stock of its own.
                </p>
              </CardBody>
            </Card>
          )}
        </div>
      ) : null}
    </div>
  )
}
