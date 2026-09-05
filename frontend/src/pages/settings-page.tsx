import { useEffect, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { toast } from "sonner";
import { useAuth } from "@/auth/auth-context";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";
import { Skeleton } from "@/components/ui/skeleton";
import { ApiRequestError, tenantApi, type CompanyProfile } from "@/lib/api";
import {
  BUSINESS_TYPES,
  SALES_MODES,
  recommendedSalesMode,
  salesExperienceSummary,
  type BusinessType,
  type SalesMode,
} from "@/lib/sales-experience";

export function SettingsPage() {
  const { user, refreshUser } = useAuth();
  const canEdit = user?.role === "OWNER";
  const [profile, setProfile] = useState<CompanyProfile | null>(null);
  const [businessType, setBusinessType] = useState<BusinessType>("OTHER");
  const [salesMode, setSalesMode] = useState<SalesMode>("HYBRID");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirmOpen, setConfirmOpen] = useState(false);

  useEffect(() => {
    tenantApi
      .get()
      .then((data) => {
        setProfile(data);
        setBusinessType(data.businessType);
        setSalesMode(data.salesMode);
      })
      .catch((err: unknown) => {
        setError(err instanceof ApiRequestError ? err.message : "Unable to load settings");
      })
      .finally(() => setLoading(false));
  }, []);

  const recommended = recommendedSalesMode(businessType);
  const modeChanged = profile != null && salesMode !== profile.salesMode;

  async function save() {
    if (!profile) return;
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
        businessType,
        salesMode,
      });
      setProfile(saved);
      setBusinessType(saved.businessType);
      setSalesMode(saved.salesMode);
      await refreshUser();
      setConfirmOpen(false);
      toast.success("Business profile saved");
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "Unable to save settings");
    } finally {
      setSaving(false);
    }
  }

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (!canEdit) return;
    if (modeChanged) {
      setConfirmOpen(true);
      return;
    }
    void save();
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
    return <p className="text-sm text-destructive">{error ?? "Settings are unavailable."}</p>;
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Settings</h1>
        <p className="mt-1 text-sm text-muted-foreground">{salesExperienceSummary(profile.salesMode)}</p>
      </div>
      <Card>
        <CardHeader>
          <CardTitle>Business profile</CardTitle>
          <CardDescription>
            Business type describes what you sell. Sales experience describes how you sell. Both feed the same POS,
            invoice, and inventory engine — this only changes navigation.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form className="space-y-4" onSubmit={onSubmit}>
            <div className="space-y-2">
              <Label htmlFor="settings-business-type">Business type</Label>
              <NativeSelect
                id="settings-business-type"
                aria-label="Business type"
                disabled={!canEdit}
                value={businessType}
                onChange={(e) => setBusinessType(e.target.value as BusinessType)}
              >
                {BUSINESS_TYPES.map((item) => (
                  <option key={item.value} value={item.value}>
                    {item.label}
                  </option>
                ))}
              </NativeSelect>
            </div>
            <div className="space-y-2">
              <Label htmlFor="settings-sales-mode">Sales experience</Label>
              <NativeSelect
                id="settings-sales-mode"
                aria-label="Sales experience"
                disabled={!canEdit}
                value={salesMode}
                onChange={(e) => setSalesMode(e.target.value as SalesMode)}
              >
                {SALES_MODES.map((item) => (
                  <option key={item.value} value={item.value}>
                    {item.label}
                  </option>
                ))}
              </NativeSelect>
              <p className="text-xs text-muted-foreground">Recommended mode: {SALES_MODES.find((item) => item.value === recommended)?.label}</p>
            </div>
            {error ? <p className="text-sm text-destructive">{error}</p> : null}
            {canEdit ? (
              <Button type="submit" disabled={saving}>
                {saving ? "Saving..." : "Save business profile"}
              </Button>
            ) : (
              <p className="text-sm text-muted-foreground">Only the owner can change business type or sales experience.</p>
            )}
          </form>
        </CardContent>
      </Card>

      {canEdit ? (
        <Card>
          <CardHeader>
            <CardTitle>Users & Roles</CardTitle>
            <CardDescription>Invite is not used. Create managers and cashiers with a temporary password.</CardDescription>
          </CardHeader>
          <CardContent>
            <Button asChild>
              <Link to="/app/settings/users">Manage users</Link>
            </Button>
          </CardContent>
        </Card>
      ) : null}

      <Dialog open={confirmOpen} onOpenChange={setConfirmOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Change sales experience?</DialogTitle>
            <DialogDescription>
              Changing your sales experience will change which sales workflows are highlighted in your navigation. Your
              existing sales, invoices, customers and inventory will not be deleted.
            </DialogDescription>
          </DialogHeader>
          <div className="flex justify-end gap-2">
            <Button variant="outline" onClick={() => setConfirmOpen(false)}>
              Keep current
            </Button>
            <Button onClick={() => void save()} disabled={saving}>
              Change sales experience
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}
