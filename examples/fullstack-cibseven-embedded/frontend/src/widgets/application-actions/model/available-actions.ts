import type { LeasingStatus } from "@/entities/leasing-application";

/** The business actions the detail action bar can offer. Clarify lives in the inbox, not here. */
export type LeasingAction = "sign" | "handover" | "withdraw";

/**
 * Which actions are valid in each status. A non-terminal case can always be withdrawn; signing is
 * offered before the order, reporting the handover after it. Terminal cases offer nothing — the bar
 * shows an explanatory line instead.
 */
const MATRIX: Record<LeasingStatus, LeasingAction[]> = {
  RECEIVED: ["sign", "withdraw"],
  ORDERED: ["handover", "withdraw"],
  HANDED_OVER: ["withdraw"],
  ACTIVE: [],
  REJECTED: [],
  CANCELLED: [],
};

export function availableActions(status: LeasingStatus | string): LeasingAction[] {
  return MATRIX[status as LeasingStatus] ?? [];
}
