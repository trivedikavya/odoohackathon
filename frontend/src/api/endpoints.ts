import { api } from './client'
import type {
  Account,
  AppUser,
  BalanceSheet,
  Contact,
  ContactOption,
  DashboardSummary,
  DocumentResponse,
  DocumentStatus,
  Journal,
  JournalEntryResponse,
  LoginResponse,
  OrderRequest,
  OrderResponse,
  OrderStatus,
  PageResponse,
  PaymentRequest,
  PaymentResponse,
  Product,
  ProductOption,
  ProfitAndLoss,
  SourceType,
  UserProfile,
} from './types'

const unwrap = <T,>(p: Promise<{ data: T }>) => p.then((r) => r.data)

export const authApi = {
  login: (email: string, password: string) =>
    unwrap<LoginResponse>(api.post('/auth/login', { email, password })),
  me: () => unwrap<UserProfile>(api.get('/auth/me')),
  changePassword: (currentPassword: string, newPassword: string) =>
    unwrap<void>(api.post('/auth/change-password', { currentPassword, newPassword })),
}

export const usersApi = {
  list: () => unwrap<AppUser[]>(api.get('/users')),
  create: (body: {
    email: string
    password: string
    fullName: string
    role: string
    contactId?: number | null
  }) => unwrap<AppUser>(api.post('/users', body)),
  update: (id: number, body: { fullName?: string; role?: string; active?: boolean }) =>
    unwrap<AppUser>(api.put(`/users/${id}`, body)),
  resetPassword: (id: number, newPassword: string) =>
    unwrap<void>(api.put(`/users/${id}/password`, { newPassword })),
}

export const contactsApi = {
  search: (params: { search?: string; type?: string; includeArchived?: boolean; page?: number; size?: number }) =>
    unwrap<PageResponse<Contact>>(api.get('/contacts', { params })),
  options: () => unwrap<ContactOption[]>(api.get('/contacts/options')),
  get: (id: number) => unwrap<Contact>(api.get(`/contacts/${id}`)),
  create: (body: Partial<Contact>) => unwrap<Contact>(api.post('/contacts', body)),
  update: (id: number, body: Partial<Contact>) => unwrap<Contact>(api.put(`/contacts/${id}`, body)),
  archive: (id: number) => unwrap<Contact>(api.put(`/contacts/${id}/archive`)),
  restore: (id: number) => unwrap<Contact>(api.put(`/contacts/${id}/restore`)),
}

export const productsApi = {
  search: (params: { search?: string; type?: string; includeArchived?: boolean; page?: number; size?: number }) =>
    unwrap<PageResponse<Product>>(api.get('/products', { params })),
  options: () => unwrap<ProductOption[]>(api.get('/products/options')),
  categories: () => unwrap<string[]>(api.get('/products/categories')),
  create: (body: Partial<Product>) => unwrap<Product>(api.post('/products', body)),
  update: (id: number, body: Partial<Product>) => unwrap<Product>(api.put(`/products/${id}`, body)),
  archive: (id: number) => unwrap<Product>(api.put(`/products/${id}/archive`)),
  restore: (id: number) => unwrap<Product>(api.put(`/products/${id}/restore`)),
}

export const accountsApi = {
  list: (includeArchived = false) =>
    unwrap<Account[]>(api.get('/accounts', { params: { includeArchived } })),
  create: (body: { code: string; name: string; type: string }) =>
    unwrap<Account>(api.post('/accounts', body)),
  update: (id: number, body: { code: string; name: string; type: string }) =>
    unwrap<Account>(api.put(`/accounts/${id}`, body)),
  archive: (id: number) => unwrap<Account>(api.put(`/accounts/${id}/archive`)),
}

export const journalsApi = {
  list: () => unwrap<Journal[]>(api.get('/journals')),
  create: (body: { code: string; name: string; type: string; defaultAccountId?: number | null }) =>
    unwrap<Journal>(api.post('/journals', body)),
}

