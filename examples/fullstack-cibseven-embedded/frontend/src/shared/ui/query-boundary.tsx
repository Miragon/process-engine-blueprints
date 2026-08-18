import * as React from "react";
import { Alert, AlertDescription, AlertTitle } from "./alert";
import { Button } from "./button";
import { HttpError } from "@/shared/api";
import { copy } from "@/shared/i18n";

interface QueryBoundaryProps {
  isLoading: boolean;
  isError: boolean;
  error?: unknown;
  /** When true (and not loading/error), the empty state is shown instead of children. */
  isEmpty?: boolean;
  errorTitle?: string;
  onRetry?: () => void;
  /** Layout-matching skeleton shown while loading — never a bare spinner. */
  skeleton: React.ReactNode;
  /** Shown when isEmpty: icon + h3 + one lead line + the single most likely next action. */
  empty?: React.ReactNode;
  children: React.ReactNode;
}

/** Pulls the RFC 9457 `detail` out of an HttpError, falling back to a generic message. */
function problemDetail(error: unknown): string | undefined {
  if (error instanceof HttpError && error.problem && typeof error.problem === "object") {
    const detail = (error.problem as { detail?: unknown }).detail;
    if (typeof detail === "string") return detail;
  }
  return undefined;
}

/**
 * The one place loading / error / empty states are decided, so every screen behaves the same:
 * skeletons that match the final layout, an error alert carrying the problem detail plus a retry,
 * and an empty state that points at the most likely next action. Never a raw stack trace.
 */
export function QueryBoundary({
  isLoading,
  isError,
  error,
  isEmpty,
  errorTitle,
  onRetry,
  skeleton,
  empty,
  children,
}: QueryBoundaryProps) {
  if (isLoading) return <>{skeleton}</>;

  if (isError) {
    return (
      <Alert variant="danger">
        <AlertTitle>{errorTitle ?? copy.actions.error}</AlertTitle>
        <AlertDescription>
          <p>{problemDetail(error) ?? copy.actions.error}</p>
          {onRetry ? (
            <Button variant="secondary" size="sm" className="mt-3" onClick={onRetry}>
              {copy.common.retry}
            </Button>
          ) : null}
        </AlertDescription>
      </Alert>
    );
  }

  if (isEmpty && empty) return <>{empty}</>;

  return <>{children}</>;
}
