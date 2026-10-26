import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { AlertTriangle, CheckCircle2, Info, XCircle } from "lucide-react";
import { cn } from "@/shared/lib/cn";

export const alertVariants = cva(
  "flex gap-3 rounded-md border p-4 text-body [&>svg]:mt-0.5 [&>svg]:shrink-0",
  {
    variants: {
      variant: {
        info: "border-info/20 bg-info-soft text-schwarz",
        success: "border-success/20 bg-success-soft text-schwarz",
        warning: "border-warning/20 bg-warning-soft text-schwarz",
        danger: "border-danger/20 bg-danger-soft text-schwarz",
      },
    },
    defaultVariants: {
      variant: "info",
    },
  },
);

const iconFor = {
  info: Info,
  success: CheckCircle2,
  warning: AlertTriangle,
  danger: XCircle,
} as const;

const iconColor = {
  info: "text-info",
  success: "text-success",
  warning: "text-warning",
  danger: "text-danger",
} as const;

export interface AlertProps
  extends React.HTMLAttributes<HTMLDivElement>, VariantProps<typeof alertVariants> {
  /** Hide the leading status icon. */
  hideIcon?: boolean;
}

export const Alert = React.forwardRef<HTMLDivElement, AlertProps>(
  ({ className, variant = "info", hideIcon = false, children, ...props }, ref) => {
    const resolved = variant ?? "info";
    const Icon = iconFor[resolved];
    return (
      <div ref={ref} role="alert" className={cn(alertVariants({ variant }), className)} {...props}>
        {hideIcon ? null : (
          <Icon className={cn("size-5", iconColor[resolved])} strokeWidth={1.75} />
        )}
        <div className="flex flex-col gap-1">{children}</div>
      </div>
    );
  },
);
Alert.displayName = "Alert";

export function AlertTitle({ className, ...props }: React.HTMLAttributes<HTMLHeadingElement>) {
  return <h5 className={cn("font-medium", className)} {...props} />;
}

export function AlertDescription({
  className,
  ...props
}: React.HTMLAttributes<HTMLParagraphElement>) {
  return <div className={cn("text-body text-schwarz/70", className)} {...props} />;
}
