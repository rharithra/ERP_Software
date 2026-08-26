import { useEffect, useState } from "react";
import { IndianRupee, Package, ShoppingCart, TriangleAlert } from "lucide-react";
import { Link } from "react-router-dom";
import { useAuth } from "@/auth/auth-context";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { inventoryApi } from "@/lib/api";

export function DashboardPage() {
  const { user } = useAuth();
  const [lowStock, setLowStock] = useState<number | null>(null);

  useEffect(() => {
    inventoryApi
      .summary()
      .then((summary) => setLowStock(summary.lowStock))
      .catch(() => setLowStock(null));
  }, []);

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Dashboard</h1>
        <p className="mt-1 max-w-2xl text-sm text-muted-foreground">
          {user?.tenant.name} is ready. Sales totals stay empty until POS exists — we will not invent bill numbers.
        </p>
      </div>
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Card>
          <CardHeader className="flex flex-row items-start justify-between space-y-0">
            <div>
              <CardDescription>Today's revenue</CardDescription>
              <CardTitle className="mt-2 text-3xl font-semibold">—</CardTitle>
            </div>
            <IndianRupee className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <p className="text-sm text-muted-foreground">INR totals appear after the sales module records bills.</p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="flex flex-row items-start justify-between space-y-0">
            <div>
              <CardDescription>Today's sales</CardDescription>
              <CardTitle className="mt-2 text-3xl font-semibold">—</CardTitle>
            </div>
            <ShoppingCart className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <p className="text-sm text-muted-foreground">Invoice count will land here once POS is live.</p>
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
            <p className="text-sm text-muted-foreground">Categories, products, and stock balances are live master data.</p>
          </CardContent>
        </Card>
      </div>
      <Card>
        <CardHeader>
          <CardTitle>Next setup step</CardTitle>
          <CardDescription>Record opening stock so the shop floor matches RetailFlow.</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="flex flex-col gap-2 sm:flex-row sm:gap-6">
            <Link className="text-sm font-medium text-primary" to="/app/inventory">
              Open inventory
            </Link>
            <Link className="text-sm font-medium text-primary" to="/app/products">
              Open products
            </Link>
            <Link className="text-sm font-medium text-primary" to="/app/company">
              Open company profile
            </Link>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
