import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { toast } from "sonner";
import { useAuth } from "@/auth/auth-context";
import { ApiRequestError } from "@/lib/api";
import {
  BUSINESS_TYPES,
  SALES_MODES,
  recommendedSalesMode,
  type BusinessType,
  type SalesMode,
} from "@/lib/sales-experience";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";

export function SignupPage() {
  const { signup } = useAuth();
  const navigate = useNavigate();
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [companyName, setCompanyName] = useState("");
  const [businessType, setBusinessType] = useState<BusinessType>("GROCERY_SUPERMARKET");
  const [salesMode, setSalesMode] = useState<SalesMode>(recommendedSalesMode("GROCERY_SUPERMARKET"));
  const [modeTouched, setModeTouched] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const recommended = recommendedSalesMode(businessType);

  function changeBusinessType(next: BusinessType) {
    setBusinessType(next);
    if (!modeTouched) {
      setSalesMode(recommendedSalesMode(next));
    }
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    if (password.length < 8) {
      setError("Password must be at least 8 characters.");
      return;
    }
    setSubmitting(true);
    try {
      await signup({ fullName, email, password, companyName, businessType, salesMode });
      toast.success("Company created. You are the owner.");
      navigate("/app");
    } catch (err) {
      const message = err instanceof ApiRequestError ? err.message : "Unable to create the company";
      setError(message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-background px-4 py-10">
      <Card className="w-full max-w-lg">
        <CardHeader>
          <p className="text-sm font-medium text-primary">RetailFlow</p>
          <CardTitle>Create your company</CardTitle>
          <CardDescription>
            This registers you as OWNER, creates the tenant, and signs you into the ERP shell.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form className="space-y-4" onSubmit={onSubmit}>
            <div className="space-y-2">
              <Label htmlFor="companyName">Company name</Label>
              <Input
                id="companyName"
                required
                minLength={2}
                placeholder="Sharma Kirana Store"
                value={companyName}
                onChange={(e) => setCompanyName(e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="fullName">Your name</Label>
              <Input
                id="fullName"
                required
                minLength={2}
                placeholder="Ananya Sharma"
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="email">Work email</Label>
              <Input
                id="email"
                type="email"
                autoComplete="email"
                required
                placeholder="ananya@sharmastore.in"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="password">Password</Label>
              <Input
                id="password"
                type="password"
                autoComplete="new-password"
                required
                minLength={8}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
              <p className="text-xs text-muted-foreground">At least 8 characters. Stored as a BCrypt hash, never in plain text.</p>
            </div>

            <div className="space-y-3 rounded-lg border p-4">
              <div>
                <p className="text-sm font-medium">Tell us about your business</p>
                <p className="text-xs text-muted-foreground">
                  Business type is what you sell. Sales experience is how you sell. You can change this later in Settings.
                </p>
              </div>
              <div className="space-y-2">
                <Label htmlFor="businessType">Business type</Label>
                <NativeSelect
                  id="businessType"
                  aria-label="Business type"
                  value={businessType}
                  onChange={(e) => changeBusinessType(e.target.value as BusinessType)}
                >
                  {BUSINESS_TYPES.map((item) => (
                    <option key={item.value} value={item.value}>
                      {item.label}
                    </option>
                  ))}
                </NativeSelect>
              </div>
              <fieldset className="space-y-2">
                <legend className="text-sm font-medium">How do you normally sell?</legend>
                <p className="text-xs text-muted-foreground">
                  Recommended for your business: {SALES_MODES.find((item) => item.value === recommended)?.label}
                </p>
                {SALES_MODES.map((item) => (
                  <label key={item.value} className="flex cursor-pointer gap-3 rounded-md border p-3">
                    <input
                      type="radio"
                      name="salesMode"
                      value={item.value}
                      checked={salesMode === item.value}
                      onChange={() => {
                        setSalesMode(item.value);
                        setModeTouched(true);
                      }}
                      className="mt-1"
                    />
                    <span>
                      <span className="flex items-center gap-2 text-sm font-medium">
                        {item.label}
                        {item.value === recommended ? (
                          <span className="rounded bg-primary/10 px-1.5 py-0.5 text-[10px] uppercase tracking-wide text-primary">
                            Recommended
                          </span>
                        ) : null}
                      </span>
                      <span className="block text-xs text-muted-foreground">{item.description}</span>
                    </span>
                  </label>
                ))}
              </fieldset>
            </div>

            {error ? <p className="text-sm text-destructive">{error}</p> : null}
            <Button className="w-full" type="submit" disabled={submitting}>
              {submitting ? "Creating company..." : "Create company and continue"}
            </Button>
          </form>
          <p className="mt-4 text-center text-sm text-muted-foreground">
            Already registered?{" "}
            <Link className="font-medium text-primary" to="/login">
              Sign in
            </Link>
          </p>
        </CardContent>
      </Card>
    </div>
  );
}
