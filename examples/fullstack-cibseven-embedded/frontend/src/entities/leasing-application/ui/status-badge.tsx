import { Badge } from "@/shared/ui";
import { statusLabel, statusTone, type LeasingStatus } from "../model/status";

/** Status pill that always carries its label — meaning is never encoded in colour alone. */
export function StatusBadge({ status }: { status: LeasingStatus | string }) {
  return <Badge variant={statusTone(status)}>{statusLabel(status)}</Badge>;
}
