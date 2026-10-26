import { Check, Circle, Clock, X } from "lucide-react";
import { cn } from "@/shared/lib";
import { useCopy } from "@/shared/i18n";
import type { LeasingStatus } from "@/entities/leasing-application";

/**
 * A BPMN-shaped step rail: eingegangen → bestellt → übergeben → aktiv, with abgelehnt/storniert as
 * off-ramps. The status is the milestone that has been *reached*, so the step for the current status
 * is a completed step (green check) and the *next* step is the in-progress one (blue). `HANDED_OVER`
 * is its own step (the bike is handed over and the case waits out the withdrawal period before it
 * goes `ACTIVE`), so once handed over its step turns green and "aktiv" becomes the pending step.
 * Named `leasing-progress`, never `process-view` — in this codebase "process" means the BPMN model,
 * and the FSD `processes` layer is banned.
 */
const ORDER: Record<LeasingStatus, number> = {
  RECEIVED: 0,
  ORDERED: 1,
  HANDED_OVER: 2,
  ACTIVE: 3,
  WITHDRAWN: -1,
  REJECTED: -1,
  CANCELLED: -1,
};

export function LeasingProgress({ status }: { status: LeasingStatus | string }) {
  const copy = useCopy();
  const mainSteps: { status: LeasingStatus; label: string }[] = [
    { status: "RECEIVED", label: copy.progress.received },
    { status: "ORDERED", label: copy.progress.ordered },
    { status: "HANDED_OVER", label: copy.progress.handedOver },
    { status: "ACTIVE", label: copy.progress.active },
  ];
  const current = ORDER[status as LeasingStatus] ?? 0;
  const withdrawn = status === "WITHDRAWN";
  const offRamp = withdrawn || status === "REJECTED" || status === "CANCELLED";

  return (
    <ol className="flex flex-col gap-3" aria-label={copy.progress.title}>
      {mainSteps.map((step, index) => {
        const done = !offRamp && index <= current;
        const active = !offRamp && index === current + 1;
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
          <span
            className={cn(
              "flex size-6 items-center justify-center rounded-pill text-weiss",
              withdrawn ? "bg-warning" : "bg-danger",
            )}
          >
            {withdrawn ? (
              <Clock size={16} strokeWidth={1.75} />
            ) : (
              <X size={16} strokeWidth={1.75} />
            )}
          </span>
          <span className={cn("text-body font-medium", withdrawn ? "text-warning" : "text-danger")}>
            {withdrawn
              ? copy.progress.withdrawn
              : status === "REJECTED"
                ? copy.progress.rejected
                : copy.progress.cancelled}
          </span>
        </li>
      ) : null}
    </ol>
  );
}
