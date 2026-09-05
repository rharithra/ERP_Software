import type { BusinessType, SalesMode } from "@/lib/sales-experience";

const TOKEN_KEY = "retailflow.accessToken";

export type TenantSummary = {
  id: string;
  name: string;
  currency: string;
  timezone: string;
  businessType: BusinessType;
  salesMode: SalesMode;
};

export type AuthUser = {
  id: string;
  email: string;
  fullName: string;
  role: "OWNER" | "MANAGER" | "CASHIER";
  tenant: TenantSummary;
};

export type CompanyProfile = {
  id: string;
  name: string;
  legalName: string | null;
  gstin: string | null;
  phone: string | null;
  email: string | null;
  addressLine1: string | null;
  addressLine2: string | null;
  city: string | null;
  state: string | null;
  pincode: string | null;
  currency: string;
  timezone: string;
  businessType: BusinessType;
  salesMode: SalesMode;
};

type ApiError = {
  code: string;
  message: string;
  details?: { field: string; message: string }[] | null;
};

type ApiResponse<T> = {
  success: boolean;
  data?: T;
  error?: ApiError;
};

export class ApiRequestError extends Error {
  readonly status: number;
  readonly code: string;

  constructor(status: number, code: string, message: string) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

export function getStoredToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function storeToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
}

const AUTH_ATTEMPT_PATHS = new Set(["/api/v1/auth/login", "/api/v1/auth/signup"]);

export function handleUnauthorized(path: string) {
  if (AUTH_ATTEMPT_PATHS.has(path)) {
    return;
  }
  clearToken();
  if (typeof window !== "undefined") {
    window.location.assign("/login");
  }
}

export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  if (init.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  const token = getStoredToken();
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const response = await fetch(path, { ...init, headers });
  const payload = (await response.json().catch(() => null)) as ApiResponse<T> | null;

  if (response.status === 401) {
    handleUnauthorized(path);
  }

  if (!response.ok || !payload?.success) {
    throw new ApiRequestError(
      response.status,
      payload?.error?.code ?? "REQUEST_FAILED",
      payload?.error?.message ?? "Something went wrong. Please try again.",
    );
  }

  return payload.data as T;
}

export type AuthPayload = {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  user: AuthUser;
};

export const authApi = {
  signup: (body: {
    fullName: string;
    email: string;
    password: string;
    companyName: string;
    businessType: BusinessType;
    salesMode: SalesMode;
  }) => apiRequest<AuthPayload>("/api/v1/auth/signup", { method: "POST", body: JSON.stringify(body) }),
  login: (body: { email: string; password: string }) =>
    apiRequest<AuthPayload>("/api/v1/auth/login", { method: "POST", body: JSON.stringify(body) }),
  me: () => apiRequest<AuthUser>("/api/v1/auth/me"),
};

export type TenantMember = {
  userId: string;
  fullName: string;
  email: string;
  role: "OWNER" | "MANAGER" | "CASHIER";
};

export const tenantApi = {
  get: () => apiRequest<CompanyProfile>("/api/v1/tenant"),
  update: (body: Partial<CompanyProfile> & { name: string }) =>
    apiRequest<CompanyProfile>("/api/v1/tenant", { method: "PUT", body: JSON.stringify(body) }),
  members: () => apiRequest<TenantMember[]>("/api/v1/tenant/members"),
};

export type StaffRole = "MANAGER" | "CASHIER";
export type MembershipStatus = "ACTIVE" | "INACTIVE";

export type TenantUser = {
  id: string;
  fullName: string;
  email: string;
  role: AuthUser["role"];
  status: MembershipStatus;
  createdAt: string | null;
};

