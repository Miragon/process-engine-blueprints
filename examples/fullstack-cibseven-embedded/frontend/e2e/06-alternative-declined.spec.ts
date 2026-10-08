import { test } from "@playwright/test";
import { expectStatus, submitApplication } from "./helpers/ui";

// Mirrors bruno/07-alternative-declined: submit BIKE-OOS -> sign -> decline the alternative from the
// /aufgaben inbox -> the contract is reversed and the case ends storniert, with no order to cancel.
test("alternative declined: declining from the inbox cancels the leasing", async ({ page }) => {
  const applicationId = await submitApplication(page, {
    name: "Dana Decline",
    email: "dana@example.com",
    age: 35,
    income: 3500,
    bikeModel: "Mountain Trail 600", // BIKE-OOS
  });

  await page.getByRole("button", { name: /vertrag unterschreiben/i }).click();

  // The order finds BIKE-OOS unavailable and parks on the clarify-alternative task. Decline it from
  // the back-office inbox.
  await page.goto("/aufgaben");
  // Scope to our own row (the inbox may hold other pending cases) via the unique customer name.
  await page
    .getByRole("row")
    .filter({ hasText: "Dana Decline" })
    .getByRole("button", { name: /klären/i })
    .click();
  await page.getByRole("button", { name: /keine alternative/i }).click();

  await page.goto(`/antraege/${applicationId}`);
  await expectStatus(page, "storniert");
});
