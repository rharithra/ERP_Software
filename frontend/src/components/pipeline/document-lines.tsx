import { Plus, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { NativeSelect } from "@/components/ui/native-select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type { Product } from "@/lib/api";
import { lineTotals, saleTotals, inr } from "@/lib/sale-math";

export type QuoteLine = {
  key: string;
  productId: string;
  productName: string;
  sku: string;
  unit: string;
  quantity: string;
  unitPrice: string;
  gstRate: string;
  discount: string;
};

export function emptyQuoteLine(): QuoteLine {
  return {
    key: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
    productId: "",
    productName: "",
    sku: "",
    unit: "",
    quantity: "1",
    unitPrice: "0",
    gstRate: "0",
    discount: "0",
  };
}

export function DocumentLines({
  products,
  lines,
  setLines,
  headerDiscount,
  setHeaderDiscount,
}: {
  products: Product[];
  lines: QuoteLine[];
  setLines: (lines: QuoteLine[]) => void;
  headerDiscount: string;
  setHeaderDiscount: (value: string) => void;
}) {
  const mathLines = lines
    .filter((line) => line.productId)
    .map((line) => ({
      quantity: Number(line.quantity) || 0,
      unitPrice: Number(line.unitPrice) || 0,
      gstRate: Number(line.gstRate) || 0,
      discount: Number(line.discount) || 0,
    }));
  const totals = saleTotals(mathLines, Number(headerDiscount) || 0);

  function applyProduct(index: number, productId: string) {
    const product = products.find((item) => item.id === productId);
    setLines(
      lines.map((line, i) =>
        i === index
          ? {
              ...line,
              productId,
              productName: product?.name ?? "",
              sku: product?.sku ?? "",
              unit: product?.unit ?? "",
              unitPrice: product ? String(product.sellingPrice) : line.unitPrice,
              gstRate: product ? String(product.gstRate) : line.gstRate,
            }
          : line,
      ),
    );
  }

  return (
    <div className="space-y-4">
      <div className="overflow-x-auto">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Product</TableHead>
              <TableHead>Qty</TableHead>
              <TableHead>Price</TableHead>
              <TableHead>GST %</TableHead>
              <TableHead>Discount</TableHead>
              <TableHead>Total</TableHead>
              <TableHead />
            </TableRow>
          </TableHeader>
          <TableBody>
            {lines.map((line, index) => {
              const totalsLine = lineTotals(
                Number(line.quantity) || 0,
                Number(line.unitPrice) || 0,
                Number(line.gstRate) || 0,
                Number(line.discount) || 0,
              );
              return (
                <TableRow key={line.key}>
                  <TableCell className="min-w-56">
                    <NativeSelect
                      aria-label="Product"
                      value={line.productId}
                      onChange={(e) => applyProduct(index, e.target.value)}
                    >
                      <option value="">Select product</option>
                      {products.map((product) => (
                        <option key={product.id} value={product.id}>
                          {product.name} ({product.sku})
                        </option>
                      ))}
                    </NativeSelect>
                    {line.productId ? (
                      <p className="mt-1 text-xs text-muted-foreground">
                        GST {line.gstRate}% from product · {line.unit}
                      </p>
                    ) : null}
                  </TableCell>
                  <TableCell>
                    <Input
                      type="number"
                      min="0.001"
                      step="0.001"
                      value={line.quantity}
                      onChange={(e) =>
                        setLines(lines.map((row, i) => (i === index ? { ...row, quantity: e.target.value } : row)))
                      }
                    />
                  </TableCell>
                  <TableCell>
                    <Input
                      type="number"
                      min="0"
                      step="0.01"
                      value={line.unitPrice}
                      onChange={(e) =>
                        setLines(lines.map((row, i) => (i === index ? { ...row, unitPrice: e.target.value } : row)))
                      }
                    />
                  </TableCell>
                  <TableCell>
                    <Input
                      type="number"
                      min="0"
                      step="0.01"
                      value={line.gstRate}
                      onChange={(e) =>
                        setLines(lines.map((row, i) => (i === index ? { ...row, gstRate: e.target.value } : row)))
                      }
                    />
                  </TableCell>
                  <TableCell>
                    <Input
                      type="number"
                      min="0"
                      step="0.01"
                      value={line.discount}
                      onChange={(e) =>
                        setLines(lines.map((row, i) => (i === index ? { ...row, discount: e.target.value } : row)))
                      }
                    />
                  </TableCell>
                  <TableCell className="whitespace-nowrap font-medium">{inr(totalsLine.total)}</TableCell>
                  <TableCell>
                    <Button
                      type="button"
                      size="icon"
                      variant="ghost"
                      aria-label="Remove line"
                      onClick={() => setLines(lines.filter((_, i) => i !== index))}
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </div>
      <Button type="button" variant="outline" onClick={() => setLines([...lines, emptyQuoteLine()])}>
        <Plus className="mr-2 h-4 w-4" />
        Add line
      </Button>
      <div className="grid gap-4 sm:grid-cols-2">
        <div>
          <label className="text-sm font-medium" htmlFor="header-discount">
            Document discount (₹)
          </label>
          <Input
            id="header-discount"
            className="mt-2"
            type="number"
            min="0"
            value={headerDiscount}
            onChange={(e) => setHeaderDiscount(e.target.value)}
          />
        </div>
        <div className="rounded-lg border p-4 text-sm sm:text-right">
          <p>Subtotal: {inr(totals.subtotal)}</p>
          <p>Discount: {inr(totals.discount)}</p>
          <p data-testid="quote-tax">GST: {inr(totals.taxTotal)}</p>
          <p className="text-base font-semibold" data-testid="quote-grand">
            Grand total: {inr(totals.grandTotal)}
          </p>
        </div>
      </div>
    </div>
  );
}