export const usersApi = {
  list: () => apiRequest<TenantUser[]>("/api/v1/users"),
  get: (id: string) => apiRequest<TenantUser>(`/api/v1/users/${id}`),
  create: (body: { fullName: string; email: string; role: StaffRole; temporaryPassword: string }) =>
    apiRequest<TenantUser>("/api/v1/users", { method: "POST", body: JSON.stringify(body) }),
  changeRole: (id: string, role: StaffRole) =>
    apiRequest<TenantUser>(`/api/v1/users/${id}/role`, { method: "PATCH", body: JSON.stringify({ role }) }),
  changeStatus: (id: string, status: MembershipStatus) =>
    apiRequest<TenantUser>(`/api/v1/users/${id}/status`, { method: "PATCH", body: JSON.stringify({ status }) }),
  resetPassword: (id: string, temporaryPassword: string) =>
    apiRequest<null>(`/api/v1/users/${id}/reset-password`, {
      method: "POST",
      body: JSON.stringify({ temporaryPassword }),
    }),
};

export type Category = {
  id: string;
  name: string;
  description: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type Product = {
  id: string;
  name: string;
  sku: string;
  barcode: string | null;
  description: string | null;
  categoryId: string;
  categoryName: string;
  costPrice: number | string;
  sellingPrice: number | string;
  gstRate: number | string;
  unit: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type PageResult<T> = {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
};

export type CategoryPayload = {
  name: string;
  description?: string | null;
};

export type ProductPayload = {
  name: string;
  categoryId: string;
  description?: string | null;
  sku: string;
  barcode?: string | null;
  costPrice: number;
  sellingPrice: number;
  gstRate: number;
  unit: string;
};

function queryString(params: Record<string, string | number | boolean | undefined>) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== "") {
      search.set(key, String(value));
    }
  });
  const encoded = search.toString();
  return encoded ? `?${encoded}` : "";
}

export const categoryApi = {
  list: (params: { q?: string; active?: boolean; page?: number; size?: number } = {}) =>
    apiRequest<PageResult<Category>>(`/api/v1/categories${queryString(params)}`),
  active: () => apiRequest<Category[]>("/api/v1/categories/active"),
  create: (body: CategoryPayload) =>
    apiRequest<Category>("/api/v1/categories", { method: "POST", body: JSON.stringify(body) }),
  update: (id: string, body: CategoryPayload) =>
    apiRequest<Category>(`/api/v1/categories/${id}`, { method: "PUT", body: JSON.stringify(body) }),
  updateStatus: (id: string, active: boolean) =>
    apiRequest<Category>(`/api/v1/categories/${id}/status`, {
      method: "PATCH",
      body: JSON.stringify({ active }),
    }),
};

export const productApi = {
  list: (params: { q?: string; categoryId?: string; active?: boolean; page?: number; size?: number } = {}) =>
    apiRequest<PageResult<Product>>(`/api/v1/products${queryString(params)}`),
  get: (id: string) => apiRequest<Product>(`/api/v1/products/${id}`),
  create: (body: ProductPayload) =>
    apiRequest<Product>("/api/v1/products", { method: "POST", body: JSON.stringify(body) }),
  update: (id: string, body: ProductPayload) =>
    apiRequest<Product>(`/api/v1/products/${id}`, { method: "PUT", body: JSON.stringify(body) }),
  updateStatus: (id: string, active: boolean) =>
    apiRequest<Product>(`/api/v1/products/${id}/status`, {
      method: "PATCH",
      body: JSON.stringify({ active }),
    }),
};

export type InventoryStatus = "IN_STOCK" | "LOW_STOCK" | "OUT_OF_STOCK";

export type InventoryItem = {
  productId: string;
  productName: string;
  sku: string;
  categoryId: string;
  categoryName: string;
  unit: string;
  quantity: number | string;
  reorderLevel: number | string;
  status: InventoryStatus;
  openingRecorded: boolean;
  updatedAt: string;
};

export type InventorySummary = {
  totalProducts: number;
  inStock: number;
  lowStock: number;
  outOfStock: number;
};

export type StockMovementType =
  | "OPENING_STOCK"
  | "ADJUSTMENT_IN"
  | "ADJUSTMENT_OUT"
  | "PURCHASE_RECEIPT"
  | "SALE";

