import type { FollowUpType, LeadPriority, LeadSource, LeadStatus, PaymentStatus } from "@/lib/api";

export const LEAD_STATUSES: LeadStatus[] = [
  "NEW",
  "CONTACTED",
  "QUALIFIED",
  "QUOTATION",
  "NEGOTIATION",
  "WON",
  "LOST",
];

export const LEAD_SOURCES: { value: LeadSource; label: string }[] = [
  { value: "WALK_IN", label: "Walk-in" },
  { value: "PHONE", label: "Phone" },
  { value: "WEBSITE", label: "Website" },
  { value: "REFERRAL", label: "Referral" },
  { value: "SOCIAL_MEDIA", label: "Social media" },
  { value: "ADVERTISEMENT", label: "Advertisement" },
  { value: "EXISTING_CUSTOMER", label: "Existing customer" },
  { value: "OTHER", label: "Other" },
];

export const FOLLOW_UP_TYPES: { value: FollowUpType; label: string }[] = [
  { value: "CALL", label: "Call" },
  { value: "VISIT", label: "Visit" },
  { value: "WHATSAPP", label: "WhatsApp" },
  { value: "EMAIL", label: "Email" },
  { value: "MEETING", label: "Meeting" },
  { value: "OTHER", label: "Other" },
];

export function leadSourceLabel(value: LeadSource | string | null | undefined) {
  return LEAD_SOURCES.find((item) => item.value === value)?.label ?? value ?? "—";
}

export function followUpTypeLabel(value: FollowUpType | string | null | undefined) {
  return FOLLOW_UP_TYPES.find((item) => item.value === value)?.label ?? value ?? "—";
}

export function leadStatusLabel(status: LeadStatus | string) {
  const labels: Record<string, string> = {
    NEW: "New",
    CONTACTED: "Contacted",
    QUALIFIED: "Qualified",
    QUOTATION: "Quotation",
    NEGOTIATION: "Negotiation",
    WON: "Won",
    LOST: "Lost",
  };
  return labels[status] ?? status;
}

export function priorityLabel(priority: LeadPriority | string) {
  if (priority === "HIGH") return "High";
  if (priority === "LOW") return "Low";
  return "Medium";
}

export function paymentStatusLabel(status: PaymentStatus | string | null | undefined) {
  if (status === "PAID") return "Paid";
  if (status === "PARTIALLY_PAID") return "Partially paid";
  if (status === "UNPAID") return "Unpaid";
  return "—";
}

export function nextLeadStatuses(current: LeadStatus): LeadStatus[] {
  switch (current) {
    case "NEW":
      return ["CONTACTED", "QUALIFIED", "LOST"];
    case "CONTACTED":
      return ["QUALIFIED", "QUOTATION", "LOST"];
    case "QUALIFIED":
      return ["QUOTATION", "NEGOTIATION", "LOST"];
    case "QUOTATION":
      return ["NEGOTIATION", "WON", "LOST"];
    case "NEGOTIATION":
      return ["WON", "LOST"];
    default:
      return [];
  }
}

export function todayIso() {
  const now = new Date();
  const offset = now.getTimezoneOffset();
  return new Date(now.getTime() - offset * 60_000).toISOString().slice(0, 10);
}

export function formatClock(time: string | null | undefined) {
  if (!time) return "";
  const [h, m] = time.split(":");
  const hour = Number(h);
  if (!Number.isFinite(hour)) return time;
  const suffix = hour >= 12 ? "PM" : "AM";
  const twelve = hour % 12 === 0 ? 12 : hour % 12;
  return `${twelve}:${m ?? "00"} ${suffix}`;
}

export function formatDay(iso: string | null | undefined) {
  if (!iso) return "—";
  const date = new Date(iso.includes("T") ? iso : `${iso}T00:00:00`);
  if (Number.isNaN(date.getTime())) return iso;
  return new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", year: "numeric" }).format(date);
}
