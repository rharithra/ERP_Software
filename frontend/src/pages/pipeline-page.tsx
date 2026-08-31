import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { NativeSelect } from "@/components/ui/native-select";
import { Skeleton } from "@/components/ui/skeleton";
import { ApiRequestError, leadApi, pipelineApi, type Lead, type LeadStatus, type PipelineDashboard } from "@/lib/api";
import { LEAD_STATUSES, leadStatusLabel, nextLeadStatuses, priorityLabel } from "@/lib/pipeline";
import { inr } from "@/lib/sale-math";

export function PipelinePage() {
  const [metrics, setMetrics] = useState<PipelineDashboard | null>(null);
  const [leads, setLeads] = useState<Lead[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const [dash, board] = await Promise.all([pipelineApi.dashboard(), leadApi.board()]);
      setMetrics(dash);
      setLeads(board);
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "Unable to load the sales pipeline");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  const columns = useMemo(() => {
    return LEAD_STATUSES.map((status) => ({
      status,
      items: leads.filter((lead) => lead.status === status),
    }));
  }, [leads]);

  async function move(lead: Lead, status: LeadStatus) {
    if (status === lead.status) return;
    try {
      await leadApi.changeStatus(lead.id, status);
      await load();
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "Unable to update lead status");
    }
  }

  const cards = metrics
    ? [
        { title: "New leads", value: String(metrics.newLeads) },
        { title: "Open leads", value: String(metrics.openLeads) },
        { title: "Open quotations", value: String(metrics.openQuotations) },
        { title: "Quotation value", value: inr(metrics.quotationValue) },
        { title: "Sales orders", value: String(metrics.openSalesOrders) },
        { title: "Pipeline value", value: inr(metrics.pipelineValue) },
        { title: "Won this month", value: String(metrics.wonThisMonth) },
        { title: "Lost this month", value: String(metrics.lostThisMonth) },
        { title: "Today's follow-ups", value: String(metrics.followUpsToday) },
        { title: "Overdue follow-ups", value: String(metrics.followUpsOverdue) },
        { title: "Outstanding payments", value: inr(metrics.outstandingPayments) },
      ]
    : [];

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Sales pipeline</h1>
          <p className="mt-1 max-w-2xl text-sm text-muted-foreground">
            Enquiries become quotations, then sales orders. Stock only moves when you complete a sale — the same engine
            POS uses.
          </p>
        </div>
        <Button asChild>
          <Link to="/app/leads/new">New lead</Link>
        </Button>
      </div>

      {loading ? (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <Skeleton className="h-24" />
          <Skeleton className="h-24" />
          <Skeleton className="h-24" />
          <Skeleton className="h-24" />
        </div>
      ) : error ? (
        <p className="text-sm text-destructive">{error}</p>
      ) : (
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          {cards.map((card) => (
            <Card key={card.title}>
              <CardHeader className="pb-2">
                <CardDescription>{card.title}</CardDescription>
                <CardTitle className="text-2xl">{card.value}</CardTitle>
              </CardHeader>
            </Card>
          ))}
        </div>
      )}

      <div className="-mx-4 overflow-x-auto px-4 pb-4">
        <div className="flex min-w-[64rem] gap-3">
          {columns.map((column) => (
            <div key={column.status} className="w-64 shrink-0 rounded-xl border bg-muted/30 p-3">
              <div className="mb-3 flex items-center justify-between">
                <p className="text-sm font-medium">{leadStatusLabel(column.status)}</p>
                <Badge>{column.items.length}</Badge>
              </div>
              <div className="space-y-3">
                {column.items.length === 0 ? (
                  <p className="rounded-lg border border-dashed p-4 text-xs text-muted-foreground">No leads</p>
                ) : (
                  column.items.map((lead) => (
                    <Card key={lead.id} className="shadow-none">
                      <CardHeader className="space-y-1 p-3">
                        <CardTitle className="text-sm">
                          <Link className="hover:underline" to={`/app/leads/${lead.id}`}>
                            {lead.name}
                          </Link>
                        </CardTitle>
                        <CardDescription className="line-clamp-2">{lead.requirement ?? "No requirement yet"}</CardDescription>
                      </CardHeader>
                      <CardContent className="space-y-2 p-3 pt-0 text-xs text-muted-foreground">
                        <p>{lead.expectedValue != null ? inr(lead.expectedValue) : "No expected value"}</p>
                        <p>
                          {priorityLabel(lead.priority)}
                          {lead.expectedCloseDate ? ` · close ${lead.expectedCloseDate}` : ""}
                        </p>
                        <p>{lead.assignedToName ?? "Unassigned"}</p>
                        {nextLeadStatuses(lead.status).length > 0 ? (
                          <NativeSelect
                            aria-label={`Move ${lead.name}`}
                            value={lead.status}
                            onChange={(e) => void move(lead, e.target.value as LeadStatus)}
                          >
                            <option value={lead.status}>{leadStatusLabel(lead.status)}</option>
                            {nextLeadStatuses(lead.status).map((status) => (
                              <option key={status} value={status}>
                                {leadStatusLabel(status)}
                              </option>
                            ))}
                          </NativeSelect>
                        ) : null}
                      </CardContent>
                    </Card>
                  ))
                )}
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
