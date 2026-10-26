import * as React from "react";
import { Label as RadixLabel } from "radix-ui";
import { cn } from "@/shared/lib/cn";

export type LabelProps = React.ComponentPropsWithoutRef<typeof RadixLabel.Root>;

export const Label = React.forwardRef<React.ComponentRef<typeof RadixLabel.Root>, LabelProps>(
  ({ className, ...props }, ref) => (
    <RadixLabel.Root
      ref={ref}
      className={cn(
        "text-klein font-medium text-schwarz peer-disabled:cursor-not-allowed peer-disabled:opacity-70",
        className,
      )}
      {...props}
    />
  ),
);
Label.displayName = "Label";
