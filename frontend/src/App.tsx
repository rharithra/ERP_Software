import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ThemeProvider } from "next-themes";
import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { AuthProvider } from "@/auth/auth-context";
import { AppShell } from "@/components/layout/app-shell";
import { GuestRoute } from "@/components/layout/guest-route";
import { ProtectedRoute } from "@/components/layout/protected-route";
import { Toaster } from "@/components/ui/sonner";
import { ComingSoonPage } from "@/pages/coming-soon-page";
import { CategoriesPage } from "@/pages/categories-page";
import { CompanyPage } from "@/pages/company-page";
import { CustomersPage } from "@/pages/customers-page";
import { DashboardPage } from "@/pages/dashboard-page";
import { InventoryPage } from "@/pages/inventory-page";
import { LandingPage } from "@/pages/landing-page";
import { LoginPage } from "@/pages/login-page";
import { ProductsPage } from "@/pages/products-page";
import { PurchaseDetailPage } from "@/pages/purchase-detail-page";
import { PurchaseFormPage } from "@/pages/purchase-form-page";
import { PurchasesPage } from "@/pages/purchases-page";
import { SaleDetailPage } from "@/pages/sale-detail-page";
import { SaleInvoicePage } from "@/pages/sale-invoice-page";
import { SalePosPage } from "@/pages/sale-pos-page";
import { SalesPage } from "@/pages/sales-page";
import { SignupPage } from "@/pages/signup-page";
import { SuppliersPage } from "@/pages/suppliers-page";

const queryClient = new QueryClient();

export default function App() {
  return (
    <ThemeProvider attribute="class" defaultTheme="light" enableSystem>
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <BrowserRouter>
            <Routes>
              <Route path="/" element={<LandingPage />} />
              <Route
                path="/login"
                element={
                  <GuestRoute>
                    <LoginPage />
                  </GuestRoute>
                }
              />
              <Route
                path="/signup"
                element={
                  <GuestRoute>
                    <SignupPage />
                  </GuestRoute>
                }
              />
              <Route element={<ProtectedRoute />}>
                <Route path="/app" element={<AppShell />}>
                  <Route index element={<DashboardPage />} />
                  <Route path="company" element={<CompanyPage />} />
                  <Route path="categories" element={<CategoriesPage />} />
                  <Route path="sales" element={<SalesPage />} />
                  <Route path="sales/new" element={<SalePosPage />} />
                  <Route path="sales/:id" element={<SaleDetailPage />} />
                  <Route path="sales/:id/invoice" element={<SaleInvoicePage />} />
                  <Route path="products" element={<ProductsPage />} />
                  <Route path="inventory" element={<InventoryPage />} />
                  <Route path="customers" element={<CustomersPage />} />
                  <Route path="suppliers" element={<SuppliersPage />} />
                  <Route path="purchases" element={<PurchasesPage />} />
                  <Route path="purchases/new" element={<PurchaseFormPage />} />
                  <Route path="purchases/:id" element={<PurchaseDetailPage />} />
                  <Route path="purchases/:id/edit" element={<PurchaseFormPage />} />
                  <Route path="reports" element={<ComingSoonPage />} />
                  <Route path="expenses" element={<ComingSoonPage />} />
                  <Route path="employees" element={<ComingSoonPage />} />
                  <Route path="settings" element={<ComingSoonPage />} />
                </Route>
              </Route>
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </BrowserRouter>
          <Toaster />
        </AuthProvider>
      </QueryClientProvider>
    </ThemeProvider>
  );
}
