import { useEffect, useState } from "react";
import { IndianRupee, Package, ShoppingCart, TriangleAlert } from "lucide-react";
import { Link } from "react-router-dom";
import { useAuth } from "@/auth/auth-context";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { inventoryApi, saleApi, type Sale, type SaleDashboard } from "@/lib/api";
import { inr } from "@/lib/sale-math";

export function DashboardPage() {
  const { user } = useAuth();
  const [lowStock, setLowStock] = useState<number | null>(null);
  const [sales, setSales] = useState<SaleDashboard | null>(null);

  useEffect(() => {
    inventoryApi
      .summary()
      .then((summary) => setLowStock(summary.lowStock))
      .catch(() => setLowStock(null));
    saleApi
      .dashboard()
      .then(setSales)
      .catch(() => setSales(null));
  }, []);

  const recent: Sale[] = sales?.recentSales ?? [];

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Dashboard</h1>
        <p className="mt-1 max-w-2xl text-sm text-muted-foreground">
          {user?.tenant.name} — today&apos;s completed bills from the live sales ledger.{" "}
          {user?.tenant.salesMode === "QUICK_SALE"
            ? "RetailFlow is configured for Quick Sale."
            : user?.tenant.salesMode === "PIPELINE"
              ? "RetailFlow is configured for Sales Pipeline."
              : "RetailFlow is configured for Hybrid Sales."}
        </p>
      </div>
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Card>
          <CardHeader className="flex flex-row items-start justify-between space-y-0">
            <div>
              <CardDescription>Today&apos;s revenue</CardDescription>
              <CardTitle className="mt-2 text-3xl font-semibold">
                {sales ? inr(sales.todayRevenue) : "—"}
              </CardTitle>
            </div>
            <IndianRupee className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <p className="text-sm text-muted-foreground">Grand total of completed sales dated today.</p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="flex flex-row items-start justify-between space-y-0">
            <div>
              <CardDescription>Today&apos;s orders</CardDescription>
              <CardTitle className="mt-2 text-3xl font-semibold">{sales ? sales.todayOrders : "—"}</CardTitle>
            </div>
            <ShoppingCart className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <p className="text-sm text-muted-foreground">Completed invoices for this store today.</p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="flex flex-row items-start justify-between space-y-0">
            <div>
              <CardDescription>Low stock</CardDescription>
              <CardTitle className="mt-2 text-3xl font-semibold">{lowStock === null ? "—" : lowStock}</CardTitle>
            </div>
            <TriangleAlert className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <p className="text-sm text-muted-foreground">Products at or below their reorder level.</p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="flex flex-row items-start justify-between space-y-0">
            <div>
              <CardDescription>Catalog</CardDescription>
              <CardTitle className="mt-2 text-3xl font-semibold">Live</CardTitle>
            </div>
            <Package className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <p className="text-sm text-muted-foreground">Categories, products, stock, purchases, and POS are live.</p>
          </CardContent>
        </Card>
      </div>
      <Card>
        <CardHeader>
          <CardTitle>Recent sales</CardTitle>
          <CardDescription>Latest completed bills. Open POS to record the next one.</CardDescription>
        </CardHeader>
        <CardContent>
          {recent.length === 0 ? (
            <p className="text-sm text-muted-foreground">No completed sales yet today or earlier.</p>
          ) : (
            <ul className="space-y-3 text-sm">
              {recent.map((sale) => (
                <li key={sale.id} className="flex items-center justify-between gap-3">
                  <Link className="font-medium text-primary" to={`/app/sales/${sale.id}`}>
                    {sale.saleNumber} · {sale.customerName}
                  </Link>
                  <span>{inr(sale.grandTotal)}</span>
                </li>
              ))}
            </ul>
          )}
          <div className="mt-4 flex flex-col gap-2 sm:flex-row sm:gap-6">
            {user?.tenant.salesMode !== "PIPELINE" ? (
              <Link className="text-sm font-medium text-primary" to="/app/sales/new">
                Open POS
              </Link>
            ) : null}
            {user?.tenant.salesMode !== "QUICK_SALE" ? (
              <Link className="text-sm font-medium text-primary" to="/app/pipeline">
                Sales pipeline
              </Link>
            ) : null}
            <Link className="text-sm font-medium text-primary" to="/app/sales">
              Sales history
            </Link>
            <Link className="text-sm font-medium text-primary" to="/app/inventory">
              Open inventory
            </Link>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
