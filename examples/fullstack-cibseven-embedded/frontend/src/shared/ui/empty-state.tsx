import type { ReactNode } from "react";

/** Empty state: icon + h3 + one lead line + the single most likely next action. Never a bare "no data". */
export function EmptyState({
  icon,
  title,
  description,
  action,
}: {
  icon?: ReactNode;
  title: string;
  description: string;
  action?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center gap-3 py-16 text-center">
      {icon ? <div className="text-schwarz/30">{icon}</div> : null}
      <h3 className="text-h3 text-schwarz">{title}</h3>
      <p className="text-lead text-schwarz/60">{description}</p>
      {action ? <div className="mt-2">{action}</div> : null}
    </div>
  );
}