export type StockMovement = {
  id: string;
  productId: string;
  type: StockMovementType;
  quantity: number | string;
  quantityBefore: number | string;
  quantityAfter: number | string;
  referenceType: string | null;
  referenceId: string | null;
  reason: string | null;
  notes: string | null;
  createdBy: string;
  createdByName: string;
  createdAt: string;
};

export type AdjustmentReason = "DAMAGED" | "EXPIRED" | "PHYSICAL_COUNT" | "MISSING" | "OPENING_CORRECTION" | "OTHER";

export const inventoryApi = {
  list: (params: { q?: string; categoryId?: string; status?: InventoryStatus; page?: number; size?: number } = {}) =>
    apiRequest<PageResult<InventoryItem>>(`/api/v1/inventory${queryString(params)}`),
  summary: () => apiRequest<InventorySummary>("/api/v1/inventory/summary"),
  get: (id: string) => apiRequest<InventoryItem>(`/api/v1/inventory/${id}`),
  movements: (id: string, params: { page?: number; size?: number } = {}) =>
    apiRequest<PageResult<StockMovement>>(`/api/v1/inventory/${id}/movements${queryString(params)}`),
  openingStock: (id: string, body: { quantity: number; notes?: string | null }) =>
    apiRequest<InventoryItem>(`/api/v1/inventory/${id}/opening-stock`, {
      method: "POST",
      body: JSON.stringify(body),
    }),
  adjust: (
    id: string,
    body: { type: "ADJUSTMENT_IN" | "ADJUSTMENT_OUT"; quantity: number; reason: AdjustmentReason; notes?: string | null },
  ) =>
    apiRequest<InventoryItem>(`/api/v1/inventory/${id}/adjustments`, {
      method: "POST",
      body: JSON.stringify(body),
    }),
  updateReorderLevel: (id: string, reorderLevel: number) =>
    apiRequest<InventoryItem>(`/api/v1/inventory/${id}/reorder-level`, {
      method: "PATCH",
      body: JSON.stringify({ reorderLevel }),
    }),
};

