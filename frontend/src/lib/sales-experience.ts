export type BusinessType =
  | "GROCERY_SUPERMARKET"
  | "ELECTRONICS_COMPUTER"
  | "MOBILE_ACCESSORIES"
  | "APPLIANCES_WATER_PURIFIER"
  | "FURNITURE"
  | "HARDWARE_BUILDING_MATERIALS"
  | "OTHER";

export type SalesMode = "QUICK_SALE" | "PIPELINE" | "HYBRID";

export const BUSINESS_TYPES: { value: BusinessType; label: string }[] = [
  { value: "GROCERY_SUPERMARKET", label: "Grocery / Supermarket" },
  { value: "ELECTRONICS_COMPUTER", label: "Electronics / Computer" },
  { value: "MOBILE_ACCESSORIES", label: "Mobile / Accessories" },
  { value: "APPLIANCES_WATER_PURIFIER", label: "Appliances / Water Purifier" },
  { value: "FURNITURE", label: "Furniture" },
  { value: "HARDWARE_BUILDING_MATERIALS", label: "Hardware / Building Materials" },
  { value: "OTHER", label: "Other" },
];

export const SALES_MODES: {
  value: SalesMode;
  label: string;
  description: string;
}[] = [
  {
    value: "QUICK_SALE",
    label: "Quick Sale",
    description: "Best for businesses where customers usually buy directly through the counter.",
  },
  {
    value: "PIPELINE",
    label: "Sales Pipeline",
    description: "Best for businesses where enquiries, follow-ups and quotations are common.",
  },
  {
    value: "HYBRID",
    label: "Hybrid",
    description: "Use both quick POS sales and structured sales opportunities.",
  },
];

const RECOMMENDATIONS: Record<BusinessType, SalesMode> = {
  GROCERY_SUPERMARKET: "QUICK_SALE",
  ELECTRONICS_COMPUTER: "HYBRID",
  MOBILE_ACCESSORIES: "HYBRID",
  APPLIANCES_WATER_PURIFIER: "PIPELINE",
  FURNITURE: "PIPELINE",
  HARDWARE_BUILDING_MATERIALS: "HYBRID",
  OTHER: "HYBRID",
};

export function recommendedSalesMode(businessType: BusinessType): SalesMode {
  return RECOMMENDATIONS[businessType] ?? "HYBRID";
}

export function businessTypeLabel(value: BusinessType | string | undefined) {
  return BUSINESS_TYPES.find((item) => item.value === value)?.label ?? "Other";
}

export function salesModeLabel(value: SalesMode | string | undefined) {
  if (value === "QUICK_SALE") return "Quick Sale";
  if (value === "PIPELINE") return "Sales Pipeline";
  if (value === "HYBRID") return "Hybrid";
  return "Hybrid";
}

export function salesExperienceSummary(salesMode: SalesMode | string | undefined) {
  if (salesMode === "QUICK_SALE") return "RetailFlow is configured for Quick Sale.";
  if (salesMode === "PIPELINE") return "RetailFlow is configured for Sales Pipeline.";
  return "RetailFlow is configured for Hybrid Sales.";
}
