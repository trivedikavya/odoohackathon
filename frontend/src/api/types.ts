/** Shared API types, mirroring the backend DTOs. */

export type Role = 'ADMIN' | 'ACCOUNTANT' | 'CONTACT'
export type ContactType = 'CUSTOMER' | 'VENDOR' | 'BOTH'
export type ProductType = 'GOODS' | 'SERVICE' | 'COMBO'
export type AccountType = 'ASSET' | 'LIABILITY' | 'EQUITY' | 'INCOME' | 'EXPENSE'
export type JournalType = 'SALES' | 'PURCHASE' | 'CASH' | 'BANK'
export type OrderStatus = 'DRAFT' | 'CONFIRMED' | 'INVOICED' | 'BILLED' | 'CANCELLED'
export type DocumentStatus = 'DRAFT' | 'POSTED' | 'PARTIALLY_PAID' | 'PAID' | 'CANCELLED'
export type PaymentMethod = 'CASH' | 'BANK'
export type PaymentDirection = 'RECEIVE' | 'PAY'
export type SourceType = 'INVOICE' | 'BILL' | 'PAYMENT' | 'MANUAL'

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  last: boolean
}

export interface UserProfile {
  id: number
  email: string
  fullName: string
  role: Role
  contactId: number | null
}

export interface LoginResponse {
  token: string
  expiresInMs: number
  user: UserProfile
}

export interface AppUser {
  id: number
  email: string
  fullName: string
  role: Role
  contactId: number | null
  active: boolean
}

export interface Contact {
  id: number
  name: string
  type: ContactType
  email?: string | null
  mobile?: string | null
  addressLine?: string | null
  city?: string | null
  state?: string | null
  pincode?: string | null
  gstin?: string | null
  profileImageUrl?: string | null
  active: boolean
}

export interface ContactOption {
  id: number
  name: string
  type: ContactType
}

export interface Product {
  id: number
  name: string
  type: ProductType
  salesPrice: string
  cost: string
  category?: string | null
  hsnCode?: string | null
  taxRate: string
  active: boolean
}

export interface ProductOption {
  id: number
  name: string
  type: ProductType
  salesPrice: string
  cost: string
  taxRate: string
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
  defaultAccountId?: number | null
  defaultAccountName?: string | null
  active: boolean
}

export interface LineResponse {
  id: number
  lineNo: number
  productId: number
  productName: string
  quantity: string
  unitPrice: string
  taxRate: string
  untaxedAmount: string
  taxAmount: string
  lineTotal: string
}

export interface OrderResponse {
  id: number
  orderNo: string
  contactId: number
  contactName: string
  orderDate: string
  status: OrderStatus
  untaxedAmount: string
  taxAmount: string
  totalAmount: string
  notes?: string | null
  lines: LineResponse[]
  generatedDocumentId?: number | null
  generatedDocumentNo?: string | null
}

export interface DocumentResponse {
  id: number
  documentNo: string
  contactId: number
  contactName: string
  documentDate: string
  dueDate?: string | null
  status: DocumentStatus
  untaxedAmount: string
  taxAmount: string
  totalAmount: string
  amountPaid: string
  amountDue: string
  journalEntryId?: number | null
  journalEntryNo?: string | null
  sourceOrderId?: number | null
  sourceOrderNo?: string | null
  notes?: string | null
  lines: LineResponse[]
}

export interface PaymentResponse {
  id: number
  paymentNo: string
  contactId: number
  contactName: string
  direction: PaymentDirection
  method: PaymentMethod
  paymentDate: string
  amount: string
  invoiceId?: number | null
  invoiceNo?: string | null
  billId?: number | null
  billNo?: string | null
  journalEntryId?: number | null
  journalEntryNo?: string | null
  reference?: string | null
  reconciled: boolean
}

export interface JournalLineResponse {
  id: number
  lineNo: number
  accountId: number
  accountCode: string
  accountName: string
  contactId?: number | null
  contactName?: string | null
  label?: string | null
  debit: string
  credit: string
}

export interface JournalEntryResponse {
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
  lines: JournalLineResponse[]
}

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

export interface DashboardSummary {
  asOf: string
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
  contactCount: number
  productCount: number
}

/** Request payloads */

export interface LineRequest {
  productId: number
  quantity: string
  unitPrice: string
  taxRate?: string
}

export interface OrderRequest {
  contactId: number
  orderDate: string
  notes?: string
  lines: LineRequest[]
}

export interface PaymentRequest {
  method: PaymentMethod
  paymentDate: string
  amount: string
  reference?: string
}
