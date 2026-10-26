import * as React from "react";
import { cn } from "@/shared/lib/cn";

/**
 * Loading placeholder. Prefer this over spinners for content. The pulse
 * animation is disabled automatically under `prefers-reduced-motion: reduce`
 * (see theme.css).
 */
export function Skeleton({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      aria-hidden="true"
      className={cn("animate-pulse rounded-md bg-schwarz/10", className)}
      {...props}
    />
  );
}
