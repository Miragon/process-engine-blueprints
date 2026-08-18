import { Toaster as SonnerToaster, type ToasterProps } from "sonner";

/**
 * App-wide toast host. Mount once near the root. Trigger toasts with sonner's
 * `toast()` from anywhere. Themed to the Miragon design system.
 */
export function Toaster(props: ToasterProps) {
  return (
    <SonnerToaster
      position="bottom-right"
      closeButton
      toastOptions={{
        classNames: {
          toast: "font-sans rounded-md border border-schwarz/10 bg-weiss text-schwarz shadow-lg",
          description: "text-schwarz/60",
          success: "text-success",
          error: "text-danger",
          warning: "text-warning",
          info: "text-info",
        },
      }}
      {...props}
    />
  );
}

export { toast } from "sonner";
