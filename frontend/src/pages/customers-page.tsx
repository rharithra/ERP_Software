import { useEffect, useMemo, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { Plus, Search } from "lucide-react";
import { toast } from "sonner";
import { useAuth } from "@/auth/auth-context";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";
import { Skeleton } from "@/components/ui/skeleton";
import { Switch } from "@/components/ui/switch";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Textarea } from "@/components/ui/textarea";
import { ApiRequestError, customerApi, paymentApi, type Customer, type CustomerPayload, type CustomerSummary, type PaymentRecord } from "@/lib/api";
import { inr } from "@/lib/sale-math";

const emptyForm = {
  name: "",
  phone: "",
  email: "",
  address: "",
  gstin: "",
  notes: "",
  active: true,
};

export function CustomersPage() {
  const { user } = useAuth();
  const canMutate = user?.role !== "CASHIER";
  const [items, setItems] = useState<Customer[]>([]);
  const [summary, setSummary] = useState<CustomerSummary | null>(null);
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState<"all" | "true" | "false">("all");
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [detail, setDetail] = useState<Customer | null>(null);
  const [detailPayments, setDetailPayments] = useState<PaymentRecord[]>([]);
  const [detailOutstanding, setDetailOutstanding] = useState(0);
  const [detailPaid, setDetailPaid] = useState(0);
  const [detailSales, setDetailSales] = useState(0);
  const [editing, setEditing] = useState<Customer | null>(null);
  const [form, setForm] = useState(emptyForm);
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const activeFilter = status === "all" ? undefined : status === "true";

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const [list, counts] = await Promise.all([
        customerApi.list({ q: query || undefined, active: activeFilter, page, size: 20 }),
        customerApi.summary(),
      ]);
      setItems(list.items);
      setTotalPages(Math.max(list.totalPages, 1));
      setSummary(counts);
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "Unable to load customers");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status, page]);

  useEffect(() => {
    if (!detail) {
      setDetailPayments([]);
      return;
    }
    Promise.all([paymentApi.list({ customerId: detail.id }), paymentApi.outstanding("ALL")])
      .then(([history, rows]) => {
        const mine = rows.filter((row) => row.customerId === detail.id);
        setDetailPayments(history);
        setDetailOutstanding(mine.reduce((sum, row) => sum + Number(row.outstanding), 0));
        setDetailPaid(mine.reduce((sum, row) => sum + Number(row.paid), 0));
        setDetailSales(mine.reduce((sum, row) => sum + Number(row.grandTotal), 0));
      })
      .catch(() => {
        setDetailPayments([]);
      });
  }, [detail]);

  const emptyCopy = useMemo(() => {
    if (query || status !== "all") {
      return "No customers match these filters.";
    }
    return "Add regular buyers for faster billing. Walk-in customers do not need a record.";
  }, [query, status]);

  function openCreate() {
    setEditing(null);
    setForm(emptyForm);
    setFormError(null);
    setDialogOpen(true);
  }

  function openEdit(customer: Customer) {
    setEditing(customer);
    setForm({
      name: customer.name,
      phone: customer.phone ?? "",
      email: customer.email ?? "",
      address: customer.address ?? "",
      gstin: customer.gstin ?? "",
      notes: customer.notes ?? "",
      active: customer.active,
    });
    setFormError(null);
    setDialogOpen(true);
  }

  function setField<K extends keyof typeof emptyForm>(key: K, value: (typeof emptyForm)[K]) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (form.name.trim().length < 2) {
      setFormError("Customer name must be at least 2 characters.");
      return;
    }
    const payload: CustomerPayload = {
      name: form.name.trim(),
      phone: form.phone.trim() || null,
      email: form.email.trim() || null,
      address: form.address.trim() || null,
      gstin: form.gstin.trim() || null,
      notes: form.notes.trim() || null,
    };
    setSaving(true);
    setFormError(null);
    try {
      const saved = editing ? await customerApi.update(editing.id, payload) : await customerApi.create(payload);
      if (editing && saved.active !== form.active) {
        await customerApi.updateStatus(saved.id, form.active);
      }
      toast.success(editing ? "Customer updated" : "Customer added");
      setDialogOpen(false);
      await load();
    } catch (err) {
      setFormError(err instanceof ApiRequestError ? err.message : "Unable to save customer");
    } finally {
      setSaving(false);
    }
  }

  async function toggleStatus(customer: Customer) {
    try {
      await customerApi.updateStatus(customer.id, !customer.active);
      toast.success(customer.active ? "Customer deactivated" : "Customer activated");
      await load();
    } catch (err) {
      toast.error(err instanceof ApiRequestError ? err.message : "Unable to update status");
    }
  }

  const cards = [
    { title: "Total customers", value: summary?.totalCustomers ?? "—" },
    { title: "Active", value: summary?.activeCustomers ?? "—" },
    { title: "Inactive", value: summary?.inactiveCustomers ?? "—" },
  ];

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Customers</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Regular buyers for this store. Deactivate instead of deleting. Phone is unique when provided.
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" asChild>
            <Link to="/app/outstanding">Outstanding</Link>
          </Button>
          {canMutate ? (
            <Button onClick={openCreate}>
              <Plus className="mr-2 h-4 w-4" />
              Add customer
            </Button>
          ) : null}
        </div>
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        {cards.map((card) => (
          <Card key={card.title}>
            <CardHeader className="pb-2">
              <CardDescription>{card.title}</CardDescription>
              <CardTitle className="text-3xl">{loading ? "…" : card.value}</CardTitle>
            </CardHeader>
          </Card>
        ))}
      </div>

      <Card>
        <CardContent className="space-y-4 pt-6">
          <form
            className="grid gap-3 md:grid-cols-[1fr_10rem_auto]"
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
                placeholder="Search name, phone, or email"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                aria-label="Search customers"
              />
            </div>
            <NativeSelect
              aria-label="Filter by status"
              value={status}
              onChange={(e) => {
                setStatus(e.target.value as "all" | "true" | "false");
                setPage(1);
              }}
            >
              <option value="all">All statuses</option>
              <option value="true">Active</option>
              <option value="false">Inactive</option>
            </NativeSelect>
            <Button type="submit" variant="outline">
              Search
            </Button>
          </form>

          {loading ? (
            <div className="space-y-2">
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
            </div>
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
                      <TableHead>Customer</TableHead>
                      <TableHead>Phone</TableHead>
                      <TableHead>Email</TableHead>
                      <TableHead>GSTIN</TableHead>
                      <TableHead>Status</TableHead>
                      <TableHead className="text-right">Actions</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {items.map((customer) => (
                      <TableRow key={customer.id}>
                        <TableCell className="font-medium">{customer.name}</TableCell>
                        <TableCell>{customer.phone ?? "—"}</TableCell>
                        <TableCell>{customer.email ?? "—"}</TableCell>
                        <TableCell className="font-mono text-xs">{customer.gstin ?? "—"}</TableCell>
                        <TableCell>
                          <Badge className={customer.active ? "border-primary/30 bg-primary/10 text-primary" : ""}>
                            {customer.active ? "Active" : "Inactive"}
                          </Badge>
                        </TableCell>
                        <TableCell className="space-x-2 text-right">
                          <Button size="sm" variant="ghost" onClick={() => setDetail(customer)}>
                            View
                          </Button>
                          {canMutate ? (
                            <>
                              <Button size="sm" variant="ghost" onClick={() => openEdit(customer)}>
                                Edit
                              </Button>
                              <Button size="sm" variant="ghost" onClick={() => void toggleStatus(customer)}>
                                {customer.active ? "Deactivate" : "Activate"}
                              </Button>
                            </>
                          ) : null}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
              <div className="space-y-3 md:hidden">
                {items.map((customer) => (
                  <div key={customer.id} className="rounded-lg border p-4">
                    <div className="flex items-start justify-between gap-3">
                      <div>
                        <p className="font-medium">{customer.name}</p>
                        <p className="text-sm text-muted-foreground">{customer.phone ?? "No phone"}</p>
                      </div>
                      <Badge className={customer.active ? "border-primary/30 bg-primary/10 text-primary" : ""}>
                        {customer.active ? "Active" : "Inactive"}
                      </Badge>
                    </div>
                    <div className="mt-3 flex flex-wrap gap-2">
                      <Button size="sm" variant="outline" onClick={() => setDetail(customer)}>
                        View
                      </Button>
                      {canMutate ? (
                        <>
                          <Button size="sm" variant="outline" onClick={() => openEdit(customer)}>
                            Edit
                          </Button>
                          <Button size="sm" variant="outline" onClick={() => void toggleStatus(customer)}>
                            {customer.active ? "Deactivate" : "Activate"}
                          </Button>
                        </>
                      ) : null}
                    </div>
                  </div>
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

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>{editing ? "Edit customer" : "Add customer"}</DialogTitle>
            <DialogDescription>Name is required. Phone is unique in this store when provided.</DialogDescription>
          </DialogHeader>
          <form className="space-y-4" onSubmit={onSubmit}>
            <div className="space-y-2">
              <Label htmlFor="customer-name">Name</Label>
              <Input id="customer-name" value={form.name} onChange={(e) => setField("name", e.target.value)} />
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="customer-phone">Phone</Label>
                <Input id="customer-phone" value={form.phone} onChange={(e) => setField("phone", e.target.value)} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="customer-email">Email</Label>
                <Input id="customer-email" value={form.email} onChange={(e) => setField("email", e.target.value)} />
              </div>
            </div>
            <div className="space-y-2">
              <Label htmlFor="customer-gstin">GSTIN</Label>
              <Input id="customer-gstin" value={form.gstin} onChange={(e) => setField("gstin", e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="customer-address">Address</Label>
              <Textarea id="customer-address" value={form.address} onChange={(e) => setField("address", e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="customer-notes">Notes</Label>
              <Textarea id="customer-notes" value={form.notes} onChange={(e) => setField("notes", e.target.value)} />
            </div>
            {editing ? (
              <div className="flex items-center justify-between rounded-lg border px-3 py-2">
                <Label htmlFor="customer-active">Active</Label>
                <Switch id="customer-active" checked={form.active} onCheckedChange={(value) => setField("active", value)} />
              </div>
            ) : null}
            {formError ? <p className="text-sm text-destructive">{formError}</p> : null}
            <div className="flex justify-end gap-2">
              <Button type="button" variant="outline" onClick={() => setDialogOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={saving}>
                {editing ? "Save customer" : "Create customer"}
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={detail != null} onOpenChange={(open) => !open && setDetail(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{detail?.name}</DialogTitle>
            <DialogDescription>{detail?.active ? "Active customer" : "Inactive customer"}</DialogDescription>
          </DialogHeader>
          {detail ? (
            <div className="space-y-4">
              <dl className="grid gap-3 text-sm sm:grid-cols-2">
                <div>
                  <dt className="text-muted-foreground">Phone</dt>
                  <dd>{detail.phone ?? "—"}</dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Email</dt>
                  <dd>{detail.email ?? "—"}</dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">GSTIN</dt>
                  <dd>{detail.gstin ?? "—"}</dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Total sales</dt>
                  <dd>{inr(detailSales)}</dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Total paid</dt>
                  <dd>{inr(detailPaid)}</dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Outstanding</dt>
                  <dd>{inr(detailOutstanding)}</dd>
                </div>
                <div className="sm:col-span-2">
                  <dt className="text-muted-foreground">Address</dt>
                  <dd>{detail.address ?? "—"}</dd>
                </div>
              </dl>
              <div>
                <p className="mb-2 text-sm font-medium">Payment history</p>
                {detailPayments.length === 0 ? (
                  <p className="text-sm text-muted-foreground">No payments recorded yet.</p>
                ) : (
                  <ul className="space-y-2 text-sm">
                    {detailPayments.map((payment) => (
                      <li key={payment.id} className="flex justify-between gap-3 rounded-md border px-3 py-2">
                        <span>
                          <span className="font-mono text-xs">{payment.paymentNumber}</span>
                          <span className="ml-2 text-muted-foreground">{payment.paymentMethod}</span>
                        </span>
                        <span>
                          {inr(payment.amount)} · {payment.paymentDate}
                        </span>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </div>
          ) : null}
        </DialogContent>
      </Dialog>
    </div>
  );
}
