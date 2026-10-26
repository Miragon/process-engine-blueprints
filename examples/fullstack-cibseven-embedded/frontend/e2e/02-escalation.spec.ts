import { test } from "@playwright/test";
import { expectStatus, submitApplication } from "./helpers/ui";
import { fireTimer } from "./helpers/engine-rest";

// Mirrors bruno/02-escalation: submit, do NOT sign, fire the signature deadline -> abgelehnt.
test("escalation: an unsigned contract is rejected at the signature deadline", async ({
  page,
  request,
}) => {
  const applicationId = await submitApplication(page, {
    name: "Sam Slow",
    email: "sam@example.com",
    age: 40,
    income: 4000,
    bikeModel: "Gravel Explorer 900",
  });

  // Deliberately do not sign — let the deadline timer fire.
  await fireTimer(request, applicationId, "event_signatureDeadline");

  await expectStatus(page, "abgelehnt");
});
