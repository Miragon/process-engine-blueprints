import * as React from "react";
import { Slot } from "radix-ui";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/shared/lib/cn";

export const buttonVariants = cva(
  "inline-flex items-center justify-center gap-2 rounded-md font-sans font-medium whitespace-nowrap transition-colors duration-[var(--duration-fast)] ease-standard outline-none focus-visible:ring-2 focus-visible:ring-blau focus-visible:ring-offset-2 focus-visible:ring-offset-weiss disabled:pointer-events-none disabled:opacity-50 [&_svg]:pointer-events-none [&_svg]:shrink-0",
  {
    variants: {
      variant: {
        primary: "bg-blau text-weiss hover:bg-blau-link",
        secondary: "border border-schwarz/15 bg-weiss text-schwarz hover:bg-grau",
        ghost: "bg-transparent text-schwarz hover:bg-grau",
        danger: "bg-danger text-weiss hover:bg-danger/90",
      },
      size: {
        sm: "h-8 px-3 text-klein",
        md: "h-10 px-4 text-body",
        lg: "h-12 px-6 text-body",
      },
    },
    defaultVariants: {
      variant: "primary",
      size: "md",
    },
  },
);

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>, VariantProps<typeof buttonVariants> {
  /** Render as the single child element (Radix Slot) instead of a <button>. */
  asChild?: boolean;
}

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, asChild = false, type, ...props }, ref) => {
    const Comp = asChild ? Slot.Root : "button";
    return (
      <Comp
        ref={ref}
        type={asChild ? undefined : (type ?? "button")}
        className={cn(buttonVariants({ variant, size }), className)}
        {...props}
      />
    );
  },
);
Button.displayName = "Button";
