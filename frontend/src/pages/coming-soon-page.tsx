import { useLocation } from "react-router-dom";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

const COPY: Record<string, { title: string; body: string }> = {
  "/app/reports": {
    title: "Reports",
    body: "GST summaries and P&L need real transactional data. This page stays empty until then.",
  },
  "/app/expenses": {
    title: "Expenses",
    body: "Shop expenses and cash-book entries are not part of Milestone 1.",
  },
  "/app/employees": {
    title: "Employees",
    body: "Invite managers and cashiers once membership management ships.",
  },
  "/app/settings": {
    title: "Settings",
    body: "Tax templates, invoice series, and store preferences will extend the company profile.",
  },
};

export function ComingSoonPage() {
  const { pathname } = useLocation();
  const copy = COPY[pathname] ?? {
    title: "Coming soon",
    body: "This module is intentionally incomplete. RetailFlow ships working slices, not placeholder CRUD.",
  };

  return (
    <div className="mx-auto max-w-2xl">
      <Card>
        <CardHeader>
          <p className="text-xs font-medium uppercase tracking-wide text-primary">Later milestone</p>
          <CardTitle>{copy.title}</CardTitle>
          <CardDescription>{copy.body}</CardDescription>
        </CardHeader>
        <CardContent className="text-sm text-muted-foreground">
          Catalog, inventory, purchases, customers, and POS are live. Remaining modules stay empty until their
          milestone — no fake CRUD.
        </CardContent>
      </Card>
    </div>
  );
}
