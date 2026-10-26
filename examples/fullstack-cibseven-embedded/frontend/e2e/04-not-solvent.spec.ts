import { test } from "@playwright/test";
import { expectStatus, submitApplication } from "./helpers/ui";

// Mirrors bruno/04-not-solvent: an under-age applicant is rejected by the DMN with no further action.
// The form must NOT block this client-side — solvency is a process decision, driven from the UI here.
test("not solvent: an under-age application is rejected by the DMN", async ({ page }) => {
  await submitApplication(page, {
    name: "Tim Toojung",
    email: "tim@example.com",
    age: 15,
    income: 3500,
    bikeModel: "Gravel Explorer 900",
  });

  await expectStatus(page, "abgelehnt");
});
