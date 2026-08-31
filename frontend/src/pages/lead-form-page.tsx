import { useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";
import { Textarea } from "@/components/ui/textarea";
import { ApiRequestError, leadApi, tenantApi, type LeadPriority, type LeadSource, type TenantMember } from "@/lib/api";
import { LEAD_SOURCES } from "@/lib/pipeline";

export function LeadFormPage() {
  const navigate = useNavigate();
  const [members, setMembers] = useState<TenantMember[]>([]);
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [email, setEmail] = useState("");
  const [companyName, setCompanyName] = useState("");
  const [address, setAddress] = useState("");
  const [source, setSource] = useState<LeadSource>("WALK_IN");
  const [requirement, setRequirement] = useState("");
  const [expectedValue, setExpectedValue] = useState("");
  const [expectedCloseDate, setExpectedCloseDate] = useState("");
  const [assignedTo, setAssignedTo] = useState("");
  const [priority, setPriority] = useState<LeadPriority>("MEDIUM");
  const [notes, setNotes] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    tenantApi.members().then(setMembers).catch(() => setMembers([]));
  }, []);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (name.trim().length < 2) {
      setError("Name must be at least 2 characters.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const lead = await leadApi.create({
        name: name.trim(),
        phone: phone.trim() || null,
        email: email.trim() || null,
        companyName: companyName.trim() || null,
        address: address.trim() || null,
        source,
        requirement: requirement.trim() || null,
        expectedValue: expectedValue ? Number(expectedValue) : null,
        expectedCloseDate: expectedCloseDate || null,
        assignedTo: assignedTo || null,
        priority,
        notes: notes.trim() || null,
      });
      toast.success(`${lead.leadNumber} created`);
      navigate(`/app/leads/${lead.id}`);
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "Unable to create lead");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">New lead</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          This does not create a customer. Convert later if they buy — matching phone numbers reuse the existing customer.
        </p>
      </div>
      <Card>
        <CardHeader>
          <CardTitle>Enquiry</CardTitle>
          <CardDescription>Assigned users come from this store&apos;s memberships.</CardDescription>
        </CardHeader>
        <CardContent>
          <form className="space-y-4" onSubmit={onSubmit}>
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="lead-name">Name</Label>
                <Input id="lead-name" value={name} onChange={(e) => setName(e.target.value)} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="lead-phone">Phone</Label>
                <Input id="lead-phone" value={phone} onChange={(e) => setPhone(e.target.value)} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="lead-email">Email</Label>
                <Input id="lead-email" value={email} onChange={(e) => setEmail(e.target.value)} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="lead-company">Company</Label>
                <Input id="lead-company" value={companyName} onChange={(e) => setCompanyName(e.target.value)} />
              </div>
            </div>
            <div className="space-y-2">
              <Label htmlFor="lead-address">Address</Label>
              <Textarea id="lead-address" value={address} onChange={(e) => setAddress(e.target.value)} />
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="lead-source">Source</Label>
                <NativeSelect id="lead-source" value={source} onChange={(e) => setSource(e.target.value as LeadSource)}>
                  {LEAD_SOURCES.map((item) => (
                    <option key={item.value} value={item.value}>
                      {item.label}
                    </option>
                  ))}
                </NativeSelect>
              </div>
              <div className="space-y-2">
                <Label htmlFor="lead-priority">Priority</Label>
                <NativeSelect
                  id="lead-priority"
                  value={priority}
                  onChange={(e) => setPriority(e.target.value as LeadPriority)}
                >
                  <option value="LOW">Low</option>
                  <option value="MEDIUM">Medium</option>
                  <option value="HIGH">High</option>
                </NativeSelect>
              </div>
              <div className="space-y-2">
                <Label htmlFor="lead-value">Expected value (₹)</Label>
                <Input
                  id="lead-value"
                  type="number"
                  min="0"
                  value={expectedValue}
                  onChange={(e) => setExpectedValue(e.target.value)}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="lead-close">Expected close date</Label>
                <Input id="lead-close" type="date" value={expectedCloseDate} onChange={(e) => setExpectedCloseDate(e.target.value)} />
              </div>
            </div>
            <div className="space-y-2">
              <Label htmlFor="lead-assigned">Assigned to</Label>
              <NativeSelect id="lead-assigned" value={assignedTo} onChange={(e) => setAssignedTo(e.target.value)}>
                <option value="">Unassigned</option>
                {members.map((member) => (
                  <option key={member.userId} value={member.userId}>
                    {member.fullName}
                  </option>
                ))}
              </NativeSelect>
            </div>
            <div className="space-y-2">
              <Label htmlFor="lead-req">Requirement</Label>
              <Textarea id="lead-req" value={requirement} onChange={(e) => setRequirement(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="lead-notes">Notes</Label>
              <Textarea id="lead-notes" value={notes} onChange={(e) => setNotes(e.target.value)} />
            </div>
            {error ? <p className="text-sm text-destructive">{error}</p> : null}
            <div className="flex justify-end gap-2">
              <Button type="button" variant="outline" asChild>
                <Link to="/app/leads">Cancel</Link>
              </Button>
              <Button type="submit" disabled={saving}>
                Create lead
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
