import { test } from "@playwright/test";
import { expectStatus, submitApplication } from "./helpers/ui";
import { completeTask, waitForWaitState } from "./helpers/engine-rest";

// Mirrors bruno/03-abort: submit -> sign -> withdraw -> complete the form-only clarify-return -> storniert.
test("abort: withdrawing after signing cancels the leasing", async ({ page, request }) => {
  const applicationId = await submitApplication(page, {
    name: "Wanda Withdraw",
    email: "wanda@example.com",
    age: 33,
    income: 3200,
    bikeModel: "Gravel Explorer 900",
  });

  await page.getByRole("button", { name: /vertrag unterschreiben/i }).click();
  // Wait for the order to complete so the withdrawal saga has something to compensate.
  await expectStatus(page, "bestellt");
  // The "bestellt" badge is driven by an async projection and can race ahead of the engine actually
  // committing the order and registering its compensation boundary. Withdraw only once the instance
  // is parked at the post-order wait state, otherwise cancelBikeOrder — and the clarify-return task
  // this test completes below — is skipped and the case cancels straight to "storniert".
  await waitForWaitState(request, applicationId, "event_handoverReported");
  await page.getByRole("button", { name: /antrag zurückziehen/i }).click();

  // clarify-return has no UI on purpose — it is completed through the engine, like a Camunda Form.
  await completeTask(request, applicationId, "userTask_clarifyReturn", {
    returnClarified: { value: true, type: "Boolean" },
  });

  await expectStatus(page, "storniert");
});
