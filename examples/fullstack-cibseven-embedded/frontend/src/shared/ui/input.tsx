import * as React from "react";
import { cn } from "@/shared/lib/cn";

export type InputProps = React.InputHTMLAttributes<HTMLInputElement>;

export const Input = React.forwardRef<HTMLInputElement, InputProps>(
  ({ className, type = "text", ...props }, ref) => {
    return (
      <input
        ref={ref}
        type={type}
        className={cn(
          "flex h-10 w-full rounded-md border border-schwarz/15 bg-weiss px-3 py-2 text-body text-schwarz shadow-sm transition-colors duration-[var(--duration-fast)] ease-standard",
          "placeholder:text-schwarz/40",
          "focus-visible:border-blau focus-visible:ring-2 focus-visible:ring-blau/30 focus-visible:outline-none",
          "aria-[invalid=true]:border-danger aria-[invalid=true]:ring-danger/30",
          "disabled:cursor-not-allowed disabled:opacity-50",
          "file:border-0 file:bg-transparent file:text-body file:font-medium",
          className,
        )}
        {...props}
      />
    );
  },
);
Input.displayName = "Input";
