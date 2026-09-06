import { api } from './client'
import type {
  Account,
  CapitalRequest,
  ContactRelationship,
  DirectTradeRequest,
  ExpenseRequest,
  StockLedger,
  AgingReport,
  AgingType,
  AnalyticAccount,
  AnalyticAccountType,
  AppUser,
  AvailabilityResponse,
  BalanceSheet,
  Budget,
  BudgetReport,
  Contact,
  CreateDealRequest,
  DashboardSummary,
  Deal,
  DealStatus,
  DocumentKind,
  DocumentStatus,
  Journal,
  JournalEntry,
  LoginResponse,
  Organisation,
  PageResponse,
  PartyOption,
  PartyType,
  Payment,
  PortalSummary,
  Product,
  ProfitAndLoss,
  ReconciliationReport,
  RegisterRequest,
  SalesTrend,
  SettlementRequest,
  SourceType,
  TradeDocument,
  TrialBalance,
  UserProfile,
} from './types'

const unwrap = <T,>(p: Promise<{ data: T }>) => p.then((r) => r.data)

/**
 * A product is written with component ids, but read back with the components
 * already expanded to names and live stock — so the write shape is its own type.
 */
export type ProductPayload = Omit<Partial<Product>, 'components'> & {
  components?: { componentProductId: number; quantity: string }[]
}

export const authApi = {
  login: (identifier: string, password: string) =>
    unwrap<LoginResponse>(api.post('/auth/login', { identifier, password })),
  register: (body: RegisterRequest) => unwrap<LoginResponse>(api.post('/auth/register', body)),
  checkLoginId: (loginId: string) =>
    unwrap<AvailabilityResponse>(api.get('/auth/check-login-id', { params: { loginId } })),
  me: () => unwrap<UserProfile>(api.get('/auth/me')),
  changePassword: (currentPassword: string, newPassword: string) =>
    unwrap<void>(api.post('/auth/change-password', { currentPassword, newPassword })),
}

export const usersApi = {
  list: () => unwrap<AppUser[]>(api.get('/users')),
  create: (body: {
    loginId: string
    email: string
    password: string
    fullName: string
    accessLevel: string
  }) => unwrap<AppUser>(api.post('/users', body)),
  deactivate: (id: number) => unwrap<AppUser>(api.put(`/users/${id}/deactivate`)),
  activate: (id: number) => unwrap<AppUser>(api.put(`/users/${id}/activate`)),
}

export const masterApi = {
  suppliers: () => unwrap<PartyOption[]>(api.get('/suppliers')),

  contacts: (params: { search?: string; includeArchived?: boolean } = {}) =>
    unwrap<Contact[]>(api.get('/contacts', { params })),
  createContact: (body: {
    name: string
    type: PartyType
    email?: string
    phone?: string
    gstin?: string
    addressLine?: string
    city?: string
    state?: string
    pincode?: string
    creditDays?: number
    relationship?: ContactRelationship
  }) => unwrap<Contact>(api.post('/contacts', body)),
  linkContact: (partyId: number, creditDays?: number) =>
    unwrap<Contact>(api.post('/contacts/link', { partyId, creditDays })),
  archiveContact: (id: number) => unwrap<Contact>(api.put(`/contacts/${id}/archive`)),
  restoreContact: (id: number) => unwrap<Contact>(api.put(`/contacts/${id}/restore`)),

  products: (params: { search?: string; includeArchived?: boolean } = {}) =>
    unwrap<Product[]>(api.get('/products', { params })),
  createProduct: (body: ProductPayload) => unwrap<Product>(api.post('/products', body)),
  updateProduct: (id: number, body: ProductPayload) =>
    unwrap<Product>(api.put(`/products/${id}`, body)),
  archiveProduct: (id: number) => unwrap<Product>(api.put(`/products/${id}/archive`)),
  restoreProduct: (id: number) => unwrap<Product>(api.put(`/products/${id}/restore`)),

  accounts: (includeArchived = false) =>
    unwrap<Account[]>(api.get('/accounts', { params: { includeArchived } })),
  createAccount: (body: { code: string; name: string; type: string }) =>
    unwrap<Account>(api.post('/accounts', body)),
  archiveAccount: (id: number) => unwrap<Account>(api.put(`/accounts/${id}/archive`)),

  journals: () => unwrap<Journal[]>(api.get('/journals')),

  organisation: () => unwrap<Organisation>(api.get('/organisation')),
  updateOrganisation: (body: {
    name: string
    gstin?: string
    addressLine?: string
    city?: string
    state: string
    pincode?: string
    phone?: string
  }) => unwrap<Organisation>(api.put('/organisation', body)),
}

