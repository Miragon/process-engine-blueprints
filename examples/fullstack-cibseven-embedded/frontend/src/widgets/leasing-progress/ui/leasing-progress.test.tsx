import { describe, expect, it } from "vitest";
import { renderWithProviders, screen } from "@/shared/testing";
import { LeasingProgress } from "./leasing-progress";

// The status is the milestone that has been reached: its step is completed (green check, `bg-success`
// on the circle) and the *next* step is the in-progress one (blue, `font-medium` on the label).
const circleOf = (label: string) => screen.getByText(label).closest("li")!.querySelector("span")!;

describe("LeasingProgress", () => {
  it("marks the reached step done and the next step as in-progress while ORDERED", () => {
    renderWithProviders(<LeasingProgress status="ORDERED" />);
    // 'bestellt' is reached → completed (green), not the in-progress step
    expect(circleOf("bestellt").className).toContain("bg-success");
    expect(screen.getByText("bestellt").className).not.toContain("font-medium");
    // the next step 'übergeben' is the in-progress one
    expect(screen.getByText("übergeben").className).toContain("font-medium");
  });

  it("turns 'übergeben' green once the case is HANDED_OVER", () => {
    renderWithProviders(<LeasingProgress status="HANDED_OVER" />);
    // the handover step is now completed (green), no longer in-progress...
    expect(circleOf("übergeben").className).toContain("bg-success");
    expect(screen.getByText("übergeben").className).not.toContain("font-medium");
    // ...and 'aktiv' is the in-progress step while the withdrawal period runs out
    expect(screen.getByText("aktiv").className).toContain("font-medium");
  });

  it("shows a distinct in-progress off-ramp while WITHDRAWN", () => {
    renderWithProviders(<LeasingProgress status="WITHDRAWN" />);
    // the withdrawal off-ramp is present and uses the warning tone, not the terminal danger red
    const row = circleOf("wird zurückgezogen");
    expect(row.className).toContain("bg-warning");
    expect(row.className).not.toContain("bg-danger");
  });
});
