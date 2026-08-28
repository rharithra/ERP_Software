import {
  BarChart3,
  Boxes,
  ClipboardList,
  LayoutDashboard,
  Package,
  Receipt,
  Tags,
  Settings,
  ShoppingBag,
  Store,
  Truck,
  Users,
  Wallet,
  type LucideIcon,
} from "lucide-react";
import { NavLink } from "react-router-dom";
import { cn } from "@/lib/utils";

export type NavItem = {
  label: string;
  to: string;
  icon: LucideIcon;
  available: boolean;
};

export const NAV_ITEMS: NavItem[] = [
  { label: "Dashboard", to: "/app", icon: LayoutDashboard, available: true },
  { label: "Sales", to: "/app/sales", icon: ShoppingBag, available: true },
  { label: "Categories", to: "/app/categories", icon: Tags, available: true },
  { label: "Products", to: "/app/products", icon: Package, available: true },
  { label: "Inventory", to: "/app/inventory", icon: Boxes, available: true },
  { label: "Customers", to: "/app/customers", icon: Users, available: true },
  { label: "Suppliers", to: "/app/suppliers", icon: Truck, available: true },
  { label: "Purchases", to: "/app/purchases", icon: ClipboardList, available: true },
  { label: "Reports", to: "/app/reports", icon: BarChart3, available: false },
  { label: "Expenses", to: "/app/expenses", icon: Wallet, available: false },
  { label: "Employees", to: "/app/employees", icon: Users, available: false },
  { label: "Company", to: "/app/company", icon: Store, available: true },
  { label: "Settings", to: "/app/settings", icon: Settings, available: false },
];

export function BrandMark({ compact = false }: { compact?: boolean }) {
  return (
    <div className="flex items-center gap-2.5">
      <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-primary text-primary-foreground">
        <Receipt className="h-4 w-4" />
      </div>
      {!compact && (
        <div>
          <p className="text-sm font-semibold tracking-tight text-sidebar-foreground">RetailFlow</p>
          <p className="text-[11px] text-sidebar-muted">Indian retail ERP</p>
        </div>
      )}
    </div>
  );
}

export function NavList({ onNavigate }: { onNavigate?: () => void }) {
  return (
    <nav className="flex flex-col gap-1 px-3">
      {NAV_ITEMS.map((item) => {
        const Icon = item.icon;
        if (!item.available) {
          return (
            <NavLink
              key={item.to}
              to={item.to}
              onClick={onNavigate}
              className="flex items-center justify-between rounded-md px-2.5 py-2 text-sm text-sidebar-muted hover:bg-sidebar-accent hover:text-sidebar-foreground"
            >
              <span className="flex items-center gap-2.5">
                <Icon className="h-4 w-4" />
                {item.label}
              </span>
              <span className="rounded bg-sidebar-accent px-1.5 py-0.5 text-[10px] uppercase tracking-wide">Soon</span>
            </NavLink>
          );
        }
        return (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === "/app"}
            onClick={onNavigate}
            className={({ isActive }) =>
              cn(
                "flex items-center gap-2.5 rounded-md px-2.5 py-2 text-sm transition-colors",
                isActive
                  ? "bg-sidebar-accent text-sidebar-foreground"
                  : "text-sidebar-muted hover:bg-sidebar-accent hover:text-sidebar-foreground",
              )
            }
          >
            <Icon className="h-4 w-4" />
            {item.label}
          </NavLink>
        );
      })}
    </nav>
  );
}
