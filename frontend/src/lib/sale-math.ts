export function roundMoney(value: number): number {
  return Math.round((value + Number.EPSILON) * 100) / 100;
}

export function lineTotals(quantity: number, unitPrice: number, gstRate: number, lineDiscount = 0) {
  const gross = roundMoney(quantity * unitPrice);
  const discount = roundMoney(lineDiscount);
  const taxable = roundMoney(gross - discount);
  const tax = roundMoney((taxable * gstRate) / 100);
  return { taxable, tax, total: roundMoney(taxable + tax) };
}

export function saleTotals(
  lines: { quantity: number; unitPrice: number; gstRate: number; discount?: number }[],
  saleDiscount = 0,
) {
  let subtotal = 0;
  let taxTotal = 0;
  for (const line of lines) {
    const totals = lineTotals(line.quantity, line.unitPrice, line.gstRate, line.discount ?? 0);
    subtotal = roundMoney(subtotal + totals.taxable);
    taxTotal = roundMoney(taxTotal + totals.tax);
  }
  const discount = roundMoney(saleDiscount);
  return {
    subtotal,
    discount,
    taxTotal,
    grandTotal: roundMoney(subtotal - discount + taxTotal),
  };
}

export function inr(value: number | string) {
  const amount = typeof value === "string" ? Number(value) : value;
  return new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" }).format(
    Number.isFinite(amount) ? amount : 0,
  );
}
