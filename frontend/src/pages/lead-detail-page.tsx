import { useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";
import {
  ApiRequestError,
  followUpApi,
  leadApi,
  type FollowUpType,
  type Lead,
  type LeadStatus,
  type PipelineActivity,
} from "@/lib/api";
import {
  FOLLOW_UP_TYPES,
  formatDay,
  leadSourceLabel,
  leadStatusLabel,
  nextLeadStatuses,
  priorityLabel,
  todayIso,
} from "@/lib/pipeline";
import { inr } from "@/lib/sale-math";

export function LeadDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [lead, setLead] = useState<Lead | null>(null);
  const [timeline, setTimeline] = useState<PipelineActivity[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [lostReason, setLostReason] = useState("");
  const [followType, setFollowType] = useState<FollowUpType>("CALL");
  const [followDate, setFollowDate] = useState(todayIso());
  const [followTime, setFollowTime] = useState("10:00");
  const [followNotes, setFollowNotes] = useState("");
  const [working, setWorking] = useState(false);

  async function load() {
    if (!id) return;
    setLoading(true);
    setError(null);
    try {
      const [record, activities] = await Promise.all([leadApi.get(id), leadApi.timeline(id)]);
      setLead(record);
      setTimeline(activities);
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "Unable to load lead");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  async function convert() {
    if (!id) return;
    setWorking(true);
    try {
      const customer = await leadApi.convertCustomer(id);
      toast.success(`Customer ${customer.name} linked`);
      await load();
    } catch (err) {
      toast.error(err instanceof ApiRequestError ? err.message : "Unable to convert lead");
    } finally {
      setWorking(false);
    }
  }

  async function changeStatus(status: LeadStatus) {
    if (!id) return;
    setWorking(true);
    try {
      await leadApi.changeStatus(id, status, status === "LOST" ? lostReason : null);
      toast.success(`Lead marked ${leadStatusLabel(status)}`);
      await load();
    } catch (err) {
      toast.error(err instanceof ApiRequestError ? err.message : "Invalid status change");
    } finally {
      setWorking(false);
    }
  }

  async function scheduleFollowUp(event: FormEvent) {
    event.preventDefault();
    if (!id) return;
    setWorking(true);
    try {
      await followUpApi.create({
        leadId: id,
        type: followType,
        dueDate: followDate,
        dueTime: followTime ? `${followTime}:00` : null,
        notes: followNotes.trim() || null,
      });
      toast.success("Follow-up scheduled");
      setFollowNotes("");
      await load();
    } catch (err) {
      toast.error(err instanceof ApiRequestError ? err.message : "Unable to schedule follow-up");
    } finally {
      setWorking(false);
    }
  }

  if (loading) {
    return (
      <div className="space-y-3">
        <Skeleton className="h-10 w-64" />
        <Skeleton className="h-48 w-full" />
      </div>
    );
  }

  if (error || !lead) {
    return (
      <div className="space-y-4">
        <p className="text-sm text-destructive">{error ?? "Lead not found"}</p>
        <Button variant="outline" onClick={() => navigate("/app/leads")}>
          Back to leads
        </Button>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <p className="font-mono text-sm text-muted-foreground">{lead.leadNumber}</p>
          <h1 className="text-2xl font-semibold tracking-tight">{lead.name}</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            {leadStatusLabel(lead.status)} · {priorityLabel(lead.priority)} · {leadSourceLabel(lead.source)}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button variant="outline" asChild>
            <Link to="/app/leads">Back</Link>
          </Button>
          {!lead.convertedCustomerId ? (
            <Button onClick={() => void convert()} disabled={working}>
              Convert to customer
            </Button>
          ) : (
            <Button variant="outline" asChild>
              <Link to="/app/customers">Customer: {lead.convertedCustomerName}</Link>
            </Button>
          )}
          {lead.convertedCustomerId ? (
            <Button asChild>
              <Link to={`/app/quotations/new?leadId=${lead.id}&customerId=${lead.convertedCustomerId}`}>Create quotation</Link>
            </Button>
          ) : null}
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Lead information</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-3 text-sm sm:grid-cols-2">
            <div>
              <p className="text-muted-foreground">Phone</p>
              <p>{lead.phone ?? "—"}</p>
            </div>
            <div>
              <p className="text-muted-foreground">Email</p>
              <p>{lead.email ?? "—"}</p>
            </div>
            <div>
              <p className="text-muted-foreground">Company</p>
              <p>{lead.companyName ?? "—"}</p>
            </div>
            <div>
              <p className="text-muted-foreground">Assigned</p>
              <p>{lead.assignedToName ?? "Unassigned"}</p>
            </div>
            <div>
              <p className="text-muted-foreground">Expected value</p>
              <p>{lead.expectedValue != null ? inr(lead.expectedValue) : "—"}</p>
            </div>
            <div>
              <p className="text-muted-foreground">Expected close</p>
              <p>{lead.expectedCloseDate ?? "—"}</p>
            </div>
            <div className="sm:col-span-2">
              <p className="text-muted-foreground">Requirement</p>
              <p>{lead.requirement ?? "—"}</p>
            </div>
            {lead.lostReason ? (
              <div className="sm:col-span-2">
                <p className="text-muted-foreground">Lost reason</p>
                <p>{lead.lostReason}</p>
              </div>
            ) : null}
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>Status</CardTitle>
            <CardDescription>Closed leads cannot reopen.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            {nextLeadStatuses(lead.status).includes("LOST") ? (
              <div className="space-y-2">
                <Label htmlFor="lost">Lost reason</Label>
                <Textarea id="lost" value={lostReason} onChange={(e) => setLostReason(e.target.value)} />
              </div>
            ) : null}
            <div className="flex flex-wrap gap-2">
              {nextLeadStatuses(lead.status).map((status) => (
                <Button key={status} size="sm" variant={status === "LOST" ? "outline" : "default"} disabled={working} onClick={() => void changeStatus(status)}>
                  {leadStatusLabel(status)}
                </Button>
              ))}
            </div>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Schedule a follow-up</CardTitle>
        </CardHeader>
        <CardContent>
          <form className="grid gap-3 md:grid-cols-[8rem_10rem_8rem_1fr_auto]" onSubmit={scheduleFollowUp}>
            <NativeSelect value={followType} onChange={(e) => setFollowType(e.target.value as FollowUpType)} aria-label="Follow-up type">
              {FOLLOW_UP_TYPES.map((item) => (
                <option key={item.value} value={item.value}>
                  {item.label}
                </option>
              ))}
            </NativeSelect>
            <Input type="date" value={followDate} onChange={(e) => setFollowDate(e.target.value)} aria-label="Due date" />
            <Input type="time" value={followTime} onChange={(e) => setFollowTime(e.target.value)} aria-label="Due time" />
            <Input placeholder="Discuss quotation" value={followNotes} onChange={(e) => setFollowNotes(e.target.value)} />
            <Button type="submit" disabled={working}>
              Schedule
            </Button>
          </form>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Timeline</CardTitle>
          <CardDescription>Append-only activity for this lead.</CardDescription>
        </CardHeader>
        <CardContent>
          {timeline.length === 0 ? (
            <p className="text-sm text-muted-foreground">No activity recorded yet.</p>
          ) : (
            <ol className="space-y-4">
              {timeline.map((item) => (
                <li key={item.id} className="border-l-2 border-primary/30 pl-4">
                  <p className="text-xs text-muted-foreground">{formatDay(item.createdAt)}</p>
                  <p className="text-sm font-medium">{item.activityType.replaceAll("_", " ")}</p>
                  <p className="text-sm text-muted-foreground">{item.message}</p>
                </li>
              ))}
            </ol>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
