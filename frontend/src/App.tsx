import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from '@/auth/AuthProvider'
import { ProtectedRoute } from '@/auth/ProtectedRoute'
import { AppLayout } from '@/components/layout/AppLayout'
import { ToastProvider } from '@/components/ui/Toast'
import { AccountsPage } from '@/pages/AccountsPage'
import { ContactsPage } from '@/pages/ContactsPage'
import { DashboardPage } from '@/pages/DashboardPage'
import { JournalsPage } from '@/pages/JournalsPage'
import { LedgerPage } from '@/pages/LedgerPage'
import { LoginPage } from '@/pages/LoginPage'
import { PaymentsPage } from '@/pages/PaymentsPage'
import { ProductsPage } from '@/pages/ProductsPage'
import { UsersPage } from '@/pages/UsersPage'
import { BalanceSheetPage } from '@/pages/reports/BalanceSheetPage'
import { ProfitAndLossPage } from '@/pages/reports/ProfitAndLossPage'
import { TransactionWorkflowPage } from '@/pages/transactions/TransactionWorkflowPage'
import { purchaseWorkflow, salesWorkflow } from '@/pages/transactions/workflowConfig'

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
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <ToastProvider>
            <Routes>
              <Route path="/login" element={<LoginPage />} />

              <Route element={<ProtectedRoute />}>
                <Route element={<AppLayout />}>
                  <Route index element={<DashboardPage />} />
                  <Route path="contacts" element={<ContactsPage />} />
                  <Route path="products" element={<ProductsPage />} />
                  <Route
                    path="purchases"
                    element={<TransactionWorkflowPage config={purchaseWorkflow} />}
                  />
                  <Route
                    path="sales"
                    element={<TransactionWorkflowPage config={salesWorkflow} />}
                  />
                  <Route path="payments" element={<PaymentsPage />} />
                  <Route path="ledger" element={<LedgerPage />} />
                  <Route path="accounts" element={<AccountsPage />} />
                  <Route path="journals" element={<JournalsPage />} />
                  <Route path="reports/balance-sheet" element={<BalanceSheetPage />} />
                  <Route path="reports/profit-and-loss" element={<ProfitAndLossPage />} />

                  {/* Admin-only. The server blocks these endpoints too. */}
                  <Route element={<ProtectedRoute roles={['ADMIN']} />}>
                    <Route path="users" element={<UsersPage />} />
                  </Route>
                </Route>
              </Route>

              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </ToastProvider>
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
