import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ThemeProvider } from "next-themes";
import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { AuthProvider } from "@/auth/auth-context";
import { AppShell } from "@/components/layout/app-shell";
import { GuestRoute } from "@/components/layout/guest-route";
import { ProtectedRoute } from "@/components/layout/protected-route";
import { Toaster } from "@/components/ui/sonner";
import { ComingSoonPage } from "@/pages/coming-soon-page";
import { CompanyPage } from "@/pages/company-page";
import { DashboardPage } from "@/pages/dashboard-page";
import { LandingPage } from "@/pages/landing-page";
import { LoginPage } from "@/pages/login-page";
import { SignupPage } from "@/pages/signup-page";

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
                  <Route path="sales" element={<ComingSoonPage />} />
                  <Route path="products" element={<ComingSoonPage />} />
                  <Route path="inventory" element={<ComingSoonPage />} />
                  <Route path="customers" element={<ComingSoonPage />} />
                  <Route path="suppliers" element={<ComingSoonPage />} />
                  <Route path="purchases" element={<ComingSoonPage />} />
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
