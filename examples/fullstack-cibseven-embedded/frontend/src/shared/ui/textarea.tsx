import * as React from "react";
import { cn } from "@/shared/lib/cn";

export type TextareaProps = React.TextareaHTMLAttributes<HTMLTextAreaElement>;

export const Textarea = React.forwardRef<HTMLTextAreaElement, TextareaProps>(
  ({ className, ...props }, ref) => {
    return (
      <textarea
        ref={ref}
        className={cn(
          "flex min-h-20 w-full rounded-md border border-schwarz/15 bg-weiss px-3 py-2 text-body text-schwarz shadow-sm transition-colors duration-[var(--duration-fast)] ease-standard",
          "placeholder:text-schwarz/40",
          "focus-visible:border-blau focus-visible:ring-2 focus-visible:ring-blau/30 focus-visible:outline-none",
          "aria-[invalid=true]:border-danger aria-[invalid=true]:ring-danger/30",
          "disabled:cursor-not-allowed disabled:opacity-50",
          className,
        )}
        {...props}
      />
    );
  },
);
Textarea.displayName = "Textarea";
