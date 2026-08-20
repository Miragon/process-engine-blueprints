import { describe, expect, it } from "vitest";
import { renderWithProviders, screen } from "@/shared/testing";
import { LeasingProgress } from "./leasing-progress";

// The active step carries `font-medium` (see the label className); completed/upcoming steps do not.
describe("LeasingProgress", () => {
  it("marks 'bestellt' as the active step while ORDERED", () => {
    renderWithProviders(<LeasingProgress status="ORDERED" />);
    expect(screen.getByText("bestellt").className).toContain("font-medium");
  });

  it("advances past 'bestellt' once the case is HANDED_OVER", () => {
    renderWithProviders(<LeasingProgress status="HANDED_OVER" />);
    // the handover step exists and is now the active one...
    expect(screen.getByText("übergeben").className).toContain("font-medium");
    // ...and 'bestellt' is no longer active (it is a completed step)
    expect(screen.getByText("bestellt").className).not.toContain("font-medium");
  });
});
