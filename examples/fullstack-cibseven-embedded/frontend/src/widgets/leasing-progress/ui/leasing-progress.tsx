import { Check, Circle, X } from "lucide-react";
import { cn } from "@/shared/lib";
import { copy } from "@/shared/i18n";
import type { LeasingStatus } from "@/entities/leasing-application";

/**
 * A BPMN-shaped step rail: eingegangen → bestellt → aktiv, with abgelehnt/storniert as off-ramps.
 * Named `leasing-progress`, never `process-view` — in this codebase "process" means the BPMN model,
 * and the FSD `processes` layer is banned.
 */
const MAIN_STEPS: { status: LeasingStatus; label: string }[] = [
  { status: "RECEIVED", label: copy.progress.received },
  { status: "ORDERED", label: copy.progress.ordered },
  { status: "ACTIVE", label: copy.progress.active },
];

const ORDER: Record<LeasingStatus, number> = {
  RECEIVED: 0,
  ORDERED: 1,
  ACTIVE: 2,
  REJECTED: -1,
  CANCELLED: -1,
};

export function LeasingProgress({ status }: { status: LeasingStatus | string }) {
  const current = ORDER[status as LeasingStatus] ?? 0;
  const offRamp = status === "REJECTED" || status === "CANCELLED";

  return (
    <ol className="flex flex-col gap-3" aria-label={copy.progress.title}>
      {MAIN_STEPS.map((step, index) => {
        const done = !offRamp && index < current;
        const active = !offRamp && index === current;
        return (
          <li key={step.status} className="flex items-center gap-3">
            <span
              className={cn(
                "flex size-6 items-center justify-center rounded-pill",
                done && "bg-success text-weiss",
                active && "bg-blau text-weiss",
                !done && !active && "bg-grau text-schwarz/40",
              )}
            >
              {done ? (
                <Check size={16} strokeWidth={1.75} />
              ) : (
                <Circle size={16} strokeWidth={1.75} />
              )}
            </span>
            <span
              className={cn("text-body", active ? "font-medium text-schwarz" : "text-schwarz/60")}
            >
              {step.label}
            </span>
          </li>
        );
      })}
      {offRamp ? (
        <li className="flex items-center gap-3">
          <span className="flex size-6 items-center justify-center rounded-pill bg-danger text-weiss">
            <X size={16} strokeWidth={1.75} />
          </span>
          <span className="text-body font-medium text-danger">
            {status === "REJECTED" ? copy.progress.rejected : copy.progress.cancelled}
          </span>
        </li>
      ) : null}
    </ol>
  );
}
