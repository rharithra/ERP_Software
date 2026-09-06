import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactElement } from "react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AuthProvider } from "@/auth/auth-context";
import { authApi, returnApi, saleApi } from "@/lib/api";
import { ReturnCreatePage } from "@/pages/return-create-page";
import { ReturnsPage } from "@/pages/returns-page";

vi.mock("@/lib/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/api")>();
  return {
    ...actual,
    authApi: { me: vi.fn(), login: vi.fn(), signup: vi.fn() },
    saleApi: { get: vi.fn() },
    returnApi: { list: vi.fn(), get: vi.fn(), create: vi.fn(), complete: vi.fn(), cancel: vi.fn() },
  };
});

const owner = {
  id: "u1",
  email: "owner@shop.test",
  fullName: "Owner",
  role: "OWNER" as const,
  tenant: {
    id: "t1",
    name: "Shop",
    currency: "INR",
    timezone: "Asia/Kolkata",
    businessType: "GROCERY_SUPERMARKET" as const,
    salesMode: "HYBRID" as const,
  },
};

const sale = {
  id: "sale-1",
  saleNumber: "SAL-000001",
  invoiceNumber: "INV-000001",
  customerId: "c1",
  customerName: "Ravi Kumar",
  customerPhone: "9888810001",
  customerGstin: null,
  saleDate: "2026-09-06",
  status: "COMPLETED" as const,
  subtotal: "40000.00",
  discount: "0.00",
  taxTotal: "0.00",
  grandTotal: "40000.00",
  paymentMethod: "CASH" as const,
  paymentStatus: "PAID" as const,
  notes: null,
  completedAt: "2026-09-06T00:00:00Z",
  createdBy: "u1",
  createdByName: "Owner",
  createdAt: "2026-09-06T00:00:00Z",
  updatedAt: "2026-09-06T00:00:00Z",
  itemCount: 1,
  items: [
    {
      id: "item-1",
      productId: "p1",
      productName: "Water Purifier",
      sku: "WP-M8",
      unit: "PCS",
      quantity: "1.000",
      unitPrice: "40000.00",
      gstRate: "0",
      discount: "0.00",
      taxableAmount: "40000.00",
      taxAmount: "0.00",
      lineTotal: "40000.00",
      returnedQuantity: "0.000",
      availableToReturn: "1.000",
    },
  ],
  paidAmount: "40000.00",
  outstandingAmount: "0.00",
};

function wrap(ui: ReactElement, path = "/app/returns") {
  localStorage.setItem("retailflow.accessToken", "token");
  vi.mocked(authApi.me).mockResolvedValue(owner);
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/app/returns" element={ui} />
          <Route path="/app/returns/new" element={ui} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  );
}

describe("returns UI", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.mocked(returnApi.list).mockReset();
    vi.mocked(returnApi.create).mockReset();
    vi.mocked(saleApi.get).mockReset();
  });

  it("lists returns", async () => {
    vi.mocked(returnApi.list).mockResolvedValue({
      items: [
        {
          id: "r1",
          returnNumber: "RET-000001",
          saleId: "sale-1",
          saleNumber: "SAL-000001",
          invoiceNumber: "INV-000001",
          customerId: "c1",
          customerName: "Ravi Kumar",
          status: "COMPLETED",
          returnDate: "2026-09-06",
          reason: "DEFECTIVE",
          notes: null,
          subtotal: "40000.00",
          discount: "0.00",
          taxAmount: "0.00",
          totalAmount: "40000.00",
          createdBy: "u1",
          createdByName: "Owner",
          createdAt: "2026-09-06T00:00:00Z",
          completedAt: "2026-09-06T00:00:00Z",
          items: [],
        },
      ],
      page: 1,
      size: 20,
      totalItems: 1,
      totalPages: 1,
    });
    wrap(<ReturnsPage />);
    expect(await screen.findByText("RET-000001")).toBeInTheDocument();
    expect(screen.getByText("Ravi Kumar")).toBeInTheDocument();
  });

  it("validates return quantity against available to return", async () => {
    vi.mocked(saleApi.get).mockResolvedValue(sale);
    wrap(<ReturnCreatePage />, "/app/returns/new?saleId=sale-1");
    expect(await screen.findByText("Water Purifier")).toBeInTheDocument();
    const qty = screen.getAllByRole("spinbutton")[0];
    fireEvent.change(qty, { target: { value: "2" } });
    await userEvent.click(screen.getByRole("button", { name: "Complete return" }));
    expect(await screen.findByText(/Only 1 of Water Purifier/)).toBeInTheDocument();
    expect(returnApi.create).not.toHaveBeenCalled();
  });
});
