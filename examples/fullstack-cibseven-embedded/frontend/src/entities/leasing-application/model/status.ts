import { copy } from "@/shared/i18n";

/** The leasing lifecycle, mirrored from the backend enum. */
export const LEASING_STATUSES = ["RECEIVED", "ORDERED", "ACTIVE", "REJECTED", "CANCELLED"] as const;

export type LeasingStatus = (typeof LEASING_STATUSES)[number];

/** Badge tones are the design-system variants — status never encodes meaning in colour alone. */
export type StatusTone = "neutral" | "success" | "warning" | "danger" | "info";

const TERMINAL_STATUSES: readonly LeasingStatus[] = ["ACTIVE", "REJECTED", "CANCELLED"];

/**
 * A case is terminal once it is live or has failed. The detail page polls only while a case is
 * non-terminal, so this predicate is the single definition both the UI and its tests rely on.
 */
export function isTerminal(status: LeasingStatus | string | undefined | null): boolean {
  return status != null && TERMINAL_STATUSES.includes(status as LeasingStatus);
}

/** German label for a status; falls back to the raw value for an unknown status. */
export function statusLabel(status: LeasingStatus | string): string {
  return copy.status[status as LeasingStatus] ?? status;
}

const TONES: Record<LeasingStatus, StatusTone> = {
  RECEIVED: "info",
  ORDERED: "info",
  ACTIVE: "success",
  REJECTED: "danger",
  CANCELLED: "warning",
};

export function statusTone(status: LeasingStatus | string): StatusTone {
  return TONES[status as LeasingStatus] ?? "neutral";
}
