import { useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { toast } from "sonner";
import { DocumentLines, emptyQuoteLine, type QuoteLine } from "@/components/pipeline/document-lines";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";
import { ApiRequestError, customerApi, leadApi, productApi, salesOrderApi, type Customer, type Lead, type Product } from "@/lib/api";
import { todayIso } from "@/lib/pipeline";

export function SalesOrderFormPage() {
  const navigate = useNavigate();
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [leads, setLeads] = useState<Lead[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [customerId, setCustomerId] = useState("");
  const [leadId, setLeadId] = useState("");
  const [orderDate, setOrderDate] = useState(todayIso());
  const [delivery, setDelivery] = useState("");
  const [discount, setDiscount] = useState("0");
  const [notes, setNotes] = useState("");
  const [lines, setLines] = useState<QuoteLine[]>([emptyQuoteLine()]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    Promise.all([
      customerApi.active(),
      leadApi.list({ page: 1, size: 100 }),
      productApi.list({ active: true, page: 1, size: 100 }),
    ])
      .then(([people, leadPage, productPage]) => {
        setCustomers(people);
        setLeads(leadPage.items);
        setProducts(productPage.items);
      })
      .catch((err) => setError(err instanceof ApiRequestError ? err.message : "Unable to load sales order form"))
      .finally(() => setLoading(false));
  }, []);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    const items = lines
      .filter((line) => line.productId)
      .map((line) => ({
        productId: line.productId,
        quantity: Number(line.quantity),
        unitPrice: Number(line.unitPrice),
        gstRate: Number(line.gstRate),
        discount: Number(line.discount) || 0,
      }));
    if (!customerId) {
      setFormError("Select a customer.");
      return;
    }
    if (items.length === 0) {
      setFormError("Add at least one product.");
      return;
    }
    setSaving(true);
    setFormError(null);
    try {
      const order = await salesOrderApi.create({
        customerId,
        leadId: leadId || null,
        orderDate,
        expectedDeliveryDate: delivery || null,
        discount: Number(discount) || 0,
        notes: notes.trim() || null,
        items,
      });
      toast.success(`${order.orderNumber} saved as draft`);
      navigate(`/app/sales-orders/${order.id}`);
    } catch (err) {
      setFormError(err instanceof ApiRequestError ? err.message : "Unable to save sales order");
    } finally {
      setSaving(false);
    }
  }

  if (loading) return <Skeleton className="h-64" />;
  if (error) return <p className="text-sm text-destructive">{error}</p>;

  return (
    <form className="space-y-6" onSubmit={onSubmit}>
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">New sales order</h1>
          <p className="mt-1 text-sm text-muted-foreground">Confirming later still leaves inventory unchanged.</p>
        </div>
        <div className="flex gap-2">
          <Button type="button" variant="outline" asChild>
            <Link to="/app/sales-orders">Cancel</Link>
          </Button>
          <Button type="submit" disabled={saving}>
            Save draft
          </Button>
        </div>
      </div>
      <Card>
        <CardHeader>
          <CardTitle>Order header</CardTitle>
          <CardDescription>Prefer creating from an accepted quotation when one exists.</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-2">
            <Label htmlFor="so-customer">Customer</Label>
            <NativeSelect id="so-customer" value={customerId} onChange={(e) => setCustomerId(e.target.value)}>
              <option value="">Select customer</option>
              {customers.map((customer) => (
                <option key={customer.id} value={customer.id}>
                  {customer.name}
                </option>
              ))}
            </NativeSelect>
          </div>
          <div className="space-y-2">
            <Label htmlFor="so-lead">Lead</Label>
            <NativeSelect id="so-lead" value={leadId} onChange={(e) => setLeadId(e.target.value)}>
              <option value="">None</option>
              {leads.map((lead) => (
                <option key={lead.id} value={lead.id}>
                  {lead.leadNumber} · {lead.name}
                </option>
              ))}
            </NativeSelect>
          </div>
          <div className="space-y-2">
            <Label htmlFor="so-date">Order date</Label>
            <Input id="so-date" type="date" value={orderDate} onChange={(e) => setOrderDate(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="so-delivery">Expected delivery</Label>
            <Input id="so-delivery" type="date" value={delivery} onChange={(e) => setDelivery(e.target.value)} />
          </div>
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <CardTitle>Items</CardTitle>
        </CardHeader>
        <CardContent>
          <DocumentLines
            products={products}
            lines={lines}
            setLines={setLines}
            headerDiscount={discount}
            setHeaderDiscount={setDiscount}
          />
          <Textarea className="mt-4" placeholder="Notes" value={notes} onChange={(e) => setNotes(e.target.value)} />
          {formError ? <p className="mt-3 text-sm text-destructive">{formError}</p> : null}
        </CardContent>
      </Card>
    </form>
  );
}
