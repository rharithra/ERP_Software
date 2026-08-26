const TOKEN_KEY = "retailflow.accessToken";

export type TenantSummary = {
  id: string;
  name: string;
  currency: string;
  timezone: string;
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
  signup: (body: { fullName: string; email: string; password: string; companyName: string }) =>
    apiRequest<AuthPayload>("/api/v1/auth/signup", { method: "POST", body: JSON.stringify(body) }),
  login: (body: { email: string; password: string }) =>
    apiRequest<AuthPayload>("/api/v1/auth/login", { method: "POST", body: JSON.stringify(body) }),
  me: () => apiRequest<AuthUser>("/api/v1/auth/me"),
};

export const tenantApi = {
  get: () => apiRequest<CompanyProfile>("/api/v1/tenant"),
  update: (body: Partial<CompanyProfile> & { name: string }) =>
    apiRequest<CompanyProfile>("/api/v1/tenant", { method: "PUT", body: JSON.stringify(body) }),
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



