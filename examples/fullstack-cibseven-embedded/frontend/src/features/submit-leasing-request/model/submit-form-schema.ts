import { z } from "zod";
import { SubmitLeasingRequestBody } from "@/shared/api/generated/zod";
import { copy } from "@/shared/i18n";

/**
 * The submit form reuses the generated request-body schema (`SubmitLeasingRequestBody`) so the form
 * and the wire format can never drift. We ONLY layer German presence messages on top.
 *
 * CRITICAL: do NOT add `age >= 18` or `monthlyNetIncome >= 1500` here. Those thresholds are DMN
 * decisions evaluated by the process — enforcing them client-side would make the "not solvent"
 * scenario unreachable from the UI and quietly move a business rule out of the model. If the next
 * contributor is tempted to "fix" the form by adding them, this comment is why they must not.
 */
export const submitFormSchema = SubmitLeasingRequestBody.extend({
  customerName: z.string().min(1, copy.submit.validationName),
  email: z.string().min(1, copy.submit.validationEmail).email(copy.submit.validationEmail),
  age: z.number({ message: copy.submit.validationAge }).int(copy.submit.validationAge),
  monthlyNetIncome: z.number({ message: copy.submit.validationIncome }),
  bikeId: z.string().min(1, copy.submit.validationBike),
  bikeModel: z.string().min(1, copy.submit.validationBike),
});

export type SubmitFormValues = z.infer<typeof submitFormSchema>;
