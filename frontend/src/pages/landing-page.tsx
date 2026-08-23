import { Link } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

export function LandingPage() {
  return (
    <div className="min-h-screen bg-background">
      <header className="mx-auto flex w-full max-w-6xl items-center justify-between px-6 py-6">
        <div>
          <p className="text-lg font-semibold tracking-tight">RetailFlow</p>
          <p className="text-sm text-muted-foreground">ERP for Indian retail shops</p>
        </div>
        <div className="flex gap-2">
          <Button variant="ghost" asChild>
            <Link to="/login">Sign in</Link>
          </Button>
          <Button asChild>
            <Link to="/signup">Create company</Link>
          </Button>
        </div>
      </header>
      <main className="mx-auto grid w-full max-w-6xl gap-10 px-6 pb-20 pt-8 lg:grid-cols-[1.1fr_0.9fr] lg:items-center">
        <div className="space-y-6">
          <p className="text-sm font-medium text-primary">Milestone 1 is live</p>
          <h1 className="max-w-xl text-4xl font-semibold tracking-tight sm:text-5xl">
            Open your shop ledger without sharing it with the shop next door.
          </h1>
          <p className="max-w-xl text-base leading-7 text-muted-foreground">
            RetailFlow is a multi-tenant ERP for kirana, garments, mobiles, and general stores.
            Create a company, become the owner, and work from a GST-ready workspace. Sales,
            inventory, and billing modules follow once this foundation is in production.
          </p>
          <div className="flex flex-wrap gap-3">
            <Button size="lg" asChild>
              <Link to="/signup">Start with your company</Link>
            </Button>
            <Button size="lg" variant="outline" asChild>
              <Link to="/login">Sign in to an existing store</Link>
            </Button>
          </div>
        </div>
        <Card>
          <CardHeader>
            <CardTitle>What this release includes</CardTitle>
            <CardDescription>A complete, testable onboarding slice — not a mock dashboard.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4 text-sm leading-6 text-muted-foreground">
            <p>Self-signup creates the company, owner user, and hashed credentials in one transaction.</p>
            <p>JWT login establishes tenant context from the authenticated membership, never from a client-supplied tenant id.</p>
            <p>PostgreSQL row-level security and application filters both enforce isolation.</p>
            <p>The ERP shell is ready: dashboard, company profile, light/dark theme, and coming-soon modules.</p>
          </CardContent>
        </Card>
      </main>
    </div>
  );
}
