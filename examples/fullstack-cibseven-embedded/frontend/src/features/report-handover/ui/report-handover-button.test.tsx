import { describe, expect, it } from "vitest";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { renderWithProviders, screen, server, waitFor } from "@/shared/testing";
import { ReportHandoverButton } from "./report-handover-button";

describe("ReportHandoverButton", () => {
  it("stays disabled after a successful report so a second click can't fire a second correlate", async () => {
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    let posts = 0;
    server.use(
      http.post("*/api/bike-leasing/:id/report-handover", () => {
        posts += 1;
        return new HttpResponse(null, { status: 202 });
      }),
    );

    renderWithProviders(<ReportHandoverButton applicationId="app-1" />);
    const button = screen.getByRole("button");

    await user.click(button);
    await waitFor(() => expect(button).toBeDisabled());

    // The status refetch that unmounts this button may still be in flight; a click in that window
    // must not reach the backend (the message was already correlated — a second one 409s).
    await user.click(button);
    expect(posts).toBe(1);
  });
});
