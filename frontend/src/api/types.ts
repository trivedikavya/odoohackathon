/** Shared API types, mirroring the backend DTOs. */

/** What a party IS in a trade. */
export type PartyType = 'SELLER' | 'VENDOR' | 'CUSTOMER'

/** What a user may DO inside their own party's book. */
export type AccessLevel = 'ADMIN' | 'ACCOUNTANT' | 'USER'

export type AccountType = 'ASSET' | 'LIABILITY' | 'EQUITY' | 'INCOME' | 'EXPENSE'
export type JournalType = 'SALES' | 'PURCHASE' | 'CASH' | 'BANK' | 'GENERAL'
export type ProductType = 'GOODS' | 'SERVICE'
export type SourceType = 'INVOICE' | 'BILL' | 'PAYMENT' | 'OPENING' | 'MANUAL'
export type TaxTreatment = 'INTRA_STATE' | 'INTER_STATE' | 'UNSPECIFIED'
export type AnalyticAccountType = 'PROJECT' | 'DEPARTMENT' | 'COST_CENTER'
export type AgingType = 'RECEIVABLE' | 'PAYABLE'
export type PaymentMethod = 'CASH' | 'BANK'
export type PaymentDirection = 'IN' | 'OUT'

export type DealStatus =
  | 'RFQ_DRAFT'
  | 'RFQ_SENT'
  | 'REJECTED'
  | 'ACCEPTED'
  | 'DELIVERED'
  | 'INVOICED'
  | 'PARTIALLY_PAID'
  | 'PAID'
  | 'CANCELLED'

export type DocumentKind = 'SALES_ORDER' | 'PURCHASE_ORDER' | 'INVOICE' | 'BILL'
export type DocumentStatus = 'OPEN' | 'POSTED' | 'PARTIALLY_PAID' | 'PAID' | 'CANCELLED'

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  last: boolean
}

/** Identity */

export interface UserProfile {
  id: number
  loginId: string
  email: string
  fullName: string
  accessLevel: AccessLevel
  partyId: number
  partyType: PartyType
  partyName: string
  /** Null for customers — they keep no books. */
  bookId: number | null
}

export interface LoginResponse {
  token: string
  expiresInMs: number
  user: UserProfile
}

export interface AvailabilityResponse {
  available: boolean
  message: string
}

export interface RegisterRequest {
  loginId: string
  email: string
  password: string
  fullName: string
  partyType: PartyType
  organisationName?: string
  gstin?: string
  addressLine?: string
  city?: string
  state?: string
  pincode?: string
  phone?: string
}

export interface AppUser {
  id: number
  loginId: string
  email: string
  fullName: string
  accessLevel: AccessLevel
  active: boolean
}

/** Master data */

export interface PartyOption {
  id: number
  name: string
  type: PartyType
  city?: string | null
  state?: string | null
  keepsBooks: boolean
}

export interface Contact {
  id: number
  partyId: number
  name: string
  type: PartyType
  email?: string | null
  phone?: string | null
  gstin?: string | null
  city?: string | null
  state?: string | null
  creditDays: number
  active: boolean
  keepsBooks: boolean
}

export interface Product {
  id: number
  name: string
  type: ProductType
  salesPrice: string
  cost: string
  hsnCode?: string | null
  taxRate: string
  category?: string | null
  active: boolean
}

export interface Account {
  id: number
  code: string
  name: string
  type: AccountType
  systemCode?: string | null
  isSystem: boolean
  active: boolean
}

export interface Journal {
  id: number
  code: string
  name: string
  type: JournalType
  active: boolean
}

export interface Organisation {
  partyId: number
  bookId: number
  name: string
  type: PartyType
  email?: string | null
  phone?: string | null
  gstin?: string | null
  addressLine?: string | null
  city?: string | null
  state?: string | null
  pincode?: string | null
}

/** Trade */

export interface DealLine {
  id: number
  lineNo: number
  description: string
  hsnCode?: string | null
  quantity: string
  unitPrice: string
  taxRate: string
  untaxedAmount: string
  taxAmount: string
  cgstAmount: string
  sgstAmount: string
  igstAmount: string
  lineTotal: string
}

export interface DocumentSummary {
  id: number
  docType: DocumentKind
  docNo: string
  status: DocumentStatus
  totalAmount: string
  amountDue: string
}

export interface Deal {
  id: number
  dealNo: string
  buyerPartyId: number
  buyerName: string
  buyerType: PartyType
  sellerPartyId: number
  sellerName: string
  sellerType: PartyType
  initiatedByPartyId: number
  status: DealStatus
  dealDate: string
  expectedDelivery?: string | null
  deliveredAt?: string | null
  placeOfSupply?: string | null
  taxTreatment: TaxTreatment
  untaxedAmount: string
  taxAmount: string
  totalAmount: string
  notes?: string | null
  rejectReason?: string | null
  lines: DealLine[]
  /** True when the caller is the one who must accept or reject. */
  awaitingMyDecision: boolean
  /** True when the caller is the supplying side. */
  iAmSeller: boolean
  /** True when both sides keep books, so the deal mirrors. */
  mirrored: boolean
  myDocuments: DocumentSummary[]
}

export interface DocumentLine {
  id: number
  lineNo: number
  description: string
  hsnCode?: string | null
  quantity: string
  unitPrice: string
  taxRate: string
  untaxedAmount: string
  taxAmount: string
  cgstAmount: string
  sgstAmount: string
  igstAmount: string
  lineTotal: string
  analyticAccountId?: number | null
  analyticAccountName?: string | null
}

