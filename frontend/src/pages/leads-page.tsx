import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { Plus, Search } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { NativeSelect } from "@/components/ui/native-select";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { ApiRequestError, leadApi, type Lead, type LeadStatus } from "@/lib/api";
import { LEAD_STATUSES, leadSourceLabel, leadStatusLabel, priorityLabel } from "@/lib/pipeline";
import { inr } from "@/lib/sale-math";

export function LeadsPage() {
  const [items, setItems] = useState<Lead[]>([]);
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState<"all" | LeadStatus>("all");
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const list = await leadApi.list({
        q: query || undefined,
        status: status === "all" ? undefined : status,
        page,
        size: 20,
      });
      setItems(list.items);
      setTotalPages(Math.max(list.totalPages, 1));
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "Unable to load leads");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status, page]);

  const emptyCopy = useMemo(() => {
    if (query || status !== "all") return "No leads match these filters.";
    return "Capture walk-ins and phone enquiries here. A lead is not a customer until you convert it.";
  }, [query, status]);

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Leads</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Tenant-scoped opportunities. Convert to a customer only when you are ready to quote.
          </p>
        </div>
        <Button asChild>
          <Link to="/app/leads/new">
            <Plus className="mr-2 h-4 w-4" />
            New lead
          </Link>
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Enquiry book</CardTitle>
          <CardDescription>Numbers are allocated as LEAD-000001 from a database counter, not max+1.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <form
            className="grid gap-3 md:grid-cols-[1fr_12rem_auto]"
            onSubmit={(event) => {
              event.preventDefault();
              setPage(1);
              void load();
            }}
          >
            <div className="relative">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                className="pl-9"
                placeholder="Search name, phone, or requirement"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                aria-label="Search leads"
              />
            </div>
            <NativeSelect
              aria-label="Filter by status"
              value={status}
              onChange={(e) => {
                setStatus(e.target.value as "all" | LeadStatus);
                setPage(1);
              }}
            >
              <option value="all">All statuses</option>
              {LEAD_STATUSES.map((item) => (
                <option key={item} value={item}>
                  {leadStatusLabel(item)}
                </option>
              ))}
            </NativeSelect>
            <Button type="submit" variant="outline">
              Search
            </Button>
          </form>
          {loading ? (
            <Skeleton className="h-32 w-full" />
          ) : error ? (
            <p className="text-sm text-destructive">{error}</p>
          ) : items.length === 0 ? (
            <div className="rounded-lg border border-dashed p-8 text-center text-sm text-muted-foreground">{emptyCopy}</div>
          ) : (
            <>
              <div className="hidden md:block">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Lead</TableHead>
                      <TableHead>Source</TableHead>
                      <TableHead>Requirement</TableHead>
                      <TableHead>Value</TableHead>
                      <TableHead>Priority</TableHead>
                      <TableHead>Status</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {items.map((lead) => (
                      <TableRow key={lead.id}>
                        <TableCell>
                          <Link className="font-medium hover:underline" to={`/app/leads/${lead.id}`}>
                            {lead.name}
                          </Link>
                          <p className="font-mono text-xs text-muted-foreground">{lead.leadNumber}</p>
                        </TableCell>
                        <TableCell>{leadSourceLabel(lead.source)}</TableCell>
                        <TableCell className="max-w-xs truncate">{lead.requirement ?? "—"}</TableCell>
                        <TableCell>{lead.expectedValue != null ? inr(lead.expectedValue) : "—"}</TableCell>
                        <TableCell>{priorityLabel(lead.priority)}</TableCell>
                        <TableCell>
                          <Badge>{leadStatusLabel(lead.status)}</Badge>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
              <div className="space-y-3 md:hidden">
                {items.map((lead) => (
                  <Link key={lead.id} to={`/app/leads/${lead.id}`} className="block rounded-lg border p-4">
                    <div className="flex items-start justify-between gap-3">
                      <div>
                        <p className="font-medium">{lead.name}</p>
                        <p className="text-sm text-muted-foreground">{lead.requirement ?? "No requirement"}</p>
                      </div>
                      <Badge>{leadStatusLabel(lead.status)}</Badge>
                    </div>
                  </Link>
                ))}
              </div>
              {totalPages > 1 ? (
                <div className="flex items-center justify-end gap-2">
                  <Button variant="outline" size="sm" disabled={page <= 1} onClick={() => setPage((p) => p - 1)}>
                    Previous
                  </Button>
                  <span className="text-sm text-muted-foreground">
                    Page {page} of {totalPages}
                  </span>
                  <Button variant="outline" size="sm" disabled={page >= totalPages} onClick={() => setPage((p) => p + 1)}>
                    Next
                  </Button>
                </div>
              ) : null}
            </>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