export type Supplier = {
  id: string;
  name: string;
  contactPerson: string | null;
  phone: string | null;
  email: string | null;
  address: string | null;
  gstin: string | null;
  notes: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type SupplierSummary = {
  totalSuppliers: number;
  activeSuppliers: number;
  inactiveSuppliers: number;
};

export type SupplierPayload = {
  name: string;
  contactPerson?: string | null;
  phone?: string | null;
  email?: string | null;
  address?: string | null;
  gstin?: string | null;
  notes?: string | null;
};

export const supplierApi = {
  list: (params: { q?: string; active?: boolean; page?: number; size?: number } = {}) =>
    apiRequest<PageResult<Supplier>>(`/api/v1/suppliers${queryString(params)}`),
  summary: () => apiRequest<SupplierSummary>("/api/v1/suppliers/summary"),
  active: () => apiRequest<Supplier[]>("/api/v1/suppliers/active"),
  get: (id: string) => apiRequest<Supplier>(`/api/v1/suppliers/${id}`),
  create: (body: SupplierPayload) =>
    apiRequest<Supplier>("/api/v1/suppliers", { method: "POST", body: JSON.stringify(body) }),
  update: (id: string, body: SupplierPayload) =>
    apiRequest<Supplier>(`/api/v1/suppliers/${id}`, { method: "PUT", body: JSON.stringify(body) }),
  updateStatus: (id: string, active: boolean) =>
    apiRequest<Supplier>(`/api/v1/suppliers/${id}/status`, {
      method: "PATCH",
      body: JSON.stringify({ active }),
    }),
};

export type PurchaseStatus = "DRAFT" | "RECEIVED" | "CANCELLED";

export type PurchaseItem = {
  id: string;
  productId: string;
  productName: string;
  sku: string;
  unit: string;
  quantity: number | string;
  unitCost: number | string;
  gstRate: number | string;
  lineSubtotal: number | string;
  taxAmount: number | string;
  lineTotal: number | string;
};

export type Purchase = {
  id: string;
  purchaseNumber: string;
  supplierId: string;
  supplierName: string;
  purchaseDate: string;
  status: PurchaseStatus;
  subtotal: number | string;
  taxAmount: number | string;
  totalAmount: number | string;
  notes: string | null;
  receivedAt: string | null;
  createdBy: string;
  createdByName: string;
  createdAt: string;
  updatedAt: string;
  itemCount: number;
  items: PurchaseItem[];
};

export type PurchaseSummary = {
  totalPurchases: number;
  draftPurchases: number;
  receivedPurchases: number;
  receivedValue: number | string;
};

export type PurchaseItemPayload = {
  productId: string;
  quantity: number;
  unitCost: number;
  gstRate: number;
};

export type PurchasePayload = {
  supplierId: string;
  purchaseDate: string;
  notes?: string | null;
  items: PurchaseItemPayload[];
};

export type Customer = {
  id: string;
  name: string;
  phone: string | null;
  email: string | null;
  address: string | null;
  gstin: string | null;
  notes: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type CustomerSummary = {
  totalCustomers: number;
  activeCustomers: number;
  inactiveCustomers: number;
};

export type CustomerPayload = {
  name: string;
  phone?: string | null;
  email?: string | null;
  address?: string | null;
  gstin?: string | null;
  notes?: string | null;
};

export const customerApi = {
  list: (params: { q?: string; active?: boolean; page?: number; size?: number } = {}) =>
    apiRequest<PageResult<Customer>>(`/api/v1/customers${queryString(params)}`),
  summary: () => apiRequest<CustomerSummary>("/api/v1/customers/summary"),
  active: () => apiRequest<Customer[]>("/api/v1/customers/active"),
  get: (id: string) => apiRequest<Customer>(`/api/v1/customers/${id}`),
  create: (body: CustomerPayload) =>
    apiRequest<Customer>("/api/v1/customers", { method: "POST", body: JSON.stringify(body) }),
  update: (id: string, body: CustomerPayload) =>
    apiRequest<Customer>(`/api/v1/customers/${id}`, { method: "PUT", body: JSON.stringify(body) }),
  updateStatus: (id: string, active: boolean) =>
    apiRequest<Customer>(`/api/v1/customers/${id}/status`, {
      method: "PATCH",
      body: JSON.stringify({ active }),
    }),
};

export type SaleStatus = "DRAFT" | "COMPLETED" | "CANCELLED";
export type PaymentMethod = "CASH" | "UPI" | "CARD" | "BANK_TRANSFER" | "OTHER";
export type PaymentStatus = "UNPAID" | "PARTIALLY_PAID" | "PAID";

export type SaleItem = {
  id: string;
  productId: string;
  productName: string;
  sku: string;
  unit: string;
  quantity: number | string;
  unitPrice: number | string;
  gstRate: number | string;
  discount: number | string;
  taxableAmount: number | string;
  taxAmount: number | string;
  lineTotal: number | string;
};

export type Sale = {
  id: string;
  saleNumber: string;
  invoiceNumber: string | null;
  customerId: string | null;
  customerName: string;
  customerPhone: string | null;
  customerGstin: string | null;
  saleDate: string;
  status: SaleStatus;
  subtotal: number | string;
  discount: number | string;
  taxTotal: number | string;
  grandTotal: number | string;
  paymentMethod: PaymentMethod | null;
  paymentStatus: PaymentStatus | null;
  notes: string | null;
  completedAt: string | null;
  createdBy: string;
  createdByName: string;
  createdAt: string;
  updatedAt: string;
  itemCount: number;
  items: SaleItem[];
  salesOrderId?: string | null;
  paidAmount?: number | string;
  outstandingAmount?: number | string;
};

export type SaleSummary = {
  totalSales: number;
  draftSales: number;
  completedSales: number;
  completedValue: number | string;
};

export type SaleDashboard = {
  todayOrders: number;
  todayRevenue: number | string;
  recentSales: Sale[];
};

export type SaleInvoice = {
  sale: Sale;
  companyName: string | null;
  companyGstin: string | null;
  companyAddress: string | null;
  companyPhone: string | null;
};

export type SaleItemPayload = {
  productId: string;
  quantity: number;
  unitPrice: number;
  gstRate: number;
  discount?: number;
};

export type SalePayload = {
  customerId?: string | null;
  saleDate: string;
  discount?: number;
  notes?: string | null;
  items: SaleItemPayload[];
};

export const saleApi = {
  list: (
    params: {
      q?: string;
      customerId?: string;
      status?: SaleStatus;
      fromDate?: string;
      toDate?: string;
      page?: number;
      size?: number;
    } = {},
  ) => apiRequest<PageResult<Sale>>(`/api/v1/sales${queryString(params)}`),
  summary: () => apiRequest<SaleSummary>("/api/v1/sales/summary"),
  dashboard: () => apiRequest<SaleDashboard>("/api/v1/sales/dashboard"),
  get: (id: string) => apiRequest<Sale>(`/api/v1/sales/${id}`),
  invoice: (id: string) => apiRequest<SaleInvoice>(`/api/v1/sales/${id}/invoice`),
  create: (body: SalePayload) => apiRequest<Sale>("/api/v1/sales", { method: "POST", body: JSON.stringify(body) }),
  update: (id: string, body: SalePayload) =>
    apiRequest<Sale>(`/api/v1/sales/${id}`, { method: "PUT", body: JSON.stringify(body) }),
  complete: (id: string, paymentMethod: PaymentMethod) =>
    apiRequest<Sale>(`/api/v1/sales/${id}/complete`, {
      method: "POST",
      body: JSON.stringify({ paymentMethod }),
    }),
  cancel: (id: string) => apiRequest<Sale>(`/api/v1/sales/${id}/cancel`, { method: "POST", body: "{}" }),
};

export type LeadSource =
  | "WALK_IN"
  | "PHONE"
  | "WEBSITE"
  | "REFERRAL"
  | "SOCIAL_MEDIA"
  | "ADVERTISEMENT"
  | "EXISTING_CUSTOMER"
  | "OTHER";
export type LeadPriority = "LOW" | "MEDIUM" | "HIGH";
export type LeadStatus = "NEW" | "CONTACTED" | "QUALIFIED" | "QUOTATION" | "NEGOTIATION" | "WON" | "LOST";

export type Lead = {
  id: string;
  leadNumber: string;
  name: string;
  phone: string | null;
  email: string | null;
  companyName: string | null;
  address: string | null;
  source: LeadSource;
  requirement: string | null;
  expectedValue: number | string | null;
  expectedCloseDate: string | null;
  assignedTo: string | null;
  assignedToName: string | null;
  priority: LeadPriority;
  status: LeadStatus;
  notes: string | null;
  lostReason: string | null;
  convertedCustomerId: string | null;
  convertedCustomerName: string | null;
  createdAt: string;
  updatedAt: string;
};

export type LeadPayload = {
  name: string;
  phone?: string | null;
  email?: string | null;
  companyName?: string | null;
  address?: string | null;
  source: LeadSource;
  requirement?: string | null;
  expectedValue?: number | null;
  expectedCloseDate?: string | null;
  assignedTo?: string | null;
  priority?: LeadPriority;
  notes?: string | null;
};

export type FollowUpType = "CALL" | "VISIT" | "WHATSAPP" | "EMAIL" | "MEETING" | "OTHER";
export type FollowUpStatus = "PENDING" | "COMPLETED" | "CANCELLED";

export type FollowUp = {
  id: string;
  leadId: string;
  leadName: string;
  leadNumber: string;
  customerId: string | null;
  type: FollowUpType;
  dueDate: string;
  dueTime: string | null;
  assignedTo: string | null;
  assignedToName: string | null;
  status: FollowUpStatus;
  notes: string | null;
  outcome: string | null;
  completedAt: string | null;
  overdue: boolean;
  createdAt: string;
  updatedAt: string;
};

export type FollowUpPayload = {
  leadId: string;
  customerId?: string | null;
  type: FollowUpType;
  dueDate: string;
  dueTime?: string | null;
  assignedTo?: string | null;
  notes?: string | null;
};

export type QuotationStatus = "DRAFT" | "SENT" | "ACCEPTED" | "REJECTED" | "EXPIRED" | "CANCELLED";
export type SalesOrderStatus = "DRAFT" | "CONFIRMED" | "PROCESSING" | "READY" | "COMPLETED" | "CANCELLED";

export type PipelineLine = {
  id: string;
  productId: string;
  productName: string;
  sku: string;
  unit: string;
  quantity: number | string;
  unitPrice: number | string;
  gstRate: number | string;
  discount: number | string;
  taxableAmount: number | string;
  taxAmount: number | string;
  lineTotal: number | string;
};

export type PipelineLinePayload = {
  productId: string;
  quantity: number;
  unitPrice: number;
  gstRate: number;
  discount?: number;
};

export type Quotation = {
  id: string;
  quotationNumber: string;
  leadId: string | null;
  leadName: string | null;
  customerId: string;
  customerName: string;
  quotationDate: string;
  validUntil: string;
  status: QuotationStatus;
  subtotal: number | string;
  discount: number | string;
  taxTotal: number | string;
  grandTotal: number | string;
  notes: string | null;
  termsAndConditions: string | null;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  itemCount: number;
  items: PipelineLine[];
};

export type QuotationPayload = {
  leadId?: string | null;
  customerId: string;
  quotationDate: string;
  validUntil: string;
  discount?: number;
  notes?: string | null;
  termsAndConditions?: string | null;
  items: PipelineLinePayload[];
};

export type SalesOrder = {
  id: string;
  orderNumber: string;
  quotationId: string | null;
  quotationNumber: string | null;
  leadId: string | null;
  customerId: string;
  customerName: string;
  saleId: string | null;
  saleNumber: string | null;
  invoiceNumber: string | null;
  orderDate: string;
  expectedDeliveryDate: string | null;
  status: SalesOrderStatus;
  paymentStatus: PaymentStatus;
  subtotal: number | string;
  discount: number | string;
  taxTotal: number | string;
  grandTotal: number | string;
  advancePaid: number | string;
  outstandingAmount: number | string;
  notes: string | null;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  itemCount: number;
  items: PipelineLine[];
};

export type SalesOrderPayload = {
  quotationId?: string | null;
  leadId?: string | null;
  customerId: string;
  orderDate: string;
  expectedDeliveryDate?: string | null;
  discount?: number;
  notes?: string | null;
  items: PipelineLinePayload[];
};

export type PaymentRecord = {
  id: string;
  paymentNumber: string;
  saleId: string | null;
  invoiceNumber: string | null;
  salesOrderId: string | null;
  orderNumber: string | null;
  customerId: string | null;
  customerName: string | null;
  amount: number | string;
  paymentMethod: PaymentMethod;
  paymentDate: string;
  referenceNumber: string | null;
  notes: string | null;
  createdBy: string;
  createdByName: string;
  createdAt: string;
};

export type PaymentPayload = {
  saleId?: string | null;
  salesOrderId?: string | null;
  amount: number;
  paymentMethod: PaymentMethod;
  paymentDate: string;
  referenceNumber?: string | null;
  notes?: string | null;
};

export type OutstandingRow = {
  customerId: string | null;
  customerName: string;
  saleId: string;
  invoiceNumber: string | null;
  saleDate: string;
  grandTotal: number | string;
  paid: number | string;
  outstanding: number | string;
  paymentStatus: PaymentStatus;
};

export type PipelineDashboard = {
  newLeads: number;
  openLeads: number;
  openQuotations: number;
  quotationValue: number | string;
  openSalesOrders: number;
  pipelineValue: number | string;
  wonThisMonth: number;
  lostThisMonth: number;
  followUpsToday: number;
  followUpsOverdue: number;
  outstandingPayments: number | string;
};

export type PipelineActivity = {
  id: string;
  activityType: string;
  message: string;
  referenceType: string | null;
  referenceId: string | null;
  createdAt: string;
};

export type AppNotification = {
  id: string;
  type: string;
  title: string;
  body: string;
  entityType: string | null;
  entityId: string | null;
  readAt: string | null;
  createdAt: string;
};

export const leadApi = {
  list: (params: { q?: string; status?: LeadStatus; priority?: string; page?: number; size?: number } = {}) =>
    apiRequest<PageResult<Lead>>(`/api/v1/leads${queryString(params)}`),
  board: () => apiRequest<Lead[]>("/api/v1/leads/board"),
  get: (id: string) => apiRequest<Lead>(`/api/v1/leads/${id}`),
  timeline: (id: string) => apiRequest<PipelineActivity[]>(`/api/v1/leads/${id}/activities`),
  create: (body: LeadPayload) => apiRequest<Lead>("/api/v1/leads", { method: "POST", body: JSON.stringify(body) }),
  update: (id: string, body: LeadPayload) =>
    apiRequest<Lead>(`/api/v1/leads/${id}`, { method: "PUT", body: JSON.stringify(body) }),
  changeStatus: (id: string, status: LeadStatus, lostReason?: string | null) =>
    apiRequest<Lead>(`/api/v1/leads/${id}/status`, {
      method: "POST",
      body: JSON.stringify({ status, lostReason: lostReason ?? null }),
    }),
  convertCustomer: (id: string) =>
    apiRequest<Customer>(`/api/v1/leads/${id}/convert-customer`, { method: "POST", body: "{}" }),
};

export const followUpApi = {
  list: (params: { status?: FollowUpStatus; fromDate?: string; toDate?: string; leadId?: string } = {}) =>
    apiRequest<FollowUp[]>(`/api/v1/follow-ups${queryString(params)}`),
  create: (body: FollowUpPayload) =>
    apiRequest<FollowUp>("/api/v1/follow-ups", { method: "POST", body: JSON.stringify(body) }),
  update: (id: string, body: FollowUpPayload) =>
    apiRequest<FollowUp>(`/api/v1/follow-ups/${id}`, { method: "PUT", body: JSON.stringify(body) }),
  complete: (id: string, outcome?: string | null) =>
    apiRequest<FollowUp>(`/api/v1/follow-ups/${id}/complete`, {
      method: "POST",
      body: JSON.stringify({ outcome: outcome ?? null }),
    }),
  cancel: (id: string, outcome?: string | null) =>
    apiRequest<FollowUp>(`/api/v1/follow-ups/${id}/cancel`, {
      method: "POST",
      body: JSON.stringify({ outcome: outcome ?? null }),
    }),
};

export const quotationApi = {
  list: (params: { q?: string; status?: QuotationStatus; customerId?: string; page?: number; size?: number } = {}) =>
    apiRequest<PageResult<Quotation>>(`/api/v1/quotations${queryString(params)}`),
  get: (id: string) => apiRequest<Quotation>(`/api/v1/quotations/${id}`),
  create: (body: QuotationPayload) =>
    apiRequest<Quotation>("/api/v1/quotations", { method: "POST", body: JSON.stringify(body) }),
  update: (id: string, body: QuotationPayload) =>
    apiRequest<Quotation>(`/api/v1/quotations/${id}`, { method: "PUT", body: JSON.stringify(body) }),
  send: (id: string) => apiRequest<Quotation>(`/api/v1/quotations/${id}/send`, { method: "POST", body: "{}" }),
  accept: (id: string) => apiRequest<Quotation>(`/api/v1/quotations/${id}/accept`, { method: "POST", body: "{}" }),
  reject: (id: string) => apiRequest<Quotation>(`/api/v1/quotations/${id}/reject`, { method: "POST", body: "{}" }),
  cancel: (id: string) => apiRequest<Quotation>(`/api/v1/quotations/${id}/cancel`, { method: "POST", body: "{}" }),
};

export const salesOrderApi = {
  list: (params: { q?: string; status?: SalesOrderStatus; customerId?: string; page?: number; size?: number } = {}) =>
    apiRequest<PageResult<SalesOrder>>(`/api/v1/sales-orders${queryString(params)}`),
  get: (id: string) => apiRequest<SalesOrder>(`/api/v1/sales-orders/${id}`),
  create: (body: SalesOrderPayload) =>
    apiRequest<SalesOrder>("/api/v1/sales-orders", { method: "POST", body: JSON.stringify(body) }),
  fromQuotation: (quotationId: string) =>
    apiRequest<SalesOrder>(`/api/v1/sales-orders/from-quotation/${quotationId}`, { method: "POST", body: "{}" }),
  update: (id: string, body: SalesOrderPayload) =>
    apiRequest<SalesOrder>(`/api/v1/sales-orders/${id}`, { method: "PUT", body: JSON.stringify(body) }),
  confirm: (id: string) => apiRequest<SalesOrder>(`/api/v1/sales-orders/${id}/confirm`, { method: "POST", body: "{}" }),
  process: (id: string) => apiRequest<SalesOrder>(`/api/v1/sales-orders/${id}/process`, { method: "POST", body: "{}" }),
  ready: (id: string) => apiRequest<SalesOrder>(`/api/v1/sales-orders/${id}/ready`, { method: "POST", body: "{}" }),
  cancel: (id: string) => apiRequest<SalesOrder>(`/api/v1/sales-orders/${id}/cancel`, { method: "POST", body: "{}" }),
  convertSale: (id: string) => apiRequest<Sale>(`/api/v1/sales-orders/${id}/convert-sale`, { method: "POST", body: "{}" }),
};

export const paymentApi = {
  list: (params: { saleId?: string; salesOrderId?: string; customerId?: string } = {}) =>
    apiRequest<PaymentRecord[]>(`/api/v1/payments${queryString(params)}`),
  outstanding: (filter: "OUTSTANDING" | "PAID" | "ALL" = "OUTSTANDING") =>
    apiRequest<OutstandingRow[]>(`/api/v1/payments/outstanding${queryString({ filter })}`),
  create: (body: PaymentPayload) =>
    apiRequest<PaymentRecord>("/api/v1/payments", { method: "POST", body: JSON.stringify(body) }),
};

export const pipelineApi = {
  dashboard: () => apiRequest<PipelineDashboard>("/api/v1/pipeline/dashboard"),
};

export const notificationApi = {
  list: () => apiRequest<AppNotification[]>("/api/v1/notifications"),
  unreadCount: () => apiRequest<{ count: number }>("/api/v1/notifications/unread-count"),
  markRead: (id: string) => apiRequest<void>(`/api/v1/notifications/${id}/read`, { method: "POST", body: "{}" }),
  markAllRead: () => apiRequest<void>("/api/v1/notifications/read-all", { method: "POST", body: "{}" }),
};

export const purchaseApi = {
  list: (
    params: {
      q?: string;
      supplierId?: string;
      status?: PurchaseStatus;
      fromDate?: string;
      toDate?: string;
      page?: number;
      size?: number;
    } = {},
  ) => apiRequest<PageResult<Purchase>>(`/api/v1/purchases${queryString(params)}`),
  summary: () => apiRequest<PurchaseSummary>("/api/v1/purchases/summary"),
  get: (id: string) => apiRequest<Purchase>(`/api/v1/purchases/${id}`),
  create: (body: PurchasePayload) =>
    apiRequest<Purchase>("/api/v1/purchases", { method: "POST", body: JSON.stringify(body) }),
  update: (id: string, body: PurchasePayload) =>
    apiRequest<Purchase>(`/api/v1/purchases/${id}`, { method: "PUT", body: JSON.stringify(body) }),
  receive: (id: string) => apiRequest<Purchase>(`/api/v1/purchases/${id}/receive`, { method: "POST", body: "{}" }),
  cancel: (id: string) => apiRequest<Purchase>(`/api/v1/purchases/${id}/cancel`, { method: "POST", body: "{}" }),
};




