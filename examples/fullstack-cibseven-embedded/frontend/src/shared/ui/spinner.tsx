import { LoaderCircle } from "lucide-react";
import { cn } from "@/shared/lib/cn";

/**
 * Small spinner. Prefer <Skeleton /> for page/content loading — this is meant
 * for inline/button loading states. The spin animation is disabled under
 * `prefers-reduced-motion: reduce` (see theme.css).
 */
export function Spinner({
  className,
  label = "Lädt",
  ...props
}: React.SVGProps<SVGSVGElement> & { label?: string }) {
  return (
    <LoaderCircle
      role="status"
      aria-label={label}
      strokeWidth={1.75}
      className={cn("size-4 animate-spin text-current", className)}
      {...props}
    />
  );
}
