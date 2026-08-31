import { useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { toast } from "sonner";
import { DocumentLines, emptyQuoteLine, type QuoteLine } from "@/components/pipeline/document-lines";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";
import {
  ApiRequestError,
  customerApi,
  leadApi,
  productApi,
  quotationApi,
  type Customer,
  type Lead,
  type Product,
} from "@/lib/api";
import { todayIso } from "@/lib/pipeline";

export function QuotationFormPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [leads, setLeads] = useState<Lead[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [customerId, setCustomerId] = useState(params.get("customerId") ?? "");
  const [leadId, setLeadId] = useState(params.get("leadId") ?? "");
  const [quotationDate, setQuotationDate] = useState(todayIso());
  const [validUntil, setValidUntil] = useState(todayIso());
  const [discount, setDiscount] = useState("0");
  const [notes, setNotes] = useState("");
  const [terms, setTerms] = useState("Prices inclusive of applicable GST unless stated otherwise. Valid until the date shown.");
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
      .catch((err) => setError(err instanceof ApiRequestError ? err.message : "Unable to load quotation form"))
      .finally(() => setLoading(false));
  }, []);

  async function save(send: boolean) {
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
      setFormError("Select a customer. Convert the lead first if needed.");
      return;
    }
    if (items.length === 0) {
      setFormError("Add at least one product.");
      return;
    }
    setSaving(true);
    setFormError(null);
    try {
      const quote = await quotationApi.create({
        customerId,
        leadId: leadId || null,
        quotationDate,
        validUntil,
        discount: Number(discount) || 0,
        notes: notes.trim() || null,
        termsAndConditions: terms.trim() || null,
        items,
      });
      if (send) {
        await quotationApi.send(quote.id);
        toast.success(`${quote.quotationNumber} marked sent`);
      } else {
        toast.success(`${quote.quotationNumber} saved as draft`);
      }
      navigate(`/app/quotations/${quote.id}`);
    } catch (err) {
      setFormError(err instanceof ApiRequestError ? err.message : "Unable to save quotation");
    } finally {
      setSaving(false);
    }
  }

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    void save(false);
  }

  if (loading) {
    return <Skeleton className="h-64" />;
  }
  if (error) {
    return <p className="text-sm text-destructive">{error}</p>;
  }

  return (
    <form className="space-y-6" onSubmit={onSubmit}>
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">New quotation</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Product GST fills automatically. This document does not touch inventory.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button type="button" variant="outline" asChild>
            <Link to="/app/quotations">Cancel</Link>
          </Button>
          <Button type="submit" variant="outline" disabled={saving}>
            Save draft
          </Button>
          <Button type="button" disabled={saving} onClick={() => void save(true)}>
            Mark sent
          </Button>
        </div>
      </div>
      <Card>
        <CardHeader>
          <CardTitle>Parties</CardTitle>
          <CardDescription>Customer is required. Lead is optional context.</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-2">
            <Label htmlFor="quote-customer">Customer</Label>
            <NativeSelect id="quote-customer" value={customerId} onChange={(e) => setCustomerId(e.target.value)}>
              <option value="">Select customer</option>
              {customers.map((customer) => (
                <option key={customer.id} value={customer.id}>
                  {customer.name}
                </option>
              ))}
            </NativeSelect>
          </div>
          <div className="space-y-2">
            <Label htmlFor="quote-lead">Lead</Label>
            <NativeSelect id="quote-lead" value={leadId} onChange={(e) => setLeadId(e.target.value)}>
              <option value="">None</option>
              {leads.map((lead) => (
                <option key={lead.id} value={lead.id}>
                  {lead.leadNumber} · {lead.name}
                </option>
              ))}
            </NativeSelect>
          </div>
          <div className="space-y-2">
            <Label htmlFor="quote-date">Quotation date</Label>
            <Input id="quote-date" type="date" value={quotationDate} onChange={(e) => setQuotationDate(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="quote-valid">Valid until</Label>
            <Input id="quote-valid" type="date" value={validUntil} onChange={(e) => setValidUntil(e.target.value)} />
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
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <CardTitle>Notes</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-4">
          <Textarea value={notes} onChange={(e) => setNotes(e.target.value)} placeholder="Internal notes" />
          <Textarea value={terms} onChange={(e) => setTerms(e.target.value)} placeholder="Terms and conditions" />
          {formError ? <p className="text-sm text-destructive">{formError}</p> : null}
        </CardContent>
      </Card>
    </form>
  );
}