export const tradeApi = {
  deals: (params: { status?: DealStatus; search?: string; page?: number; size?: number } = {}) =>
    unwrap<PageResponse<Deal>>(api.get('/deals', { params })),
  inbox: (params: { page?: number; size?: number } = {}) =>
    unwrap<PageResponse<Deal>>(api.get('/deals/inbox', { params })),
  deal: (id: number) => unwrap<Deal>(api.get(`/deals/${id}`)),
  createDeal: (body: CreateDealRequest) => unwrap<Deal>(api.post('/deals', body)),
  send: (id: number) => unwrap<Deal>(api.post(`/deals/${id}/send`, {})),
  accept: (id: number) => unwrap<Deal>(api.post(`/deals/${id}/accept`, {})),
  reject: (id: number, reason?: string) => unwrap<Deal>(api.post(`/deals/${id}/reject`, { reason })),
  deliver: (id: number, deliveredAt?: string) =>
    unwrap<Deal>(api.post(`/deals/${id}/deliver`, { deliveredAt })),
  invoice: (id: number, body: { docDate?: string; dueDate?: string } = {}) =>
    unwrap<Deal>(api.post(`/deals/${id}/invoice`, body)),
  settle: (id: number, body: SettlementRequest) =>
    unwrap<Deal>(api.post(`/deals/${id}/settle`, body)),
  cancel: (id: number) => unwrap<Deal>(api.post(`/deals/${id}/cancel`, {})),

  documents: (params: {
    docType: DocumentKind
    status?: DocumentStatus
    search?: string
    page?: number
    size?: number
  }) => unwrap<PageResponse<TradeDocument>>(api.get('/documents', { params })),
  document: (id: number) => unwrap<TradeDocument>(api.get(`/documents/${id}`)),
  tagLine: (documentId: number, documentLineId: number, analyticAccountId: number | null) =>
    unwrap<TradeDocument>(
      api.post(`/documents/${documentId}/tag-line`, { documentLineId, analyticAccountId }),
    ),

  payments: (params: { search?: string; page?: number; size?: number } = {}) =>
    unwrap<PageResponse<Payment>>(api.get('/payments', { params })),
}

export const ledgerApi = {
  entries: (params: {
    sourceType?: SourceType
    from?: string
    to?: string
    page?: number
    size?: number
  } = {}) => unwrap<PageResponse<JournalEntry>>(api.get('/ledger/journal-entries', { params })),
  entry: (id: number) => unwrap<JournalEntry>(api.get(`/ledger/journal-entries/${id}`)),
}

export const reportsApi = {
  dashboard: () => unwrap<DashboardSummary>(api.get('/reports/dashboard')),
  balanceSheet: (asOf?: string) =>
    unwrap<BalanceSheet>(api.get('/reports/balance-sheet', { params: { asOf } })),
  profitAndLoss: (from?: string, to?: string) =>
    unwrap<ProfitAndLoss>(api.get('/reports/profit-and-loss', { params: { from, to } })),
  trialBalance: (asOf?: string) =>
    unwrap<TrialBalance>(api.get('/reports/trial-balance', { params: { asOf } })),
  aging: (type: AgingType, asOf?: string) =>
    unwrap<AgingReport>(api.get('/reports/aging', { params: { type, asOf } })),
  reconciliation: (asOf?: string) =>
    unwrap<ReconciliationReport>(api.get('/reports/reconciliation', { params: { asOf } })),
  budget: (asOf?: string) => unwrap<BudgetReport>(api.get('/reports/budget', { params: { asOf } })),
  stockLedger: (asOf?: string) =>
    unwrap<StockLedger>(api.get('/reports/stock-ledger', { params: { asOf } })),
  salesTrend: (months = 12) =>
    unwrap<SalesTrend>(api.get('/reports/sales-trend', { params: { months } })),
}

