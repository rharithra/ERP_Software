import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";
import { ApiRequestError, followUpApi, type FollowUp, type FollowUpType } from "@/lib/api";
import { FOLLOW_UP_TYPES, followUpTypeLabel, formatClock, todayIso } from "@/lib/pipeline";

export function FollowUpsPage() {
  const [items, setItems] = useState<FollowUp[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState<FollowUp | null>(null);
  const [dueDate, setDueDate] = useState("");
  const [dueTime, setDueTime] = useState("");
  const [type, setType] = useState<FollowUpType>("CALL");
  const [notes, setNotes] = useState("");

  async function load() {
    setLoading(true);
    setError(null);
    try {
      setItems(await followUpApi.list());
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "Unable to load follow-ups");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  const today = todayIso();
  const groups = useMemo(() => {
    const pending = items.filter((item) => item.status === "PENDING");
    return {
      today: pending.filter((item) => item.dueDate === today),
      overdue: pending.filter((item) => item.dueDate < today),
      upcoming: pending.filter((item) => item.dueDate > today),
      completed: items.filter((item) => item.status === "COMPLETED"),
    };
  }, [items, today]);

  async function complete(item: FollowUp) {
    try {
      await followUpApi.complete(item.id, "Completed from follow-up board");
      toast.success("Follow-up completed");
      await load();
    } catch (err) {
      toast.error(err instanceof ApiRequestError ? err.message : "Unable to complete follow-up");
    }
  }

  async function cancel(item: FollowUp) {
    try {
      await followUpApi.cancel(item.id, "Cancelled");
      toast.success("Follow-up cancelled");
      await load();
    } catch (err) {
      toast.error(err instanceof ApiRequestError ? err.message : "Unable to cancel follow-up");
    }
  }

  function openEdit(item: FollowUp) {
    setEditing(item);
    setDueDate(item.dueDate);
    setDueTime(item.dueTime ? item.dueTime.slice(0, 5) : "");
    setType(item.type);
    setNotes(item.notes ?? "");
  }

  async function saveEdit() {
    if (!editing) return;
    try {
      await followUpApi.update(editing.id, {
        leadId: editing.leadId,
        customerId: editing.customerId,
        type,
        dueDate,
        dueTime: dueTime ? `${dueTime}:00` : null,
        notes: notes.trim() || null,
      });
      toast.success("Follow-up rescheduled");
      setEditing(null);
      await load();
    } catch (err) {
      toast.error(err instanceof ApiRequestError ? err.message : "Unable to update follow-up");
    }
  }

  function Section({ title, rows, empty }: { title: string; rows: FollowUp[]; empty: string }) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>{title}</CardTitle>
          <CardDescription>{rows.length} item{rows.length === 1 ? "" : "s"}</CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          {rows.length === 0 ? (
            <p className="rounded-lg border border-dashed p-6 text-center text-sm text-muted-foreground">{empty}</p>
          ) : (
            rows.map((item) => (
              <div key={item.id} className="flex flex-col gap-3 rounded-lg border p-4 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <p className="text-sm text-muted-foreground">
                    {formatClock(item.dueTime) || "No time"} · {followUpTypeLabel(item.type)}
                  </p>
                  <p className="font-medium">
                    <Link className="hover:underline" to={`/app/leads/${item.leadId}`}>
                      {item.leadName}
                    </Link>
                  </p>
                  <p className="text-sm text-muted-foreground">{item.notes ?? "No notes"}</p>
                </div>
                {item.status === "PENDING" ? (
                  <div className="flex flex-wrap gap-2">
                    <Button size="sm" onClick={() => void complete(item)}>
                      Complete
                    </Button>
                    <Button size="sm" variant="outline" onClick={() => openEdit(item)}>
                      Reschedule
                    </Button>
                    <Button size="sm" variant="ghost" onClick={() => void cancel(item)}>
                      Cancel
                    </Button>
                  </div>
                ) : null}
              </div>
            ))
          )}
        </CardContent>
      </Card>
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Follow-ups</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Today, overdue, and upcoming calls. This is a working list, not a calendar product.
        </p>
      </div>
      {loading ? (
        <Skeleton className="h-40 w-full" />
      ) : error ? (
        <p className="text-sm text-destructive">{error}</p>
      ) : (
        <>
          <Section title="Today's follow-ups" rows={groups.today} empty="Nothing scheduled for today." />
          <Section title="Overdue" rows={groups.overdue} empty="No overdue follow-ups." />
          <Section title="Upcoming" rows={groups.upcoming} empty="No upcoming follow-ups." />
          <Section title="Completed" rows={groups.completed} empty="Completed follow-ups will appear here." />
        </>
      )}

      <Dialog open={editing != null} onOpenChange={(open) => !open && setEditing(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Reschedule follow-up</DialogTitle>
            <DialogDescription>{editing?.leadName}</DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            <div className="space-y-2">
              <Label>Type</Label>
              <NativeSelect value={type} onChange={(e) => setType(e.target.value as FollowUpType)}>
                {FOLLOW_UP_TYPES.map((item) => (
                  <option key={item.value} value={item.value}>
                    {item.label}
                  </option>
                ))}
              </NativeSelect>
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              <div className="space-y-2">
                <Label>Due date</Label>
                <Input type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} />
              </div>
              <div className="space-y-2">
                <Label>Due time</Label>
                <Input type="time" value={dueTime} onChange={(e) => setDueTime(e.target.value)} />
              </div>
            </div>
            <div className="space-y-2">
              <Label>Notes</Label>
              <Textarea value={notes} onChange={(e) => setNotes(e.target.value)} />
            </div>
            <div className="flex justify-end gap-2">
              <Button variant="outline" onClick={() => setEditing(null)}>
                Close
              </Button>
              <Button onClick={() => void saveEdit()}>Save</Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}
