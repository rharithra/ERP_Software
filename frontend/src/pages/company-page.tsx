import { useEffect, useState, type FormEvent } from "react";
import { toast } from "sonner";
import { tenantApi, ApiRequestError, type CompanyProfile } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";

export function CompanyPage() {
  const [profile, setProfile] = useState<CompanyProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    tenantApi
      .get()
      .then(setProfile)
      .catch((err: unknown) => {
        setError(err instanceof ApiRequestError ? err.message : "Unable to load company profile");
      })
      .finally(() => setLoading(false));
  }, []);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!profile) {
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const saved = await tenantApi.update({
        name: profile.name,
        legalName: profile.legalName,
        gstin: profile.gstin,
        phone: profile.phone,
        email: profile.email,
        addressLine1: profile.addressLine1,
        addressLine2: profile.addressLine2,
        city: profile.city,
        state: profile.state,
        pincode: profile.pincode,
      });
      setProfile(saved);
      toast.success("Company profile saved");
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "Unable to save company profile");
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return (
      <div className="space-y-3">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }

  if (!profile) {
    return <p className="text-sm text-destructive">{error ?? "Company profile is unavailable."}</p>;
  }

  function field(key: keyof CompanyProfile, value: string) {
    setProfile((current) => (current ? { ...current, [key]: value } : current));
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Company profile</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          These details belong only to {profile.name}. Currency stays INR for this release.
        </p>
      </div>
      <Card>
        <CardHeader>
          <CardTitle>GST-ready store identity</CardTitle>
          <CardDescription>Owners and managers can update this. Cashiers can view it.</CardDescription>
        </CardHeader>
        <CardContent>
          <form className="grid gap-4 sm:grid-cols-2" onSubmit={onSubmit}>
            <div className="space-y-2 sm:col-span-2">
              <Label htmlFor="name">Display name</Label>
              <Input id="name" required value={profile.name} onChange={(e) => field("name", e.target.value)} />
            </div>
            <div className="space-y-2 sm:col-span-2">
              <Label htmlFor="legalName">Legal name</Label>
              <Input id="legalName" value={profile.legalName ?? ""} onChange={(e) => field("legalName", e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="gstin">GSTIN</Label>
              <Input id="gstin" value={profile.gstin ?? ""} onChange={(e) => field("gstin", e.target.value.toUpperCase())} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="phone">Phone</Label>
              <Input id="phone" value={profile.phone ?? ""} onChange={(e) => field("phone", e.target.value)} />
            </div>
            <div className="space-y-2 sm:col-span-2">
              <Label htmlFor="email">Billing email</Label>
              <Input id="email" type="email" value={profile.email ?? ""} onChange={(e) => field("email", e.target.value)} />
            </div>
            <div className="space-y-2 sm:col-span-2">
              <Label htmlFor="addressLine1">Address line 1</Label>
              <Input id="addressLine1" value={profile.addressLine1 ?? ""} onChange={(e) => field("addressLine1", e.target.value)} />
            </div>
            <div className="space-y-2 sm:col-span-2">
              <Label htmlFor="addressLine2">Address line 2</Label>
              <Input id="addressLine2" value={profile.addressLine2 ?? ""} onChange={(e) => field("addressLine2", e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="city">City</Label>
              <Input id="city" value={profile.city ?? ""} onChange={(e) => field("city", e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="state">State</Label>
              <Input id="state" value={profile.state ?? ""} onChange={(e) => field("state", e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="pincode">PIN code</Label>
              <Input id="pincode" value={profile.pincode ?? ""} onChange={(e) => field("pincode", e.target.value)} />
            </div>
            {error ? <p className="text-sm text-destructive sm:col-span-2">{error}</p> : null}
            <div className="sm:col-span-2">
              <Button type="submit" disabled={saving}>
                {saving ? "Saving..." : "Save company profile"}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
