import { purchaseApi, salesApi } from '@/api/endpoints'
import type {
  ContactType,
  DocumentResponse,
  DocumentStatus,
  OrderRequest,
  OrderResponse,
  OrderStatus,
  PageResponse,
  PaymentRequest,
  PaymentResponse,
} from '@/api/types'

/**
 * The sales and purchase flows are mirror images of each other
 * (Order -> Document -> Payment), so both screens are driven by this one
 * config rather than duplicating the whole workflow twice.
 */
export interface WorkflowConfig {
  kind: 'sales' | 'purchase'
  title: string
  description: string

  orderLabel: string // "Sales Order"
  orderLabelPlural: string
  documentLabel: string // "Customer Invoice"
  documentLabelPlural: string
  partyLabel: string // "Customer" / "Vendor"
  contactFilter: ContactType

  /** Terminal status of an order once it has been converted. */
  convertedStatus: OrderStatus
  convertActionLabel: string
  paymentActionLabel: string
  priceField: 'salesPrice' | 'cost'

  searchOrders: (p: { search?: string; status?: OrderStatus; page?: number; size?: number }) => Promise<PageResponse<OrderResponse>>
  getOrder: (id: number) => Promise<OrderResponse>
  createOrder: (body: OrderRequest) => Promise<OrderResponse>
  updateOrder: (id: number, body: OrderRequest) => Promise<OrderResponse>
  confirmOrder: (id: number) => Promise<OrderResponse>
  cancelOrder: (id: number) => Promise<OrderResponse>
  convertOrder: (id: number, body: { documentDate?: string; dueDate?: string }) => Promise<DocumentResponse>

  searchDocuments: (p: { search?: string; status?: DocumentStatus; page?: number; size?: number }) => Promise<PageResponse<DocumentResponse>>
  getDocument: (id: number) => Promise<DocumentResponse>
  postDocument: (id: number) => Promise<DocumentResponse>
  cancelDocument: (id: number) => Promise<DocumentResponse>
  payDocument: (id: number, body: PaymentRequest) => Promise<PaymentResponse>
}

export const salesWorkflow: WorkflowConfig = {
  kind: 'sales',
  title: 'Sales',
  description: 'Sales Order → Customer Invoice → Payment received',
  orderLabel: 'Sales Order',
  orderLabelPlural: 'Sales Orders',
  documentLabel: 'Invoice',
  documentLabelPlural: 'Invoices',
  partyLabel: 'Customer',
  contactFilter: 'CUSTOMER',
  convertedStatus: 'INVOICED',
  convertActionLabel: 'Create Invoice',
  paymentActionLabel: 'Register Payment',
  priceField: 'salesPrice',

  searchOrders: salesApi.searchOrders,
  getOrder: salesApi.getOrder,
  createOrder: salesApi.createOrder,
  updateOrder: salesApi.updateOrder,
  confirmOrder: salesApi.confirmOrder,
  cancelOrder: salesApi.cancelOrder,
  convertOrder: salesApi.createInvoice,

  searchDocuments: salesApi.searchInvoices,
  getDocument: salesApi.getInvoice,
  postDocument: salesApi.postInvoice,
  cancelDocument: salesApi.cancelInvoice,
  payDocument: salesApi.payInvoice,
}

export const purchaseWorkflow: WorkflowConfig = {
  kind: 'purchase',
  title: 'Purchases',
  description: 'Purchase Order → Vendor Bill → Payment made',
  orderLabel: 'Purchase Order',
  orderLabelPlural: 'Purchase Orders',
  documentLabel: 'Vendor Bill',
  documentLabelPlural: 'Vendor Bills',
  partyLabel: 'Vendor',
  contactFilter: 'VENDOR',
  convertedStatus: 'BILLED',
  convertActionLabel: 'Create Bill',
  paymentActionLabel: 'Register Payment',
  priceField: 'cost',

  searchOrders: purchaseApi.searchOrders,
  getOrder: purchaseApi.getOrder,
  createOrder: purchaseApi.createOrder,
  updateOrder: purchaseApi.updateOrder,
  confirmOrder: purchaseApi.confirmOrder,
  cancelOrder: purchaseApi.cancelOrder,
  convertOrder: purchaseApi.createBill,

  searchDocuments: purchaseApi.searchBills,
  getDocument: purchaseApi.getBill,
  postDocument: purchaseApi.postBill,
  cancelDocument: purchaseApi.cancelBill,
  payDocument: purchaseApi.payBill,
}
