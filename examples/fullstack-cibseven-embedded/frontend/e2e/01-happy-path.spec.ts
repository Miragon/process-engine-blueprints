import { test } from "@playwright/test";
import { expectStatus, submitApplication } from "./helpers/ui";
import { fireTimer } from "./helpers/engine-rest";

// Mirrors bruno/01-happy-path: submit -> sign -> report handover -> fire withdrawal timer -> aktiv.
test("happy path: the leasing goes active after the withdrawal period", async ({
  page,
  request,
}) => {
  const applicationId = await submitApplication(page, {
    name: "Alice Rider",
    email: "alice@example.com",
    age: 35,
    income: 3500,
    bikeModel: "Gravel Explorer 900",
  });

  await page.getByRole("button", { name: /vertrag unterschreiben/i }).click();
  // The engine advances asynchronously; wait for the order (ORDERED) before the next action.
  await expectStatus(page, "bestellt");
  await page.getByRole("button", { name: /übergabe melden/i }).click();

  // The 14-day withdrawal period is an engine timer, not a UI action.
  await fireTimer(request, applicationId, "event_withdrawalPeriodElapsed");

  await expectStatus(page, "aktiv");
});
