import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { AuthProvider } from '@/auth/AuthProvider'
import { ProtectedRoute } from '@/auth/ProtectedRoute'
import { AppLayout } from '@/components/layout/AppLayout'
import { PortalLayout } from '@/components/layout/PortalLayout'
import { ToastProvider } from '@/components/ui/Toast'
import { AccountsPage } from '@/pages/AccountsPage'
import { AnalyticAccountsPage } from '@/pages/AnalyticAccountsPage'
import { BudgetsPage } from '@/pages/BudgetsPage'
import { ContactsPage } from '@/pages/ContactsPage'
import { DashboardPage } from '@/pages/DashboardPage'
import { DealsPage } from '@/pages/DealsPage'
import { DocumentsPage } from '@/pages/DocumentsPage'
import { AppErrorBoundary, NotFoundPage } from '@/pages/ErrorPages'
import { LedgerPage } from '@/pages/LedgerPage'
import { LoginPage } from '@/pages/LoginPage'
import { OrganisationPage } from '@/pages/OrganisationPage'
import { PaymentsPage } from '@/pages/PaymentsPage'
import { ProductsPage } from '@/pages/ProductsPage'
import { RegisterPage } from '@/pages/RegisterPage'
import { UsersPage } from '@/pages/UsersPage'
import { PortalBuyPage } from '@/pages/portal/PortalBuyPage'
import { PortalInvoicesPage } from '@/pages/portal/PortalInvoicesPage'
import { PortalOverviewPage } from '@/pages/portal/PortalOverviewPage'
import { PortalPaymentsPage } from '@/pages/portal/PortalPaymentsPage'
import { AgingReportPage } from '@/pages/reports/AgingReportPage'
import { BalanceSheetPage } from '@/pages/reports/BalanceSheetPage'
import { BudgetReportPage } from '@/pages/reports/BudgetReportPage'
import { ProfitAndLossPage } from '@/pages/reports/ProfitAndLossPage'
import { ReconciliationPage } from '@/pages/reports/ReconciliationPage'
import { TrialBalancePage } from '@/pages/reports/TrialBalancePage'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
      staleTime: 10_000,
    },
  },
})

export default function App() {
  return (
    <AppErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <AuthProvider>
            <ToastProvider>
              <Routes>
                <Route path="/login" element={<LoginPage />} />
                <Route path="/register" element={<RegisterPage />} />

                {/*
                  Customer self-service. A separate tree with its own shell:
                  a USER account is barred from every non-portal API path, so
                  a trimmed-down back office would imply pages exist that
                  cannot load.
                */}
                <Route element={<ProtectedRoute levels={['USER']} />}>
                  <Route path="/portal" element={<PortalLayout />}>
                    <Route index element={<PortalOverviewPage />} />
                    <Route path="buy" element={<PortalBuyPage />} />
                    <Route path="invoices" element={<PortalInvoicesPage />} />
                    <Route path="payments" element={<PortalPaymentsPage />} />
                  </Route>
                </Route>

                {/* Back office, for staff of a seller or vendor. */}
                <Route element={<ProtectedRoute levels={['ADMIN', 'ACCOUNTANT']} />}>
                  <Route element={<AppLayout />}>
                    <Route index element={<DashboardPage />} />
                    <Route path="deals" element={<DealsPage />} />
                    <Route path="documents" element={<DocumentsPage />} />
                    <Route path="payments" element={<PaymentsPage />} />
                    <Route path="contacts" element={<ContactsPage />} />
                    <Route path="products" element={<ProductsPage />} />
                    <Route path="ledger" element={<LedgerPage />} />
                    <Route path="accounts" element={<AccountsPage />} />
                    <Route path="analytic-accounts" element={<AnalyticAccountsPage />} />
                    <Route path="budgets" element={<BudgetsPage />} />
                    <Route path="reports/trial-balance" element={<TrialBalancePage />} />
                    <Route path="reports/balance-sheet" element={<BalanceSheetPage />} />
                    <Route path="reports/profit-and-loss" element={<ProfitAndLossPage />} />
                    <Route path="reports/aging" element={<AgingReportPage />} />
                    <Route path="reports/reconciliation" element={<ReconciliationPage />} />
                    <Route path="reports/budget" element={<BudgetReportPage />} />

                    {/* Admin-only. The server blocks these endpoints too. */}
                    <Route element={<ProtectedRoute levels={['ADMIN']} />}>
                      <Route path="organisation" element={<OrganisationPage />} />
                      <Route path="users" element={<UsersPage />} />
                    </Route>
                  </Route>
                </Route>

                <Route path="*" element={<NotFoundPage />} />
              </Routes>
            </ToastProvider>
          </AuthProvider>
        </BrowserRouter>
      </QueryClientProvider>
    </AppErrorBoundary>
  )
}
