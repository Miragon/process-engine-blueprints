import { expect, test } from "@playwright/test";
import { expectStatus, submitApplication } from "./helpers/ui";
import { fireTimer } from "./helpers/engine-rest";

// Mirrors bruno/05-bike-unavailable: submit BIKE-OOS -> sign -> resolve via the /aufgaben inbox
// choosing an available bike -> report handover -> fire withdrawal timer -> aktiv, and the bike is
// now the chosen alternative.
test("bike unavailable: resolve the clarification from the inbox and go active", async ({
  page,
  request,
}) => {
  const applicationId = await submitApplication(page, {
    name: "Erin Restock",
    email: "erin@example.com",
    age: 35,
    income: 3500,
    bikeModel: "Mountain Trail 600", // BIKE-OOS
  });

  await page.getByRole("button", { name: /vertrag unterschreiben/i }).click();

  // The order finds BIKE-OOS unavailable and parks on the clarify-alternative task. Resolve it from
  // the back-office inbox by choosing an available bike.
  await page.goto("/aufgaben");
  // Scope to our own row (the inbox may hold other pending cases) via the unique customer name.
  await page
    .getByRole("row")
    .filter({ hasText: "Erin Restock" })
    .getByRole("button", { name: /klären/i })
    .click();
  await page.getByRole("combobox").click();
  await page.getByRole("option", { name: "Gravel Explorer 900" }).click();
  await page.getByRole("button", { name: /alternative bestätigen/i }).click();

  // Back on the detail page, the re-order now succeeds; wait for ORDERED, then report the handover.
  await page.goto(`/antraege/${applicationId}`);
  await expectStatus(page, "bestellt");
  await page.getByRole("button", { name: /übergabe melden/i }).click();
  await fireTimer(request, applicationId, "event_withdrawalPeriodElapsed");

  await expectStatus(page, "aktiv");
  // ...and the case now carries the chosen alternative bike.
  await expect(page.getByText("Gravel Explorer 900").first()).toBeVisible();
});
