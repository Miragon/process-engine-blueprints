import { z } from "zod";
import { SubmitLeasingRequestBody } from "@/shared/api/generated/zod";
import type { Copy } from "@/shared/i18n";

/**
 * The submit form reuses the generated request-body schema (`SubmitLeasingRequestBody`) so the form
 * and the wire format can never drift. We ONLY layer localized presence messages on top, so the
 * schema is built from the active `copy` (the form memoizes it per locale).
 *
 * CRITICAL: do NOT add `age >= 18` or `monthlyNetIncome >= 1500` here. Those thresholds are DMN
 * decisions evaluated by the process — enforcing them client-side would make the "not solvent"
 * scenario unreachable from the UI and quietly move a business rule out of the model. If the next
 * contributor is tempted to "fix" the form by adding them, this comment is why they must not.
 *
 * `monthlyNetIncome > 0` is different: the API refuses such a request with a 400 before any process
 * starts, so it is an input rule like the presence checks, and the form names it instead of leaving
 * the user with the generic error toast.
 */
export function makeSubmitFormSchema(copy: Copy) {
  return SubmitLeasingRequestBody.extend({
    customerName: z.string().min(1, copy.submit.validationName),
    email: z.string().min(1, copy.submit.validationEmail).email(copy.submit.validationEmail),
    age: z.number({ message: copy.submit.validationAge }).int(copy.submit.validationAge),
    monthlyNetIncome: z
      .number({ message: copy.submit.validationIncome })
      .positive(copy.submit.validationIncomePositive),
    bikeId: z.string().min(1, copy.submit.validationBike),
    bikeModel: z.string().min(1, copy.submit.validationBike),
  });
}

export type SubmitFormValues = z.infer<ReturnType<typeof makeSubmitFormSchema>>;
