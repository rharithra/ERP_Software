import { IndianRupee, Package, ShoppingCart, TriangleAlert } from "lucide-react";
import { Link } from "react-router-dom";
import { useAuth } from "@/auth/auth-context";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

const PLACEHOLDERS = [
  {
    title: "Today's revenue",
    value: "—",
    hint: "INR totals appear after the sales module records bills.",
    icon: IndianRupee,
  },
  {
    title: "Today's sales",
    value: "—",
    hint: "Invoice count will land here once POS is live.",
    icon: ShoppingCart,
  },
  {
    title: "Low stock",
    value: "—",
    hint: "Inventory alerts need product and stock ledgers first.",
    icon: TriangleAlert,
  },
  {
    title: "Catalog",
    value: "—",
    hint: "Product and category setup is scheduled for a later milestone.",
    icon: Package,
  },
];

export function DashboardPage() {
  const { user } = useAuth();

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Dashboard</h1>
        <p className="mt-1 max-w-2xl text-sm text-muted-foreground">
          {user?.tenant.name} is ready. Metrics stay empty until purchases and sales exist — we will not
          invent numbers for a client demo.
        </p>
      </div>
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {PLACEHOLDERS.map((item) => {
          const Icon = item.icon;
          return (
            <Card key={item.title}>
              <CardHeader className="flex flex-row items-start justify-between space-y-0">
                <div>
                  <CardDescription>{item.title}</CardDescription>
                  <CardTitle className="mt-2 text-3xl font-semibold">{item.value}</CardTitle>
                </div>
                <Icon className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <p className="text-sm text-muted-foreground">{item.hint}</p>
              </CardContent>
            </Card>
          );
        })}
      </div>
      <Card>
        <CardHeader>
          <CardTitle>Next setup step</CardTitle>
          <CardDescription>Complete GST and address details so invoices can pick them up later.</CardDescription>
        </CardHeader>
        <CardContent>
          <Link className="text-sm font-medium text-primary" to="/app/company">
            Open company profile
          </Link>
        </CardContent>
      </Card>
    </div>
  );
}
