import { expect, type Page } from "@playwright/test";

export interface ApplicationInput {
  name: string;
  email: string;
  age: number;
  income: number;
  /** The bike model label shown in the picker, e.g. "Gravel Explorer 900" or "Mountain Trail 600". */
  bikeModel: string;
}

/**
 * Fills and submits the customer-portal submit form, then returns the new application id read from
 * the detail URL the app navigates to.
 */
export async function submitApplication(page: Page, input: ApplicationInput): Promise<string> {
  await page.goto("/antraege/neu");
  // Required fields render a trailing "*" in their label, so match by prefix, not exact.
  await page.getByLabel(/^name/i).fill(input.name);
  await page.getByLabel(/^e-mail/i).fill(input.email);
  await page.getByLabel(/^alter/i).fill(String(input.age));
  await page.getByLabel(/nettoeinkommen/i).fill(String(input.income));

  await page.getByRole("combobox").click();
  await page.getByRole("option", { name: input.bikeModel }).click();

  await page.getByRole("button", { name: /antrag absenden/i }).click();

  await page.waitForURL(/\/antraege\/[0-9a-f-]{36}$/i, { timeout: 15_000 });
  const match = page.url().match(/\/antraege\/([0-9a-f-]{36})/i);
  expect(match, "expected to land on the detail page").not.toBeNull();
  return match![1];
}

/** Waits for the detail page's status badge to show the expected German status label. */
export async function expectStatus(page: Page, label: string): Promise<void> {
  // The label can appear in both the status badge and the progress rail; the badge comes first.
  await expect(page.getByText(label, { exact: true }).first()).toBeVisible({ timeout: 20_000 });
}
