import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { ApiRequestError, saleApi, type SaleInvoice } from "@/lib/api";
import { inr } from "@/lib/sale-math";

export function SaleInvoicePage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [invoice, setInvoice] = useState<SaleInvoice | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    saleApi
      .invoice(id)
      .then(setInvoice)
      .catch((err) => setError(err instanceof ApiRequestError ? err.message : "Unable to load invoice"))
      .finally(() => setLoading(false));
  }, [id]);

  if (loading) {
    return (
      <div className="space-y-3">
        <Skeleton className="h-10 w-64" />
        <Skeleton className="h-96 w-full" />
      </div>
    );
  }

  if (error || !invoice) {
    return (
      <div className="space-y-4">
        <p className="text-sm text-destructive">{error ?? "Invoice not found"}</p>
        <Button variant="outline" onClick={() => navigate("/app/sales")}>
          Back to sales
        </Button>
      </div>
    );
  }

  const sale = invoice.sale;

  return (
    <div className="mx-auto max-w-[210mm] space-y-4">
      <div className="flex flex-wrap gap-2 print:hidden">
        <Button variant="outline" asChild>
          <Link to={`/app/sales/${sale.id}`}>Back to sale</Link>
        </Button>
        <Button onClick={() => window.print()}>Print Invoice</Button>
      </div>
      <article className="rounded-lg border bg-white p-8 text-black shadow-sm print:border-0 print:shadow-none dark:bg-white">
        <header className="flex flex-col gap-4 border-b pb-6 sm:flex-row sm:justify-between">
          <div>
            <h1 className="text-2xl font-semibold">{invoice.companyName ?? "RetailFlow store"}</h1>
            {invoice.companyAddress ? <p className="mt-1 text-sm text-neutral-600">{invoice.companyAddress}</p> : null}
            {invoice.companyPhone ? <p className="text-sm text-neutral-600">Phone: {invoice.companyPhone}</p> : null}
            {invoice.companyGstin ? <p className="text-sm text-neutral-600">GSTIN: {invoice.companyGstin}</p> : null}
          </div>
          <div className="text-sm sm:text-right">
            <p className="text-lg font-semibold">Tax Invoice</p>
            <p>Invoice: {sale.invoiceNumber}</p>
            <p>Sale: {sale.saleNumber}</p>
            <p>Date: {sale.saleDate}</p>
          </div>
        </header>
        <section className="grid gap-4 py-6 text-sm sm:grid-cols-2">
          <div>
            <p className="font-medium">Bill to</p>
            <p>{sale.customerName}</p>
            {sale.customerPhone ? <p>{sale.customerPhone}</p> : null}
            {sale.customerGstin ? <p>GSTIN: {sale.customerGstin}</p> : null}
          </div>
          <div className="sm:text-right">
            <p>Payment: {sale.paymentMethod ?? "—"}</p>
            <p>Status: {sale.paymentStatus ?? "PAID"}</p>
          </div>
        </section>
        <table className="w-full text-left text-sm">
          <thead>
            <tr className="border-b">
              <th className="py-2">Product</th>
              <th className="py-2">SKU</th>
              <th className="py-2">Qty</th>
              <th className="py-2">Price</th>
              <th className="py-2">GST</th>
              <th className="py-2 text-right">Line total</th>
            </tr>
          </thead>
          <tbody>
            {sale.items.map((item) => (
              <tr key={item.id} className="border-b">
                <td className="py-2">{item.productName}</td>
                <td className="py-2 font-mono text-xs">{item.sku}</td>
                <td className="py-2">
                  {item.quantity} {item.unit}
                </td>
                <td className="py-2">{inr(item.unitPrice)}</td>
                <td className="py-2">{Number(item.gstRate)}%</td>
                <td className="py-2 text-right">{inr(item.lineTotal)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <section className="mt-6 space-y-1 text-sm sm:ml-auto sm:w-64 sm:text-right">
          <p>Subtotal: {inr(sale.subtotal)}</p>
          <p>Discount: {inr(sale.discount)}</p>
          <p>GST: {inr(sale.taxTotal)}</p>
          <p className="text-base font-semibold">Grand total: {inr(sale.grandTotal)}</p>
        </section>
        <p className="mt-10 text-xs text-neutral-500">Thank you for shopping with us. This invoice is generated from the completed sale.</p>
      </article>
    </div>
  );
}