export const salesApi = {
  searchOrders: (params: { search?: string; status?: OrderStatus; page?: number; size?: number }) =>
    unwrap<PageResponse<OrderResponse>>(api.get('/sales-orders', { params })),
  getOrder: (id: number) => unwrap<OrderResponse>(api.get(`/sales-orders/${id}`)),
  createOrder: (body: OrderRequest) => unwrap<OrderResponse>(api.post('/sales-orders', body)),
  updateOrder: (id: number, body: OrderRequest) =>
    unwrap<OrderResponse>(api.put(`/sales-orders/${id}`, body)),
  confirmOrder: (id: number) => unwrap<OrderResponse>(api.post(`/sales-orders/${id}/confirm`)),
  cancelOrder: (id: number) => unwrap<OrderResponse>(api.post(`/sales-orders/${id}/cancel`)),
  createInvoice: (id: number, body: { documentDate?: string; dueDate?: string }) =>
    unwrap<DocumentResponse>(api.post(`/sales-orders/${id}/create-invoice`, body)),

  searchInvoices: (params: { search?: string; status?: DocumentStatus; page?: number; size?: number }) =>
    unwrap<PageResponse<DocumentResponse>>(api.get('/invoices', { params })),
  getInvoice: (id: number) => unwrap<DocumentResponse>(api.get(`/invoices/${id}`)),
  postInvoice: (id: number) => unwrap<DocumentResponse>(api.post(`/invoices/${id}/post`)),
  cancelInvoice: (id: number) => unwrap<DocumentResponse>(api.post(`/invoices/${id}/cancel`)),
  payInvoice: (id: number, body: PaymentRequest) =>
    unwrap<PaymentResponse>(api.post(`/invoices/${id}/payments`, body)),
}

export const purchaseApi = {
  searchOrders: (params: { search?: string; status?: OrderStatus; page?: number; size?: number }) =>
    unwrap<PageResponse<OrderResponse>>(api.get('/purchase-orders', { params })),
  getOrder: (id: number) => unwrap<OrderResponse>(api.get(`/purchase-orders/${id}`)),
  createOrder: (body: OrderRequest) => unwrap<OrderResponse>(api.post('/purchase-orders', body)),
  updateOrder: (id: number, body: OrderRequest) =>
    unwrap<OrderResponse>(api.put(`/purchase-orders/${id}`, body)),
  confirmOrder: (id: number) => unwrap<OrderResponse>(api.post(`/purchase-orders/${id}/confirm`)),
  cancelOrder: (id: number) => unwrap<OrderResponse>(api.post(`/purchase-orders/${id}/cancel`)),
  createBill: (id: number, body: { documentDate?: string; dueDate?: string }) =>
    unwrap<DocumentResponse>(api.post(`/purchase-orders/${id}/create-bill`, body)),

  searchBills: (params: { search?: string; status?: DocumentStatus; page?: number; size?: number }) =>
    unwrap<PageResponse<DocumentResponse>>(api.get('/bills', { params })),
  getBill: (id: number) => unwrap<DocumentResponse>(api.get(`/bills/${id}`)),
  postBill: (id: number) => unwrap<DocumentResponse>(api.post(`/bills/${id}/post`)),
  cancelBill: (id: number) => unwrap<DocumentResponse>(api.post(`/bills/${id}/cancel`)),
  payBill: (id: number, body: PaymentRequest) =>
    unwrap<PaymentResponse>(api.post(`/bills/${id}/payments`, body)),
}

export const paymentsApi = {
  search: (params: { search?: string; direction?: string; page?: number; size?: number }) =>
    unwrap<PageResponse<PaymentResponse>>(api.get('/payments', { params })),
}

export const capitalApi = {
  contribute: (body: { method: string; date: string; amount: string; note?: string }) =>
    unwrap<{ journalEntryId: number; journalEntryNo: string; amount: string }>(
      api.post('/capital/contributions', body),
    ),
}

export const ledgerApi = {
  search: (params: { from?: string; to?: string; sourceType?: SourceType; page?: number; size?: number }) =>
    unwrap<PageResponse<JournalEntryResponse>>(api.get('/ledger/journal-entries', { params })),
  get: (id: number) => unwrap<JournalEntryResponse>(api.get(`/ledger/journal-entries/${id}`)),
}

export const reportsApi = {
  balanceSheet: (asOf?: string) =>
    unwrap<BalanceSheet>(api.get('/reports/balance-sheet', { params: { asOf } })),
  profitAndLoss: (from?: string, to?: string) =>
    unwrap<ProfitAndLoss>(api.get('/reports/profit-and-loss', { params: { from, to } })),
  dashboard: (asOf?: string) =>
    unwrap<DashboardSummary>(api.get('/reports/dashboard', { params: { asOf } })),
}
