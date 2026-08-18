import * as React from "react";
import { Label } from "./label";
import { cn } from "@/shared/lib/cn";

export interface FieldProps {
  /** Visible field label. */
  label?: React.ReactNode;
  /** Helper/description text rendered below the control. */
  description?: React.ReactNode;
  /**
   * Error message. Pass e.g. `errors.email?.message` from react-hook-form.
   * When present, the control is marked aria-invalid and the message is shown.
   */
  error?: string;
  /** Mark the field as required (renders an asterisk). */
  required?: boolean;
  className?: string;
  /**
   * Exactly one form control (Input, Select, Textarea, ...). The Field clones
   * it to wire `id`, `aria-invalid`, and `aria-describedby` automatically.
   */
  children: React.ReactElement<{
    id?: string;
    "aria-invalid"?: boolean;
    "aria-describedby"?: string;
  }>;
}

/**
 * react-hook-form-compatible field wrapper: label + control slot + error +
 * description. Wire it as:
 *
 * ```tsx
 * <Field label="E-Mail" error={errors.email?.message}>
 *   <Input {...register("email")} />
 * </Field>
 * ```
 *
 * `error` is a plain string (RHF's `fieldError.message`). The child control is
 * cloned so accessibility attributes are connected for you.
 */
export function Field({ label, description, error, required, className, children }: FieldProps) {
  const generatedId = React.useId();
  const controlId = children.props.id ?? `${generatedId}-control`;
  const errorId = `${generatedId}-error`;
  const descriptionId = `${generatedId}-description`;

  const describedBy =
    [description ? descriptionId : null, error ? errorId : null].filter(Boolean).join(" ") ||
    undefined;

  const control = React.cloneElement(children, {
    id: controlId,
    "aria-invalid": error ? true : undefined,
    "aria-describedby": describedBy,
  });

  return (
    <div className={cn("flex flex-col gap-1.5", className)}>
      {label ? (
        <Label htmlFor={controlId}>
          {label}
          {required ? <span className="ml-0.5 text-danger">*</span> : null}
        </Label>
      ) : null}
      {control}
      {description ? (
        <p id={descriptionId} className="text-klein text-schwarz/60">
          {description}
        </p>
      ) : null}
      {error ? (
        <p id={errorId} role="alert" className="text-klein font-medium text-danger">
          {error}
        </p>
      ) : null}
    </div>
  );
}
