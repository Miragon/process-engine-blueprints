import { describe, expect, it } from "vitest";
import { de } from "@/shared/i18n";
import { makeSubmitFormSchema } from "./submit-form-schema";

const request = {
  customerName: "Dana Decline",
  email: "dana@example.com",
  age: 35,
  monthlyNetIncome: 3500,
  bikeId: "BIKE-900",
  bikeModel: "Gravel Explorer 900",
};

describe("submit form schema", () => {
  const schema = makeSubmitFormSchema(de);

  it("refuses an income of zero with its own message", () => {
    const result = schema.safeParse({ ...request, monthlyNetIncome: 0 });

    expect(result.success).toBe(false);
    expect(result.error?.issues.map((issue) => issue.message)).toEqual([
      de.submit.validationIncomePositive,
    ]);
  });

  it("accepts an income the credit-rating decision would reject", () => {
    expect(schema.safeParse({ ...request, monthlyNetIncome: 1 }).success).toBe(true);
  });
});