export const analyticApi = {
  list: (includeArchived = false) =>
    unwrap<AnalyticAccount[]>(api.get('/analytic-accounts', { params: { includeArchived } })),
  create: (body: { code: string; name: string; type: AnalyticAccountType; notes?: string }) =>
    unwrap<AnalyticAccount>(api.post('/analytic-accounts', body)),
  update: (
    id: number,
    body: { code: string; name: string; type: AnalyticAccountType; notes?: string },
  ) => unwrap<AnalyticAccount>(api.put(`/analytic-accounts/${id}`, body)),
  archive: (id: number) => unwrap<AnalyticAccount>(api.put(`/analytic-accounts/${id}/archive`)),
  restore: (id: number) => unwrap<AnalyticAccount>(api.put(`/analytic-accounts/${id}/restore`)),

  budgets: (includeArchived = false) =>
    unwrap<Budget[]>(api.get('/budgets', { params: { includeArchived } })),
  createBudget: (body: {
    name: string
    analyticAccountId: number
    periodStart: string
    periodEnd: string
    plannedAmount: string
    responsible?: string
    notes?: string
  }) => unwrap<Budget>(api.post('/budgets', body)),
  archiveBudget: (id: number) => unwrap<Budget>(api.put(`/budgets/${id}/archive`)),
}

/**
 * The entries that are not a trade with a counterparty: money the owner
 * puts in, stock the book already had, overheads, and trades with people
 * who are not on the platform.
 */
export const operationsApi = {
  capital: (body: CapitalRequest) =>
    unwrap<{ journalEntryId: number; entryNo: string }>(api.post('/operations/capital', body)),
  openingStock: (body: { productId: number; date: string; quantity: string; unitCost: string }) =>
    unwrap<{ journalEntryId: number; entryNo: string }>(
      api.post('/operations/opening-stock', body),
    ),
  expense: (body: ExpenseRequest) =>
    unwrap<{ journalEntryId: number; entryNo: string }>(api.post('/operations/expenses', body)),
  directPurchase: (body: DirectTradeRequest) =>
    unwrap<Deal>(api.post('/operations/direct-purchase', body)),
  directSale: (body: DirectTradeRequest) =>
    unwrap<Deal>(api.post('/operations/direct-sale', body)),
}

/**
 * Customer self-service. A customer keeps no books, so everything here is
 * scoped by counterparty rather than by book — they see invoices from
 * every supplier they have bought from, in one place.
 */
export const portalApi = {
  summary: () => unwrap<PortalSummary>(api.get('/portal/summary')),
  myDocuments: (params: { page?: number; size?: number } = {}) =>
    unwrap<PageResponse<TradeDocument>>(api.get('/portal/my-documents', { params })),
  myDocument: (id: number) => unwrap<TradeDocument>(api.get(`/portal/my-documents/${id}`)),
  myPayments: (params: { page?: number; size?: number } = {}) =>
    unwrap<PageResponse<Payment>>(api.get('/portal/my-payments', { params })),
  pay: (id: number, body: SettlementRequest) =>
    unwrap<Deal>(api.post(`/portal/my-documents/${id}/pay`, body)),

  suppliers: () => unwrap<PartyOption[]>(api.get('/portal/suppliers')),
  /** What one supplier is willing to sell, so a customer picks rather than types. */
  supplierCatalogue: (partyId: number) =>
    unwrap<Product[]>(api.get(`/portal/suppliers/${partyId}/catalogue`)),
  myOrders: (params: { page?: number; size?: number } = {}) =>
    unwrap<PageResponse<Deal>>(api.get('/portal/my-orders', { params })),
  myOrder: (id: number) => unwrap<Deal>(api.get(`/portal/my-orders/${id}`)),
  createOrder: (body: CreateDealRequest) => unwrap<Deal>(api.post('/portal/my-orders', body)),
  sendOrder: (id: number) => unwrap<Deal>(api.post(`/portal/my-orders/${id}/send`, {})),
  cancelOrder: (id: number) => unwrap<Deal>(api.post(`/portal/my-orders/${id}/cancel`, {})),
}