export interface TradeDocument {
  id: number
  dealId: number
  dealNo: string
  docType: DocumentKind
  docNo: string
  docDate: string
  dueDate?: string | null
  status: DocumentStatus
  counterpartyPartyId?: number | null
  counterpartyName?: string | null
  placeOfSupply?: string | null
  taxTreatment: TaxTreatment
  untaxedAmount: string
  taxAmount: string
  cgstAmount: string
  sgstAmount: string
  igstAmount: string
  totalAmount: string
  amountSettled: string
  amountDue: string
  journalEntryId?: number | null
  journalEntryNo?: string | null
  lines: DocumentLine[]
}

export interface Payment {
  id: number
  paymentNo: string
  documentId: number
  documentNo: string
  documentType: DocumentKind
  counterpartyPartyId?: number | null
  counterpartyName?: string | null
  direction: PaymentDirection
  method: PaymentMethod
  paymentDate: string
  amount: string
  reference?: string | null
  journalEntryId?: number | null
  journalEntryNo?: string | null
}

export interface DealLineRequest {
  description: string
  hsnCode?: string
  quantity: string
  unitPrice: string
  taxRate?: string
}

export interface CreateDealRequest {
  sellerPartyId: number
  dealDate: string
  expectedDelivery?: string
  notes?: string
  lines: DealLineRequest[]
}

export interface SettlementRequest {
  method: PaymentMethod
  settlementDate: string
  amount: string
  reference?: string
}

/** Ledger */

export interface JournalLine {
  id: number
  lineNo: number
  accountId: number
  accountCode: string
  accountName: string
  contactId?: number | null
  contactName?: string | null
  analyticAccountCode?: string | null
  label?: string | null
  debit: string
  credit: string
}

export interface JournalEntry {
  id: number
  entryNo: string
  journalCode: string
  journalName: string
  entryDate: string
  sourceType: SourceType
  sourceId?: number | null
  narration?: string | null
  totalDebit: string
  totalCredit: string
  balanced: boolean
  createdBy?: string | null
  lines: JournalLine[]
}

/** Reports */

export interface ReportLine {
  accountId: number | null
  code: string
  name: string
  type: AccountType
  amount: string
}

export interface ReportSection {
  title: string
  lines: ReportLine[]
  total: string
}

export interface BalanceSheet {
  asOf: string
  assets: ReportSection
  liabilities: ReportSection
  equity: ReportSection
  retainedEarnings: string
  totalAssets: string
  totalLiabilitiesAndEquity: string
  difference: string
  balanced: boolean
}

export interface ProfitAndLoss {
  from: string
  to: string
  income: ReportSection
  expenses: ReportSection
  totalIncome: string
  totalExpenses: string
  netProfit: string
}

export interface TrialBalanceRow {
  code: string
  name: string
  type: AccountType
  debit: string
  credit: string
}

export interface TrialBalance {
  asOf: string
  rows: TrialBalanceRow[]
  totalDebit: string
  totalCredit: string
  difference: string
  balanced: boolean
}

export interface DashboardSummary {
  asOf: string
  bookName: string
  totalSales: string
  totalPurchases: string
  cashBalance: string
  bankBalance: string
  cashAndBank: string
  accountsReceivable: string
  accountsPayable: string
  netProfit: string
  invoiceCount: number
  billCount: number
  openDealCount: number
  awaitingMyDecisionCount: number
}

export interface AgingDocument {
  documentId: number
  documentNo: string
  documentDate: string
  dueDate?: string | null
  daysOverdue: number
  amountDue: string
  bucket: string
}

export interface AgingRow {
  partyId: number
  partyName: string
  current: string
  days1To30: string
  days31To60: string
  days61To90: string
  days90Plus: string
  total: string
  documents: AgingDocument[]
}

export interface AgingReport {
  asOf: string
  type: AgingType
  rows: AgingRow[]
  current: string
  days1To30: string
  days31To60: string
  days61To90: string
  days90Plus: string
  total: string
  ledgerBalance: string
  difference: string
  reconciled: boolean
}

export interface ReconciliationRow {
  partyId: number
  partyName: string
  ourPosition: string
  theirPosition: string | null
  difference: string | null
  matched: boolean
  comparable: boolean
}

export interface ReconciliationReport {
  asOf: string
  rows: ReconciliationRow[]
  comparableCount: number
  matchedCount: number
  allMatched: boolean
}

export interface TrendPoint {
  period: string
  income: string
  expenses: string
  netProfit: string
}

export interface SalesTrend {
  from: string
  to: string
  points: TrendPoint[]
}

/** Analytic */

export interface AnalyticAccount {
  id: number
  code: string
  name: string
  type: AnalyticAccountType
  notes?: string | null
  active: boolean
}

export interface Budget {
  id: number
  name: string
  analyticAccountId: number
  analyticAccountCode: string
  analyticAccountName: string
  periodStart: string
  periodEnd: string
  plannedAmount: string
  responsible?: string | null
  notes?: string | null
  active: boolean
}

export interface BudgetPerformance {
  budgetId: number
  name: string
  analyticAccountCode: string
  analyticAccountName: string
  periodStart: string
  periodEnd: string
  responsible?: string | null
  plannedAmount: string
  actualAmount: string
  variance: string
  utilisationPercent?: string | null
  overBudget: boolean
}

export interface BudgetReport {
  asOf: string
  budgets: BudgetPerformance[]
  totalPlanned: string
  totalActual: string
  totalVariance: string
}

/** Portal */

export interface PortalSummary {
  partyId: number
  partyName: string
  totalBilled: string
  totalPaid: string
  outstanding: string
  openCount: number
  overdueCount: number
}
