import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactElement } from "react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AuthProvider } from "@/auth/auth-context";
import { ApiRequestError, authApi, categoryApi, inventoryApi, storeToken } from "@/lib/api";
import { InventoryPage } from "@/pages/inventory-page";

vi.mock("@/lib/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/api")>();
  return {
    ...actual,
    authApi: {
      me: vi.fn(),
      login: vi.fn(),
      signup: vi.fn(),
    },
    categoryApi: {
      list: vi.fn(),
    },
    inventoryApi: {
      list: vi.fn(),
      summary: vi.fn(),
      get: vi.fn(),
      movements: vi.fn(),
      openingStock: vi.fn(),
      adjust: vi.fn(),
      updateReorderLevel: vi.fn(),
    },
  };
});

const milk = {
  productId: "22222222-2222-2222-2222-222222222222",
  productName: "Amul Taaza 500ml",
  sku: "AMUL-MILK-500",
  categoryId: "11111111-1111-1111-1111-111111111111",
  categoryName: "Dairy",
  unit: "ML",
  quantity: "42",
  reorderLevel: "10",
  status: "IN_STOCK" as const,
  openingRecorded: true,
  updatedAt: "2026-01-01T00:00:00Z",
};

function pageOf<T>(items: T[]) {
  return { items, page: 1, size: 20, totalItems: items.length, totalPages: items.length ? 1 : 0 };
}

function renderPage(ui: ReactElement) {
  return render(
    <AuthProvider>
      <MemoryRouter>{ui}</MemoryRouter>
    </AuthProvider>,
  );
}

describe("Inventory page", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.mocked(authApi.me).mockReset();
    vi.mocked(categoryApi.list).mockResolvedValue(pageOf([]));
    vi.mocked(inventoryApi.list).mockReset();
    vi.mocked(inventoryApi.summary).mockReset();
    vi.mocked(inventoryApi.openingStock).mockReset();
    vi.mocked(inventoryApi.adjust).mockReset();
  });

  it("renders inventory rows and live summary counts", async () => {
    vi.mocked(inventoryApi.list).mockResolvedValue(pageOf([milk]));
    vi.mocked(inventoryApi.summary).mockResolvedValue({ totalProducts: 1, inStock: 1, lowStock: 0, outOfStock: 0 });
    renderPage(<InventoryPage />);
    expect(await screen.findAllByText("Amul Taaza 500ml")).not.toHaveLength(0);
    expect(screen.getAllByText("AMUL-MILK-500").length).toBeGreaterThan(0);
    expect(screen.getAllByText("In stock").length).toBeGreaterThan(0);
    expect(screen.getByText("Total products")).toBeInTheDocument();
  });

  it("shows a loading skeleton then an empty catalog message", async () => {
    vi.mocked(inventoryApi.list).mockResolvedValue(pageOf([]));
    vi.mocked(inventoryApi.summary).mockResolvedValue({ totalProducts: 0, inStock: 0, lowStock: 0, outOfStock: 0 });
    renderPage(<InventoryPage />);
    expect(await screen.findByText(/Add products in the catalog first/)).toBeInTheDocument();
  });

  it("validates opening quantity", async () => {
    vi.mocked(inventoryApi.list).mockResolvedValue(pageOf([{ ...milk, openingRecorded: false, quantity: "0", status: "OUT_OF_STOCK" }]));
    vi.mocked(inventoryApi.summary).mockResolvedValue({ totalProducts: 1, inStock: 0, lowStock: 0, outOfStock: 1 });
    renderPage(<InventoryPage />);
    await screen.findAllByText("Amul Taaza 500ml");
    await userEvent.setup().click(screen.getAllByRole("button", { name: "Opening stock" })[0]);
    await userEvent.setup().click(screen.getByRole("button", { name: "Initialize stock" }));
    expect(await screen.findByText("Opening quantity must be greater than zero.")).toBeInTheDocument();
    expect(inventoryApi.openingStock).not.toHaveBeenCalled();
  });

  it("validates stock adjustments and requires confirmation", async () => {
    vi.mocked(inventoryApi.list).mockResolvedValue(pageOf([milk]));
    vi.mocked(inventoryApi.summary).mockResolvedValue({ totalProducts: 1, inStock: 1, lowStock: 0, outOfStock: 0 });
    renderPage(<InventoryPage />);
    await screen.findAllByText("Amul Taaza 500ml");
    const user = userEvent.setup();
    await user.click(screen.getAllByRole("button", { name: "Adjust" })[0]);
    await user.type(screen.getByLabelText("Quantity"), "3");
    await user.click(screen.getByRole("button", { name: "Save adjustment" }));
    expect(await screen.findByText("Confirm this adjustment before saving.")).toBeInTheDocument();
    expect(inventoryApi.adjust).not.toHaveBeenCalled();
  });

  it("saves a confirmed adjustment", async () => {
    vi.mocked(inventoryApi.list).mockResolvedValue(pageOf([milk]));
    vi.mocked(inventoryApi.summary).mockResolvedValue({ totalProducts: 1, inStock: 1, lowStock: 0, outOfStock: 0 });
    vi.mocked(inventoryApi.adjust).mockResolvedValue({ ...milk, quantity: "45" });
    renderPage(<InventoryPage />);
    await screen.findAllByText("Amul Taaza 500ml");
    const user = userEvent.setup();
    await user.click(screen.getAllByRole("button", { name: "Adjust" })[0]);
    await user.type(screen.getByLabelText("Quantity"), "3");
    await user.click(screen.getByLabelText("I confirm this adjustment is correct"));
    await user.click(screen.getByRole("button", { name: "Save adjustment" }));
    expect(inventoryApi.adjust).toHaveBeenCalled();
  });

  it("shows API errors on the inventory list", async () => {
    vi.mocked(inventoryApi.list).mockRejectedValue(new ApiRequestError(500, "INTERNAL_ERROR", "Stock service unavailable"));
    vi.mocked(inventoryApi.summary).mockRejectedValue(new ApiRequestError(500, "INTERNAL_ERROR", "Stock service unavailable"));
    renderPage(<InventoryPage />);
    expect(await screen.findByText("Stock service unavailable")).toBeInTheDocument();
  });

  it("hides stock mutations for cashiers", async () => {
    storeToken("cashier-token");
    vi.mocked(authApi.me).mockResolvedValue({
      id: "u1",
      email: "cashier@shop.test",
      fullName: "Ravi Cashier",
      role: "CASHIER",
      tenant: { id: "t1", name: "Shop", currency: "INR", timezone: "Asia/Kolkata" },
    });
    vi.mocked(inventoryApi.list).mockResolvedValue(pageOf([milk]));
    vi.mocked(inventoryApi.summary).mockResolvedValue({ totalProducts: 1, inStock: 1, lowStock: 0, outOfStock: 0 });
    renderPage(<InventoryPage />);
    expect(await screen.findAllByText("Amul Taaza 500ml")).not.toHaveLength(0);
    expect(screen.queryByRole("button", { name: "Adjust" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Opening stock" })).not.toBeInTheDocument();
  });
});
